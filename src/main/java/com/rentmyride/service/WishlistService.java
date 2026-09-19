package com.rentmyride.service;

import com.rentmyride.dtos.CarDTO;

import java.util.List;

public interface WishlistService {
    void addToWishlist(Long customerId, Long carId);
    void removeFromWishlist(Long customerId, Long carId);
    boolean isWishlisted(Long customerId, Long carId);
    List<CarDTO> getWishlistedCars(Long customerId);
}
