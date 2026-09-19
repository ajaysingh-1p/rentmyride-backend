package com.rentmyride.repository;

import com.rentmyride.entities.Driver;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
    Optional<Driver> findByEmail(String email);
    // Mirrors Customer's identifier lookup — used by driver OTP-login/forgot-password so a
    // driver can sign in with either their email or mobile number, same as a customer can.
    @Query("SELECT d FROM Driver d WHERE d.email = :identifier OR d.mobileNumber = :identifier")
    Optional<Driver> findByEmailOrMobile(@Param("identifier") String identifier);
    boolean existsByEmail(String email);
    boolean existsByMobileNumber(String mobileNumber);
    // Used by updateDriver() to allow a driver to keep their OWN existing email while still
    // rejecting a change to an email already used by someone else.
    boolean existsByEmailAndDriverIdNot(String email, Long driverId);
    List<Driver> findByStatus(Driver.Status status);

    // Row-level lock — fixes the double-booking race in ReservationServiceImpl.assignDriver():
    // two admins assigning overlapping trips to the SAME driver at nearly the same moment could
    // both run findConflictingReservationsForDriver(), both see "no conflicts" (because neither
    // assignment had saved yet), and both succeed — leaving the driver double-booked for
    // overlapping windows. This serializes those two assignment attempts on the driver row.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Driver d WHERE d.driverId = :driverId")
    Optional<Driver> findByIdForUpdate(@Param("driverId") Long driverId);
}
