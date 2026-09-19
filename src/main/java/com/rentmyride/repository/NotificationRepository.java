package com.rentmyride.repository;

import com.rentmyride.entities.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByCustomer_CustomerIdOrderByCreatedAtDesc(Long customerId);
    long countByCustomer_CustomerIdAndReadFalse(Long customerId);

    List<Notification> findByDriver_DriverIdOrderByCreatedAtDesc(Long driverId);
    long countByDriver_DriverIdAndReadFalse(Long driverId);

    List<Notification> findByForAdminTrueOrderByCreatedAtDesc();
    long countByForAdminTrueAndReadFalse();
}
