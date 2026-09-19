package com.rentmyride.service.impl;

import com.rentmyride.custom_exceptions.CustomerNotFoundException;
import com.rentmyride.dtos.NotificationDTO;
import com.rentmyride.entities.Customer;
import com.rentmyride.entities.Notification;
import com.rentmyride.repository.CustomerRepository;
import com.rentmyride.repository.NotificationRepository;
import com.rentmyride.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final RestTemplate restTemplate;
    private final NotificationRepository notificationRepository;
    private final CustomerRepository customerRepository;
    private final com.rentmyride.repository.DriverRepository driverRepository;
    private final JavaMailSender mailSender;

    @Value("${notification.enabled:false}")
    private boolean enabled;

    @Value("${twilio.account-sid:}")
    private String accountSid;

    @Value("${twilio.auth-token:}")
    private String authToken;

    @Value("${twilio.sms-from:}")
    private String smsFrom;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Override
    @org.springframework.scheduling.annotation.Async
    public void sendSms(String mobileNumber, String message) {
        send(toE164(mobileNumber), smsFrom, message);
    }

    @Override
    public void sendBookingConfirmation(String customerName, String mobileNumber, Long reservationId,
                                         String carLabel, String pickupDate, double amount) {
        String message = String.format(
                "Hi %s! Your RentMyRide booking #RES-%d for %s on %s is CONFIRMED. Amount: Rs.%.0f. Thank you for choosing us!",
                customerName, reservationId, carLabel, pickupDate, amount);
        sendSms(mobileNumber, message);
    }

    // Performance fix: assignDriver() (and other flows) used to call this SYNCHRONOUSLY — the
    // admin's browser sat waiting for the full SMTP round-trip (connect + TLS handshake + send)
    // before getting a response back, which is exactly why "driver assign hone mein time leta
    // hai" (assigning a driver felt slow). @Async runs this on a background thread instead, so
    // the calling method returns to the HTTP response immediately and the email goes out without
    // blocking anything.
    @Override
    @org.springframework.scheduling.annotation.Async
    public void sendEmail(String toEmail, String subject, String body) {
        if (toEmail == null || toEmail.isBlank()) return;
        // Bug fix: application.properties ships spring.mail.username/password with PLACEHOLDER
        // defaults ("your-email@gmail.com" / "your-16-char-app-password") — exactly the same
        // problem the Twilio SMS credentials had. Every mailSender.send() call was silently
        // failing against Gmail's real SMTP server with those placeholder creds, and the
        // catch-block below swallowed it into a vague log line — so booking-confirmation emails
        // looked like they should be going out (the code path is correct) but never actually
        // left the server. This check catches it BEFORE attempting to send and says exactly
        // what's missing, instead of a generic SMTP auth failure buried in the logs.
        if (smtpUsername.isBlank() || smtpPassword.isBlank()
                || smtpUsername.equals("your-email@gmail.com") || smtpPassword.equals("your-16-char-app-password")) {
            log.warn("[DDT-WARN] Email not sent to {} — SMTP_USERNAME/SMTP_PASSWORD are unset or still placeholder " +
                    "values. Set real Gmail credentials (SMTP_USERNAME=your real Gmail address, SMTP_PASSWORD=a " +
                    "16-character Gmail App Password — NOT your normal Gmail password) as environment variables and " +
                    "restart the backend. Would have sent subject: {}", toEmail, subject);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("[DDT] Email sent to {}: {}", toEmail, subject);
        } catch (Exception e) {
            // Never let an email failure break the calling flow (e.g. driver assignment) — but
            // log the FULL exception (not just getMessage()), since a Gmail auth failure often
            // has the real reason ("Username and Password not accepted", "less secure app
            // blocked", etc.) several layers down in the cause chain.
            log.error("[DDT-ERROR] Failed to send email to {}: {}", toEmail, e.toString(), e);
        }
    }

    // ── Internal ─────────────────────────────────────────
    private void send(String to, String from, String body) {
        if (!enabled) {
            log.info("[DDT] SMS not sent — notification.enabled is false (set NOTIFICATIONS_ENABLED=true to turn SMS on). Would have sent to {}: {}", to, body);
            return;
        }
        // Distinguishes "feature turned off" from "turned on but Twilio was never actually
        // configured" — these used to log the exact same vague message, which is why SMS
        // silently never went out even after someone flipped notification.enabled=true: the
        // account SID/auth token/from-number were still the placeholder values from
        // application.properties (e.g. "YOUR_TWILIO_ACCOUNT_SID"), not blank, so nothing ever
        // flagged that they hadn't actually been replaced with real credentials.
        if (accountSid.isBlank() || authToken.isBlank() || smsFrom.isBlank()
                || accountSid.startsWith("YOUR_") || authToken.startsWith("YOUR_") || smsFrom.contains("XXXXXXXXXX")) {
            log.warn("[DDT-WARN] SMS not sent — notification.enabled=true but Twilio credentials look unset/placeholder " +
                    "(TWILIO_ACCOUNT_SID / TWILIO_AUTH_TOKEN / TWILIO_SMS_FROM). Set real values from your Twilio " +
                    "console. Would have sent to {}: {}", to, body);
            return;
        }
        try {
            String url = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            String credentials = Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes());
            headers.set("Authorization", "Basic " + credentials);

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("To", to);
            form.add("From", from);
            form.add("Body", body);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
            restTemplate.postForEntity(url, request, String.class);
            log.info("[DDT] SMS sent to {}", to);
        } catch (Exception e) {
            // Never let a notification failure break the booking/payment flow — but log loudly,
            // since a caught Twilio error here (bad credentials, unverified trial number, etc.)
            // is exactly the kind of failure that otherwise looks identical to "it worked".
            log.error("[DDT-ERROR] Twilio SMS to {} failed: {}", to, e.getMessage());
        }
    }

    // Normalizes a 10-digit Indian mobile number to E.164 (+91XXXXXXXXXX). Leaves already-formatted numbers as-is.
    private String toE164(String mobileNumber) {
        if (mobileNumber == null) return "";
        String digits = mobileNumber.replaceAll("[^0-9]", "");
        if (digits.length() == 10) return "+91" + digits;
        if (mobileNumber.startsWith("+")) return mobileNumber;
        return "+" + digits;
    }

    // ── In-app notifications ────────────────────────────────
    @Override
    @Transactional
    public void notifyCustomer(Long customerId, String title, String message, Notification.Type type, Long relatedReservationId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        Notification notification = Notification.builder()
                .customer(customer).title(title).message(message).type(type)
                .relatedReservationId(relatedReservationId)
                .build();
        notificationRepository.save(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDTO> getNotificationsForCustomer(Long customerId) {
        // IDOR fix: without this, any logged-in customer could read anyone else's notifications.
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(customerId);
        return notificationRepository.findByCustomer_CustomerIdOrderByCreatedAtDesc(customerId)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(Long customerId) {
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(customerId);
        return notificationRepository.countByCustomer_CustomerIdAndReadFalse(customerId);
    }

    @Override
    @Transactional
    public void markAsRead(Long notificationId) {
        // IDOR fix: without checking who the notification actually belongs to, any authenticated
        // user could mark (or infer the existence of) any other user's notification by guessing IDs.
        notificationRepository.findById(notificationId).ifPresent(n -> {
            Long ownerCustomerId = n.getCustomer() != null ? n.getCustomer().getCustomerId() : null;
            Long ownerDriverId = n.getDriver() != null ? n.getDriver().getDriverId() : null;
            com.rentmyride.security.SecurityUtils.assertOwnsAsCustomerOrDriver(ownerCustomerId, ownerDriverId);
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    @Override
    @Transactional
    public void markAllAsRead(Long customerId) {
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(customerId);
        List<Notification> unread = notificationRepository.findByCustomer_CustomerIdOrderByCreatedAtDesc(customerId)
                .stream().filter(n -> !n.isRead()).collect(Collectors.toList());
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    // ── Driver in-app notifications ─────────────────────────
    @Override
    @Transactional
    public void notifyDriver(Long driverId, String title, String message, Notification.Type type, Long relatedReservationId) {
        com.rentmyride.entities.Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new com.rentmyride.custom_exceptions.DriverNotFoundException(driverId));

        Notification notification = Notification.builder()
                .driver(driver).title(title).message(message).type(type)
                .relatedReservationId(relatedReservationId)
                .build();
        notificationRepository.save(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDTO> getNotificationsForDriver(Long driverId) {
        com.rentmyride.security.SecurityUtils.assertOwnsAsDriver(driverId);
        return notificationRepository.findByDriver_DriverIdOrderByCreatedAtDesc(driverId)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCountForDriver(Long driverId) {
        com.rentmyride.security.SecurityUtils.assertOwnsAsDriver(driverId);
        return notificationRepository.countByDriver_DriverIdAndReadFalse(driverId);
    }

    @Override
    @Transactional
    public void markAllAsReadForDriver(Long driverId) {
        com.rentmyride.security.SecurityUtils.assertOwnsAsDriver(driverId);
        List<Notification> unread = notificationRepository.findByDriver_DriverIdOrderByCreatedAtDesc(driverId)
                .stream().filter(n -> !n.isRead()).collect(Collectors.toList());
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    // ── Admin in-app notifications ──────────────────────────
    // Broadcast row (forAdmin=true, no customer/driver) — every admin sees the same list,
    // matching how there's a single shared admin panel rather than per-admin inboxes.
    @Override
    @Transactional
    public void notifyAdmins(String title, String message, Notification.Type type, Long relatedReservationId) {
        Notification notification = Notification.builder()
                .forAdmin(true).title(title).message(message).type(type)
                .relatedReservationId(relatedReservationId)
                .build();
        notificationRepository.save(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDTO> getNotificationsForAdmin() {
        return notificationRepository.findByForAdminTrueOrderByCreatedAtDesc()
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCountForAdmin() {
        return notificationRepository.countByForAdminTrueAndReadFalse();
    }

    @Override
    @Transactional
    public void markAllAsReadForAdmin() {
        List<Notification> unread = notificationRepository.findByForAdminTrueOrderByCreatedAtDesc()
                .stream().filter(n -> !n.isRead()).collect(Collectors.toList());
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    private NotificationDTO mapToDTO(Notification n) {
        return NotificationDTO.builder()
                .notificationId(n.getNotificationId())
                .title(n.getTitle()).message(n.getMessage()).type(n.getType())
                .relatedReservationId(n.getRelatedReservationId())
                .read(n.isRead()).createdAt(n.getCreatedAt())
                .build();
    }
}
