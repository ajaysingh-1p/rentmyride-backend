package com.rentmyride.repository;

import com.rentmyride.entities.BusinessSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessSettingsRepository extends JpaRepository<BusinessSettings, Long> {
}
