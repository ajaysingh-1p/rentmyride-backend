package com.rentmyride.repository;

import com.rentmyride.entities.Car;
import com.rentmyride.entities.SelfDriveConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SelfDriveConfigRepository extends JpaRepository<SelfDriveConfig, Long> {
    Optional<SelfDriveConfig> findByCarCategory(Car.CarCategory carCategory);
    boolean existsByCarCategory(Car.CarCategory carCategory);
    List<SelfDriveConfig> findAllByOrderByCarCategoryAsc();
}
