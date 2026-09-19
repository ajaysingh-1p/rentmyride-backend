package com.rentmyride.repository;

import com.rentmyride.entities.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // Issue #24/#25 fixes — same pattern as ReservationRepository.
    @EntityGraph(attributePaths = {"rental", "customer"})
    @Query("SELECT p FROM Payment p")
    List<Payment> findAllWithDetails();

    @EntityGraph(attributePaths = {"rental", "customer"})
    @Query(value = "SELECT p FROM Payment p", countQuery = "SELECT COUNT(p) FROM Payment p")
    Page<Payment> findAllWithDetails(Pageable pageable);

    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);
    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);
    // Bug fix: this used to be Optional<Payment> (single-result) — but rental is now @ManyToOne
    // (issue #46 area: a rental CAN legitimately have more than one Payment row, e.g. a FAILED
    // attempt followed by a successful retry). A single-result query throws
    // IncorrectResultSizeDataAccessException the moment a second row exists for the same rental,
    // which a failed-then-retried payment makes trivial to hit. Returns the list; callers pick
    // the one they actually want (see PaymentServiceImpl.getPaymentByRentalId).
    List<Payment> findByRental_RentalId(Long rentalId);
    // Issue #46 fix: application-level enforcement of "at most one SUCCESSFUL payment per
    // rental" — the real invariant that matters, without a blanket DB unique=true on rental_id
    // (which would incorrectly reject legitimate FAILED-then-retry rows, or the reservation-time
    // deposit+balance payments that intentionally share nothing with rental_id at all).
    long countByRental_RentalIdAndPaymentStatus(Long rentalId, Payment.PaymentStatus status);
    List<Payment> findByCustomer_CustomerId(Long customerId);
    List<Payment> findByPaymentStatus(Payment.PaymentStatus status);
    List<Payment> findByPaymentMethod(Payment.PaymentMethod method);

    @Query("SELECT SUM(p.totalAmount) FROM Payment p WHERE p.paymentStatus = 'SUCCESS'")
    Double getTotalCollected();

    @Query("SELECT SUM(p.totalAmount) FROM Payment p WHERE p.paymentStatus = 'SUCCESS' " +
           "AND MONTH(p.paymentDatetime) = :month AND YEAR(p.paymentDatetime) = :year")
    Double getMonthlyCollection(@Param("month") int month, @Param("year") int year);

    long countByPaymentStatus(Payment.PaymentStatus status);

    @Query("SELECT p FROM Payment p ORDER BY p.createdAt DESC")
    List<Payment> findRecentPayments(org.springframework.data.domain.Pageable pageable);
}
