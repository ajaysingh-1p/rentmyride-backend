package com.rentmyride.repository;

import com.rentmyride.entities.SavedAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedAddressRepository extends JpaRepository<SavedAddress, Long> {
    List<SavedAddress> findByCustomer_CustomerIdOrderByCreatedAtDesc(Long customerId);
}
