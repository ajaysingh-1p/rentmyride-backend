package com.rentmyride.repository;

import com.rentmyride.entities.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    // Get the most recent OTP for a given identifier + purpose
    Optional<OtpVerification> findTopByIdentifierAndPurposeOrderByCreatedAtDesc(
            String identifier, OtpVerification.OtpPurpose purpose);
}
