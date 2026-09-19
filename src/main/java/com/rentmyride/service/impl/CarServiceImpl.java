package com.rentmyride.service.impl;

import com.rentmyride.custom_exceptions.CarNotFoundException;
import com.rentmyride.custom_exceptions.DuplicateRegistrationException;
import com.rentmyride.custom_exceptions.InvalidPricingException;
import com.rentmyride.dtos.CarDTO;
import com.rentmyride.entities.Car;
import com.rentmyride.repository.CarRepository;
import com.rentmyride.service.CarService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarServiceImpl implements CarService {

    private final CarRepository carRepository;

    @Override
    @Transactional
    public CarDTO addCar(CarDTO dto) {
        if (carRepository.findByRegistrationNumber(dto.getRegistrationNumber()).isPresent())
            throw new DuplicateRegistrationException("Car with registration " + dto.getRegistrationNumber() + " already exists.");
        validatePricing(dto);
        return mapToDTO(carRepository.save(mapToEntity(dto)));
    }

    // Issue #22 fix: nothing previously stopped an admin (or a buggy admin-panel request) from
    // saving a car with a zero or negative rentPerDay/ratePerKm — which would then let a customer
    // book that car for free, or the system would compute a negative total charge.
    private void validatePricing(CarDTO dto) {
        if (dto.getRentPerDay() == null || dto.getRentPerDay() <= 0)
            throw new InvalidPricingException("Rent per day must be a positive amount.");
        if (dto.getRatePerKm() != null && dto.getRatePerKm() < 0)
            throw new InvalidPricingException("Rate per km cannot be negative.");
        if (dto.getNightChargePerNight() != null && dto.getNightChargePerNight() < 0)
            throw new InvalidPricingException("Night charge cannot be negative.");
    }

    @Override
    @Transactional
    public CarDTO updateCar(Long carId, CarDTO dto) {
        Car car = carRepository.findById(carId).orElseThrow(() -> new CarNotFoundException(carId));
        if (carRepository.existsByRegistrationNumberAndCarIdNot(dto.getRegistrationNumber(), carId))
            throw new DuplicateRegistrationException("Registration number already in use.");
        validatePricing(dto);
        car.setBrand(dto.getBrand());
        car.setModel(dto.getModel());
        car.setYear(dto.getYear());
        car.setRegistrationNumber(dto.getRegistrationNumber());
        car.setColor(dto.getColor());
        car.setFuelType(dto.getFuelType());
        car.setTransmissionType(dto.getTransmissionType());
        car.setCarCategory(dto.getCarCategory());
        car.setSeatingCapacity(dto.getSeatingCapacity());
        car.setRentPerDay(dto.getRentPerDay());
        car.setRatePerKm(dto.getRatePerKm());
        car.setNightChargePerNight(dto.getNightChargePerNight());
        car.setMileageKmpl(dto.getMileageKmpl());
        car.setImageUrl(dto.getImageUrl());
        car.setDescription(dto.getDescription());
        return mapToDTO(carRepository.save(car));
    }

    @Override
    @Transactional
    public void deleteCar(Long carId) {
        Car car = carRepository.findById(carId).orElseThrow(() -> new CarNotFoundException(carId));
        car.setAvailabilityStatus(Car.AvailabilityStatus.RETIRED);
        carRepository.save(car);
    }

    @Override
    @Transactional(readOnly = true)
    public CarDTO getCarById(Long carId) {
        return mapToDTO(carRepository.findById(carId).orElseThrow(() -> new CarNotFoundException(carId)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarDTO> getAllCars() {
        return carRepository.findAll().stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    // Issue #25 fix — paginated version. Cars is the highest-traffic list in the app (public
    // browsing page), so this is arguably the most important of the pagination fixes.
    @Override
    @Transactional(readOnly = true)
    public com.rentmyride.dtos.PageResponse<CarDTO> getAllCarsPaged(int page, int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("createdAt").descending());
        return com.rentmyride.dtos.PageResponse.from(carRepository.findAll(pageable).map(this::mapToDTO));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarDTO> getAvailableCars() {
        return carRepository.findByAvailabilityStatus(Car.AvailabilityStatus.AVAILABLE)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarDTO> searchCars(String keyword) {
        return carRepository.searchByBrandOrModel(keyword)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarDTO> getCarsByCategory(Car.CarCategory category) {
        return carRepository.findByCarCategory(category)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarDTO> getAvailableCarsBetweenDates(LocalDate pickupDate, LocalDate returnDate) {
        return carRepository.findAvailableCarsBetweenDates(pickupDate, returnDate)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CarDTO updateAvailabilityStatus(Long carId, Car.AvailabilityStatus status) {
        Car car = carRepository.findById(carId).orElseThrow(() -> new CarNotFoundException(carId));
        car.setAvailabilityStatus(status);
        return mapToDTO(carRepository.save(car));
    }

    private CarDTO mapToDTO(Car car) {
        return CarDTO.builder()
                .carId(car.getCarId()).brand(car.getBrand()).model(car.getModel())
                .year(car.getYear()).registrationNumber(car.getRegistrationNumber())
                .color(car.getColor()).fuelType(car.getFuelType())
                .transmissionType(car.getTransmissionType()).carCategory(car.getCarCategory())
                .seatingCapacity(car.getSeatingCapacity()).rentPerDay(car.getRentPerDay())
                .ratePerKm(car.getRatePerKm()).nightChargePerNight(car.getNightChargePerNight())
                .mileageKmpl(car.getMileageKmpl()).availabilityStatus(car.getAvailabilityStatus())
                .imageUrl(car.getImageUrl()).description(car.getDescription())
                .build();
    }

    private Car mapToEntity(CarDTO dto) {
        return Car.builder()
                .brand(dto.getBrand()).model(dto.getModel()).year(dto.getYear())
                .registrationNumber(dto.getRegistrationNumber()).color(dto.getColor())
                .fuelType(dto.getFuelType()).transmissionType(dto.getTransmissionType())
                .carCategory(dto.getCarCategory()).seatingCapacity(dto.getSeatingCapacity())
                .rentPerDay(dto.getRentPerDay()).ratePerKm(dto.getRatePerKm())
                .nightChargePerNight(dto.getNightChargePerNight()).mileageKmpl(dto.getMileageKmpl())
                .imageUrl(dto.getImageUrl()).description(dto.getDescription())
                .build();
    }
}
