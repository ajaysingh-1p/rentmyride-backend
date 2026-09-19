// ─────────────────────────────────────────────────────────────
// FILE: service/CarService.java
// ─────────────────────────────────────────────────────────────
package com.rentmyride.service;

import com.rentmyride.dtos.CarDTO;
import com.rentmyride.entities.Car;
import java.time.LocalDate;
import java.util.List;

public interface CarService {
    CarDTO addCar(CarDTO carDTO);
    CarDTO updateCar(Long carId, CarDTO carDTO);
    void deleteCar(Long carId);
    CarDTO getCarById(Long carId);
    List<CarDTO> getAllCars();
    com.rentmyride.dtos.PageResponse<CarDTO> getAllCarsPaged(int page, int size);
    List<CarDTO> getAvailableCars();
    List<CarDTO> searchCars(String keyword);
    List<CarDTO> getCarsByCategory(Car.CarCategory category);
    List<CarDTO> getAvailableCarsBetweenDates(LocalDate pickupDate, LocalDate returnDate);
    CarDTO updateAvailabilityStatus(Long carId, Car.AvailabilityStatus status);
}
