package com.rentmyride.repository;

import com.rentmyride.entities.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // Find by email
    Optional<Customer> findByEmail(String email);

    // Row-level lock (SELECT ... FOR UPDATE) so concurrent wallet deductions serialize instead of
    // both reading the same balance and racing to write it back — fixes issue #14 (wallet race).
    // A second concurrent transaction blocks here until the first one commits/rolls back, so it
    // always sees the up-to-date balance rather than a stale one.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Customer c WHERE c.customerId = :customerId")
    Optional<Customer> findByIdForUpdate(@Param("customerId") Long customerId);

    // Find by mobile number
    Optional<Customer> findByMobileNumber(String mobileNumber);

    // Find by email or mobile (for login)
    @Query("SELECT c FROM Customer c WHERE c.email = :identifier OR c.mobileNumber = :identifier")
    Optional<Customer> findByEmailOrMobile(@Param("identifier") String identifier);

    // Find by driving license number
    Optional<Customer> findByDrivingLicenseNumber(String drivingLicenseNumber);

    // Find by Aadhar number
    Optional<Customer> findByAadharNumber(String aadharNumber);

    // ── Referral Program ──
    Optional<Customer> findByReferralCode(String referralCode);
    boolean existsByReferralCode(String referralCode);
    long countByReferredByCode(String referralCode);

    // Find by account status
    List<Customer> findByAccountStatus(Customer.AccountStatus accountStatus);

    // Check if email exists
    boolean existsByEmail(String email);

    // Check if mobile exists
    boolean existsByMobileNumber(String mobileNumber);

    // Check if Aadhar exists
    boolean existsByAadharNumber(String aadharNumber);

    // Check if DL exists
    boolean existsByDrivingLicenseNumber(String drivingLicenseNumber);

    // Search customers by name or email
    @Query("SELECT c FROM Customer c WHERE " +
           "LOWER(c.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.lastName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "c.mobileNumber LIKE CONCAT('%', :keyword, '%')")
    List<Customer> searchCustomers(@Param("keyword") String keyword);

    // Find customers by city
    List<Customer> findByCityIgnoreCase(String city);

    // Count active customers
    long countByAccountStatus(Customer.AccountStatus status);

    // Find recently registered customers
    @Query("SELECT c FROM Customer c ORDER BY c.createdAt DESC")
    List<Customer> findRecentCustomers(org.springframework.data.domain.Pageable pageable);
}
