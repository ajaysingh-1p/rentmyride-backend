package com.rentmyride.service;

import com.rentmyride.dtos.NotificationDTO;
import com.rentmyride.entities.Notification;

import java.util.List;

public interface NotificationService {

    /**
     * Sends a plain SMS to the given Indian mobile number (10-digit or already E.164).
     */
    void sendSms(String mobileNumber, String message);

    /**
     * Sends an SMS confirmation — used for important events like booking confirmation.
     */
    void sendBookingConfirmation(String customerName, String mobileNumber, Long reservationId,
                                  String carLabel, String pickupDate, double amount);

    /** Sends a plain-text email. Silently logs (no throw) if mail isn't configured. */
    void sendEmail(String toEmail, String subject, String body);

    // ── In-app notifications (bell icon in the customer dashboard) ──
    void notifyCustomer(Long customerId, String title, String message, Notification.Type type, Long relatedReservationId);
    List<NotificationDTO> getNotificationsForCustomer(Long customerId);
    long getUnreadCount(Long customerId);
    void markAsRead(Long notificationId);
    void markAllAsRead(Long customerId);

    // ── In-app notifications (bell icon in the driver dashboard) ──
    void notifyDriver(Long driverId, String title, String message, Notification.Type type, Long relatedReservationId);
    List<NotificationDTO> getNotificationsForDriver(Long driverId);
    long getUnreadCountForDriver(Long driverId);
    void markAllAsReadForDriver(Long driverId);

    // ── In-app notifications (bell icon in the admin dashboard) ──
    // Broadcast to every admin — not tied to one adminId — since these are operational alerts
    // (new booking, payment received) any admin logging into the panel should see.
    void notifyAdmins(String title, String message, Notification.Type type, Long relatedReservationId);
    List<NotificationDTO> getNotificationsForAdmin();
    long getUnreadCountForAdmin();
    void markAllAsReadForAdmin();
}

