package com.rentmyride.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Recorded when a customer picks dates on a car's booking page — if no reservation follows
// within a couple of hours, a scheduled job emails them a reminder. Cleared once they book.
@Entity
@Table(name = "booking_intent")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "intent_id")
    private Long intentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_id", nullable = false)
    private Car car;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reminder_sent", nullable = false)
    @Builder.Default
    private boolean reminderSent = false;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
