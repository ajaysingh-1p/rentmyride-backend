package com.rentmyride.repository;

import com.rentmyride.entities.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
    List<Wishlist> findByCustomer_CustomerIdOrderByCreatedAtDesc(Long customerId);
    Optional<Wishlist> findByCustomer_CustomerIdAndCar_CarId(Long customerId, Long carId);
    boolean existsByCustomer_CustomerIdAndCar_CarId(Long customerId, Long carId);
    void deleteByCustomer_CustomerIdAndCar_CarId(Long customerId, Long carId);
}
