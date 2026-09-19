package com.rentmyride.service;

import com.rentmyride.entities.OtpVerification;

public interface OtpService {

    /**
     * Generates a new OTP for the given identifier + purpose, saves it, and emails it out.
     */
    void generateAndSendOtp(String identifier, OtpVerification.OtpPurpose purpose);

    /**
     * Verifies the OTP. Throws InvalidOtpException if it's wrong, expired, or too many attempts were made.
     * Marks the OTP as verified (consumed) on success.
     */
    void verifyOtp(String identifier, String otp, OtpVerification.OtpPurpose purpose);

    /**
     * Confirms that the most recent OTP for this identifier+purpose was already verified.
     * Used as a guard before sensitive actions like resetting a password.
     */
    boolean isRecentlyVerified(String identifier, OtpVerification.OtpPurpose purpose);

    /**
     * Generates + saves an OTP and RETURNS the code instead of emailing it. Used by the
     * self-drive handover, where the customer reads the code off their own screen to the
     * staff at the counter — emailing it there would be both pointless and slow.
     */
    String generateOtp(String identifier, OtpVerification.OtpPurpose purpose);
}
