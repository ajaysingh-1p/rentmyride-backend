package com.rentmyride.repository;

import com.rentmyride.entities.PromoCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {
    Optional<PromoCode> findByCodeIgnoreCase(String code);
    List<PromoCode> findAllByOrderByCreatedAtDesc();

    // Row-level lock — fixes issue #15 (promo code usage race). Without it, two customers
    // redeeming the same LAST-remaining use of a code at nearly the same time could both read
    // usedCount as one-below-the-limit, both pass checkEligibility(), and both get the discount —
    // over-redeeming a code past its intended usageLimit.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PromoCode p WHERE UPPER(p.code) = UPPER(:code)")
    Optional<PromoCode> findByCodeIgnoreCaseForUpdate(@Param("code") String code);
}
