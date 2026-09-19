package com.rentmyride.service.impl;

import com.rentmyride.custom_exceptions.InvalidOtpException;
import com.rentmyride.entities.OtpVerification;
import com.rentmyride.repository.OtpVerificationRepository;
import com.rentmyride.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpServiceImpl implements OtpService {

    private final OtpVerificationRepository otpRepository;
    private final JavaMailSender mailSender;

    @Value("${otp.expiry-minutes:5}")
    private int expiryMinutes;

    @Value("${otp.length:6}")
    private int otpLength;

    @Value("${otp.max-attempts:5}")
    private int maxAttempts;

    private final SecureRandom random = new SecureRandom();

    @Override
    @Transactional
    public void generateAndSendOtp(String identifier, OtpVerification.OtpPurpose purpose) {
        String code = saveNewOtp(identifier, purpose);
        sendOtpEmail(identifier, code, purpose);
    }

    @Override
    @Transactional
    public String generateOtp(String identifier, OtpVerification.OtpPurpose purpose) {
        // Same record, no email — the caller hands the code straight back to the customer's UI.
        return saveNewOtp(identifier, purpose);
    }

    private String saveNewOtp(String identifier, OtpVerification.OtpPurpose purpose) {
        String code = generateNumericCode(otpLength);

        OtpVerification otpVerification = OtpVerification.builder()
                .identifier(identifier)
                .otpCode(code)
                .purpose(purpose)
                .expiryTime(LocalDateTime.now().plusMinutes(expiryMinutes))
                .build();

        otpRepository.save(otpVerification);
        return code;
    }

    @Override
    @Transactional
    public void verifyOtp(String identifier, String otp, OtpVerification.OtpPurpose purpose) {
        OtpVerification record = otpRepository
                .findTopByIdentifierAndPurposeOrderByCreatedAtDesc(identifier, purpose)
                .orElseThrow(() -> new InvalidOtpException("No OTP was requested for this account."));

        if (record.isVerified()) {
            throw new InvalidOtpException("This OTP has already been used. Please request a new one.");
        }

        if (record.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw new InvalidOtpException("OTP has expired. Please request a new one.");
        }

        if (record.getAttempts() >= maxAttempts) {
            throw new InvalidOtpException("Too many incorrect attempts. Please request a new OTP.");
        }

        if (!record.getOtpCode().equals(otp)) {
            record.setAttempts(record.getAttempts() + 1);
            otpRepository.save(record);
            throw new InvalidOtpException("Invalid OTP. Please try again.");
        }

        record.setVerified(true);
        otpRepository.save(record);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isRecentlyVerified(String identifier, OtpVerification.OtpPurpose purpose) {
        return otpRepository.findTopByIdentifierAndPurposeOrderByCreatedAtDesc(identifier, purpose)
                .map(record -> record.isVerified() && record.getExpiryTime().isAfter(LocalDateTime.now().minusMinutes(expiryMinutes)))
                .orElse(false);
    }

    private String generateNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private void sendOtpEmail(String toEmail, String code, OtpVerification.OtpPurpose purpose) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            String subject;
            switch (purpose) {
                case LOGIN -> subject = "RentMyRide — Your Login OTP";
                case FORGOT_PASSWORD -> subject = "RentMyRide — Password Reset OTP";
                default -> subject = "RentMyRide — Verification Code";
            }
            message.setSubject(subject);
            message.setText(
                    "Your OTP code is: " + code + "\n\n" +
                    "This code will expire in " + expiryMinutes + " minutes.\n" +
                    "If you did not request this, please ignore this email."
            );
            mailSender.send(message);
        } catch (Exception e) {
            // Don't leak SMTP errors to the client, but do log them for debugging.
            log.error("[DDT-ERROR] Failed to send OTP email to {}: {}", toEmail, e.getMessage());
            throw new InvalidOtpException("Failed to send OTP email. Please try again later.");
        }
    }
}
