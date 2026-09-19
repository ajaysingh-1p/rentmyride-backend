package com.rentmyride.controller;

import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.dtos.CarDTO;
import com.rentmyride.entities.Car;
import com.rentmyride.service.CarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/cars")
@RequiredArgsConstructor
public class CarController {
    private final CarService carService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> addCar(@Valid @RequestBody CarDTO dto) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Car added.", carService.addCar(dto)));
    }

    @PutMapping("/{carId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> updateCar(@PathVariable Long carId, @Valid @RequestBody CarDTO dto) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Car updated.", carService.updateCar(carId, dto)));
    }

    @DeleteMapping("/{carId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> deleteCar(@PathVariable Long carId) {
        carService.deleteCar(carId);
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Car retired.", null));
    }

    @GetMapping("/{carId}")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getCarById(@PathVariable Long carId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Car fetched.", carService.getCarById(carId)));
    }

    @GetMapping
    public ResponseEntity<AuthResponseDTO.ApiResponse> getAllCars() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("All cars.", carService.getAllCars()));
    }

    // Issue #25 — paginated version, public just like the endpoint above (car browsing needs no login).
    @GetMapping("/page")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getAllCarsPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Cars page.",
                carService.getAllCarsPaged(page, Math.min(size, 100))));
    }

    @GetMapping("/available")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getAvailableCars() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Available cars.", carService.getAvailableCars()));
    }

    @GetMapping("/search")
    public ResponseEntity<AuthResponseDTO.ApiResponse> searchCars(@RequestParam String keyword) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Search results.", carService.searchCars(keyword)));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getByCategory(@PathVariable Car.CarCategory category) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Cars by category.", carService.getCarsByCategory(category)));
    }

    @GetMapping("/available-between")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getAvailableBetween(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate pickupDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate returnDate) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Available cars.",
                carService.getAvailableCarsBetweenDates(pickupDate, returnDate)));
    }

    @PatchMapping("/{carId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> updateStatus(@PathVariable Long carId,
            @RequestParam Car.AvailabilityStatus status) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Status updated.",
                carService.updateAvailabilityStatus(carId, status)));
    }
}
