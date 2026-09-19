package com.rentmyride.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    // Set instead of `customer` when this notification is meant for a driver (e.g. new trip assigned)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    // Set instead of `customer`/`driver` when this notification is a broadcast alert for the
    // admin panel (new booking, payment received, etc.) — not owned by one specific admin, since
    // any admin logging in should see the same operational alerts.
    @Column(name = "for_admin", nullable = false)
    @Builder.Default
    private boolean forAdmin = false;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, columnDefinition = "VARCHAR(30)")
    private Type type;

    // Optional — lets the frontend deep-link to the relevant booking
    @Column(name = "related_reservation_id")
    private Long relatedReservationId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.read = false;
    }

    public enum Type {
        BOOKING_CONFIRMED, PAYMENT_DUE, CANCELLATION, DRIVER_ASSIGNED, TRIP_ASSIGNED, GENERAL,
        // Admin-facing alerts
        NEW_RESERVATION, PAYMENT_RECEIVED
    }
}
