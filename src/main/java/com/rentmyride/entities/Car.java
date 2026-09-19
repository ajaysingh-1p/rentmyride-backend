package com.rentmyride.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "car")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Car {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "car_id")
    private Long carId;

    @Column(name = "brand", nullable = false, length = 50)
    private String brand;

    @Column(name = "model", nullable = false, length = 50)
    private String model;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "registration_number", nullable = false, unique = true, length = 20)
    private String registrationNumber;

    @Column(name = "color", length = 30)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type", nullable = false)
    private FuelType fuelType;

    @Enumerated(EnumType.STRING)
    @Column(name = "transmission_type", nullable = false)
    private TransmissionType transmissionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "car_category", nullable = false)
    private CarCategory carCategory;

    @Column(name = "seating_capacity", nullable = false)
    private Integer seatingCapacity;

    @Column(name = "rent_per_day", nullable = false)
    private Double rentPerDay;

    // Rate charged per kilometre — used to calculate booking price based on
    // the distance between pickup and drop-off locations.
    @Column(name = "rate_per_km")
    private Double ratePerKm;

    // Charge per night the car stays out on an outstation trip (min ₹300, enforced in service layer)
    @Column(name = "night_charge_per_night")
    private Double nightChargePerNight;

    @Column(name = "mileage_kmpl")
    private Double mileageKmpl;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false)
    private AvailabilityStatus availabilityStatus;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.availabilityStatus = AvailabilityStatus.AVAILABLE;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Relationships
    // Issue #38 fix: CascadeType.ALL includes CascadeType.REMOVE — if anyone ever calls
    // carRepository.delete(car) (a hard delete), Hibernate would cascade and delete every
    // Reservation and Rental for that car too, wiping historical booking/invoice data that
    // should be permanent business/financial records. deleteCar() in CarServiceImpl already only
    // ever retires a car (soft "RETIRED" status), never hard-deletes it — but leaving ALL cascade
    // wired up is a loaded gun for whoever touches this next. PERSIST/MERGE cover the only cascade
    // behavior this app actually uses (saving a Car also saving newly-associated child rows);
    // deletion is deliberately NOT cascaded.
    // Bug fix: reservations/rentals were @OneToMany reverse-mappedBy fields that NOTHING in the
    // codebase ever actually read (zero calls to car.getReservations()/car.getRentals()) — but
    // their mere presence meant Lombok's @Data equals()/hashCode()/toString() on Car could
    // silently trigger a FULL collection fetch (every reservation/rental for that car, ever)
    // any time two Car objects got compared or a list of them got deduplicated/logged. Same
    // "why is this query firing for no reason" bug as Rental.invoice/Reservation.rental — removed.
    // Enums
    public enum FuelType {
        PETROL, DIESEL, CNG, ELECTRIC, HYBRID
    }

    public enum TransmissionType {
        MANUAL, AUTOMATIC
    }

    public enum CarCategory {
        HATCHBACK, SEDAN, SUV, LUXURY, VAN, TEMPO_TRAVELLER
    }

    public enum AvailabilityStatus {
        AVAILABLE, BOOKED, UNDER_MAINTENANCE, RETIRED
    }
}
