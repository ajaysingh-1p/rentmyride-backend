package com.rentmyride.repository;

import com.rentmyride.entities.Rental;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RentalRepository extends JpaRepository<Rental, Long> {

    // Row-level lock (SELECT ... FOR UPDATE) on a single rental — fixes the same class of race
    // as CarRepository.findByIdForUpdate() (issue #13), but for the drop-off/complete-trip flow.
    // Two simultaneous completeRental() calls for the SAME rental (e.g. a driver double-tapping
    // "Complete Trip", a retried network request, or two staff devices submitting the return form
    // together) now serialize: the second caller blocks here until the first transaction commits,
    // so its own ACTIVE-status check then correctly sees COMPLETED and is rejected — instead of
    // both reading ACTIVE at the same time and both racing through pricing, trust-score updates,
    // and invoice generation.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Rental r WHERE r.rentalId = :rentalId")
    Optional<Rental> findByIdForUpdate(@Param("rentalId") Long rentalId);

    // Issue #24/#25 fixes — same pattern as ReservationRepository above.
    @EntityGraph(attributePaths = {"reservation", "customer", "car", "driver"})
    @Query("SELECT r FROM Rental r")
    List<Rental> findAllWithDetails();

    @EntityGraph(attributePaths = {"reservation", "customer", "car", "driver"})
    @Query(value = "SELECT r FROM Rental r", countQuery = "SELECT COUNT(r) FROM Rental r")
    Page<Rental> findAllWithDetails(Pageable pageable);

    // Bug fix (N+1): mapToDTO() touches r.getReservation()/.getCustomer()/.getCar()/.getDriver()
    // for every row — without @EntityGraph, Hibernate lazy-loads each of those separately PER
    // ROW, so listing a driver's rentals fired one query for the list plus 3-4 more per rental
    // (visible in the logs as the exact same "SELECT ... WHERE reservation_id=?" repeated back
    // to back for the same ID). @EntityGraph JOIN FETCHes all four in the single listing query,
    // same pattern findAllWithDetails() below already uses.
    @EntityGraph(attributePaths = {"reservation", "customer", "car", "driver"})
    List<Rental> findByCustomer_CustomerId(Long customerId);

    List<Rental> findByCar_CarId(Long carId);

    @EntityGraph(attributePaths = {"reservation", "customer", "car", "driver"})
    List<Rental> findByDriver_DriverId(Long driverId);

    @EntityGraph(attributePaths = {"reservation", "customer", "car", "driver"})
    List<Rental> findByRentalStatus(Rental.RentalStatus status);

    @EntityGraph(attributePaths = {"reservation", "customer", "car", "driver"})
    List<Rental> findByDamageApprovalStatus(Rental.DamageApprovalStatus status);

    Optional<Rental> findByReservation_ReservationId(Long reservationId);

    @EntityGraph(attributePaths = {"reservation", "customer", "car", "driver"})
    List<Rental> findByCustomer_CustomerIdAndRentalStatus(Long customerId, Rental.RentalStatus status);

    @EntityGraph(attributePaths = {"reservation", "customer", "car", "driver"})
    @Query("SELECT r FROM Rental r WHERE r.rentalStatus = 'ACTIVE' ORDER BY r.createdAt DESC")
    List<Rental> findAllActiveRentals();

    @Query("SELECT SUM(r.totalAmount) FROM Rental r WHERE r.rentalStatus = 'COMPLETED'")
    Double getTotalRevenue();

    @Query("SELECT SUM(r.totalAmount) FROM Rental r WHERE r.rentalStatus = 'COMPLETED' " +
           "AND MONTH(r.actualReturnDatetime) = :month AND YEAR(r.actualReturnDatetime) = :year")
    Double getMonthlyRevenue(@Param("month") int month, @Param("year") int year);

    long countByRentalStatus(Rental.RentalStatus status);
}
