package com.rentmyride.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "feedback")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feedback_id")
    private Long feedbackId;

    // Race-condition fix: submitFeedback() checks existsByRental_RentalId() before inserting,
    // but that check-then-act isn't atomic — two near-simultaneous submissions for the same
    // rental (double-tap on a slow connection) could both see "no feedback yet" and both insert,
    // double-counting this rental in car rating averages and double-awarding the trust-score
    // bonus. unique = true adds a DB-level backstop: the second insert fails with a constraint
    // violation instead of silently succeeding.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_id", nullable = false, unique = true)
    private Rental rental;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "car_condition", nullable = false)
    private Integer carCondition;

    @Column(name = "staff_behavior", nullable = false)
    private Integer staffBehavior;

    @Column(name = "value_for_money", nullable = false)
    private Integer valueForMoney;

    @Column(name = "booking_process", nullable = false)
    private Integer bookingProcess;

    @Column(name = "overall_service", nullable = false)
    private Integer overallService;

    // Optional — only present if the reservation had an assigned driver. Lets customers
    // rate the driver separately from the car/booking experience.
    @Column(name = "driver_rating")
    private Integer driverRating;

    @Column(name = "comments", length = 1000)
    private String comments;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
