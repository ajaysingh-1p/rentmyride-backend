package com.rentmyride.service.impl;

import com.rentmyride.custom_exceptions.CarNotFoundException;
import com.rentmyride.custom_exceptions.CustomerNotFoundException;
import com.rentmyride.dtos.CarDTO;
import com.rentmyride.entities.Car;
import com.rentmyride.entities.Customer;
import com.rentmyride.entities.Wishlist;
import com.rentmyride.repository.CarRepository;
import com.rentmyride.repository.CustomerRepository;
import com.rentmyride.repository.WishlistRepository;
import com.rentmyride.service.CarService;
import com.rentmyride.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final CustomerRepository customerRepository;
    private final CarRepository carRepository;
    private final CarService carService;

    @Override
    @Transactional
    public void addToWishlist(Long customerId, Long carId) {
        // IDOR fix: role-only check let any logged-in customer modify anyone's wishlist by
        // swapping the customerId in the URL.
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(customerId);
        if (wishlistRepository.existsByCustomer_CustomerIdAndCar_CarId(customerId, carId)) return; // already saved

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new CarNotFoundException(carId));

        wishlistRepository.save(Wishlist.builder().customer(customer).car(car).build());
    }

    @Override
    @Transactional
    public void removeFromWishlist(Long customerId, Long carId) {
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(customerId);
        wishlistRepository.deleteByCustomer_CustomerIdAndCar_CarId(customerId, carId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isWishlisted(Long customerId, Long carId) {
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(customerId);
        return wishlistRepository.existsByCustomer_CustomerIdAndCar_CarId(customerId, carId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarDTO> getWishlistedCars(Long customerId) {
        // IDOR fix: without this, any customer could enumerate customerIds and read anyone's
        // saved/wishlisted cars.
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(customerId);
        return wishlistRepository.findByCustomer_CustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(w -> carService.getCarById(w.getCar().getCarId()))
                .collect(Collectors.toList());
    }
}
