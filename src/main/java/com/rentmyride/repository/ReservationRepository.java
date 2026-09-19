package com.rentmyride.repository;

import com.rentmyride.entities.Reservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // Row-level lock — used by payForReservation() and cancelReservation(), both of which do a
    // check-then-act on this row's payment/status fields (remaining balance, already-cancelled
    // check) before writing back and — in cancelReservation's case — calling the Razorpay refund
    // API. Without the lock, two near-simultaneous calls (double-tap, retried request, or a
    // webhook landing at the same moment as a browser call) could both read the pre-write state
    // and both act on it: two payments both computing the same "remaining" balance (causing
    // overpayment), or two cancellations both passing the "not already cancelled" check and both
    // triggering a REAL Razorpay refund call — refunding the customer twice.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.reservationId = :reservationId")
    java.util.Optional<Reservation> findByIdForUpdate(@Param("reservationId") Long reservationId);

    // Issue #24 fix (N+1): plain findAll() + mapToDTO() was firing one extra SELECT per row per
    // lazy association (customer, car, assignedDriver) — 3N+1 queries for a page of N
    // reservations. @EntityGraph tells Hibernate to JOIN FETCH those associations in the SAME
    // query, so listing reservations is now O(1) queries regardless of how many rows come back.
    @EntityGraph(attributePaths = {"customer", "car", "assignedDriver"})
    @Query("SELECT r FROM Reservation r")
    List<Reservation> findAllWithDetails();

    // Issue #25 fix (pagination): the paginated counterpart — used by the new page-aware admin
    // listing endpoint so "get all reservations" no longer means "return every row in the table
    // in one response" as the business grows.
    @EntityGraph(attributePaths = {"customer", "car", "assignedDriver"})
    @Query(value = "SELECT r FROM Reservation r", countQuery = "SELECT COUNT(r) FROM Reservation r")
    Page<Reservation> findAllWithDetails(Pageable pageable);

    List<Reservation> findByCustomer_CustomerId(Long customerId);

    // For ReservationExpiryService — abandoned unpaid bookings older than the grace window.
    List<Reservation> findByReservationStatusAndCreatedAtBefore(
            Reservation.ReservationStatus status, java.time.LocalDateTime cutoff);

    // Race-condition fix: this used to be a read-the-list-then-loop-and-save() pattern in
    // ReservationExpiryService, which re-checked "amountPaid > 0" only against data read SEVERAL
    // SECONDS before the save actually happened. If a customer's payment landed (payForReservation
    // committing amountPaid + CONFIRMED status) in that window, the sweep's save() would silently
    // overwrite their just-paid, just-confirmed booking back to CANCELLED — an already-paying
    // customer loses their booking to a background job. A single UPDATE ... WHERE statement is
    // atomic: the DB evaluates status/amountPaid/createdAt at the moment it writes, not against a
    // stale in-memory read, so a reservation that got paid in the meantime is simply excluded by
    // the WHERE clause and left untouched.
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Reservation r SET r.reservationStatus = 'CANCELLED' " +
           "WHERE r.reservationStatus = 'PENDING' AND (r.amountPaid IS NULL OR r.amountPaid <= 0) " +
           "AND r.createdAt < :cutoff")
    int cancelAbandonedUnpaidReservations(@Param("cutoff") java.time.LocalDateTime cutoff);

    // New — for the "you have a due amount, pay now" login popup. Picks up both an unpaid
    // initial booking (still PENDING/CONFIRMED, e.g. only a deposit was paid) and a completed
    // rental with an outstanding balance (extra km/damage charges added after the trip ended).
    @Query("SELECT r FROM Reservation r WHERE r.customer.customerId = :customerId " +
           "AND r.reservationStatus IN ('CONFIRMED','COMPLETED') " +
           "AND r.amountPaid < r.estimatedAmount ORDER BY r.pickupDate DESC")
    List<Reservation> findWithDueBalanceForCustomer(@Param("customerId") Long customerId);
    List<Reservation> findByCar_CarId(Long carId);
    @Query("SELECT r FROM Reservation r WHERE r.car.carId = :carId AND r.reservationStatus NOT IN ('CANCELLED','REJECTED')")
    List<Reservation> findActiveByCarId(@Param("carId") Long carId);
    List<Reservation> findByReservationStatus(Reservation.ReservationStatus status);

    /** Self-drive handover desk: confirmed self-drive bookings whose pickup date has arrived. */
    @EntityGraph(attributePaths = {"customer", "car"})
    List<Reservation> findByTripModeAndReservationStatusAndPickupDateLessThanEqual(
            Reservation.TripMode tripMode,
            Reservation.ReservationStatus reservationStatus,
            LocalDate pickupDate);
    List<Reservation> findByCustomer_CustomerIdAndReservationStatus(Long customerId, Reservation.ReservationStatus status);

    // Reservations assigned to a driver that haven't been picked up yet (no Rental exists) —
    // used to populate the driver's own pickup-form dropdown.
    // Fix: Reservation has no `rental` field — the OneToOne is owned by Rental
    // (Rental.reservation), so "r.rental IS NULL" doesn't compile against Reservation's
    // metamodel. A NOT EXISTS subquery against Rental achieves the same "no rental row yet" check.
    @Query("SELECT r FROM Reservation r WHERE r.assignedDriver.driverId = :driverId " +
           "AND r.reservationStatus = 'CONFIRMED' " +
           "AND NOT EXISTS (SELECT 1 FROM Rental rt WHERE rt.reservation = r)")
    List<Reservation> findPendingPickupsForDriver(@Param("driverId") Long driverId);

    // Trip requests assigned to a driver still awaiting their accept/reject response.
    @Query("SELECT r FROM Reservation r WHERE r.assignedDriver.driverId = :driverId " +
           "AND r.driverAssignmentStatus = 'PENDING'")
    List<Reservation> findPendingTripRequestsForDriver(@Param("driverId") Long driverId);

    List<Reservation> findByAssignedDriver_DriverId(Long driverId);

    @Query("SELECT r FROM Reservation r WHERE r.pickupDate BETWEEN :start AND :end")
    List<Reservation> findByPickupDateBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    // Issue fix: once a trip is COMPLETED, that date/slot must free back up for a new booking on
    // the same car/driver — e.g. same-day back-to-back trips. Previously only CANCELLED/REJECTED
    // were excluded here, so a completed trip's original date range kept "blocking" new bookings
    // for that car/driver indefinitely, even though the car's own availabilityStatus flag had
    // already been reset to AVAILABLE by completeRental() — this query silently overrode that.
    @Query("SELECT r FROM Reservation r WHERE r.car.carId = :carId AND r.reservationStatus NOT IN ('CANCELLED','REJECTED','COMPLETED') " +
           "AND (:pickup <= r.returnDate AND :returnD >= r.pickupDate)")
    List<Reservation> findConflictingReservations(@Param("carId") Long carId,
                                                   @Param("pickup") LocalDate pickup,
                                                   @Param("returnD") LocalDate returnD);

    // Used when assigning a driver — a driver can't be double-booked to two overlapping trips.
    @Query("SELECT r FROM Reservation r WHERE r.assignedDriver.driverId = :driverId " +
           "AND r.reservationStatus NOT IN ('CANCELLED','REJECTED','COMPLETED') " +
           "AND (:pickup <= r.returnDate AND :returnD >= r.pickupDate)")
    List<Reservation> findConflictingReservationsForDriver(@Param("driverId") Long driverId,
                                                             @Param("pickup") LocalDate pickup,
                                                             @Param("returnD") LocalDate returnD);

    long countByReservationStatus(Reservation.ReservationStatus status);

    @Query("SELECT r FROM Reservation r ORDER BY r.createdAt DESC")
    List<Reservation> findRecentReservations(org.springframework.data.domain.Pageable pageable);
}
