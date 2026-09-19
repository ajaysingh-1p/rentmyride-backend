package com.rentmyride.repository;

import com.rentmyride.entities.BookingIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingIntentRepository extends JpaRepository<BookingIntent, Long> {

    // Intents older than the cutoff, not yet reminded — candidates for the abandoned-booking email
    @Query("SELECT bi FROM BookingIntent bi WHERE bi.reminderSent = false AND bi.createdAt < :cutoff")
    List<BookingIntent> findStaleUnreminded(@Param("cutoff") LocalDateTime cutoff);

    void deleteByCustomer_CustomerIdAndCar_CarId(Long customerId, Long carId);
}
