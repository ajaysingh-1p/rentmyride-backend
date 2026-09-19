package com.rentmyride.service.impl;

import com.rentmyride.custom_exceptions.SelfDriveConfigNotFoundException;
import com.rentmyride.dtos.SelfDriveConfigDTO;
import com.rentmyride.entities.Car;
import com.rentmyride.entities.SelfDriveConfig;
import com.rentmyride.repository.SelfDriveConfigRepository;
import com.rentmyride.service.SelfDriveConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SelfDriveConfigServiceImpl implements SelfDriveConfigService {

    private final SelfDriveConfigRepository selfDriveConfigRepository;

    @Override
    @Transactional
    public SelfDriveConfigDTO create(SelfDriveConfigDTO dto) {
        // One config per category — surface a clear error instead of letting the DB's
        // unique constraint throw a raw, unfriendly exception up to the admin panel.
        if (selfDriveConfigRepository.existsByCarCategory(dto.getCarCategory())) {
            throw new IllegalStateException(
                    "A self-drive config for " + dto.getCarCategory() + " already exists. Edit it instead of creating another.");
        }
        SelfDriveConfig config = SelfDriveConfig.builder()
                .carCategory(dto.getCarCategory())
                .securityDeposit(dto.getSecurityDeposit())
                .freeKmPerDay(dto.getFreeKmPerDay())
                .overageRatePerKm(dto.getOverageRatePerKm())
                .lateReturnPenaltyPerHour(dto.getLateReturnPenaltyPerHour())
                .refundWindowDays(dto.getRefundWindowDays())
                .enabled(dto.isEnabled())
                .build();
        return mapToDTO(selfDriveConfigRepository.save(config));
    }

    @Override
    @Transactional
    public SelfDriveConfigDTO update(Long configId, SelfDriveConfigDTO dto) {
        SelfDriveConfig config = selfDriveConfigRepository.findById(configId)
                .orElseThrow(() -> new SelfDriveConfigNotFoundException("Self-drive config not found: " + configId));
        // Category is intentionally not editable here — delete + recreate if a category
        // needs to be repointed, so there's never a moment with two configs racing for the
        // same category.
        config.setSecurityDeposit(dto.getSecurityDeposit());
        config.setFreeKmPerDay(dto.getFreeKmPerDay());
        config.setOverageRatePerKm(dto.getOverageRatePerKm());
        config.setLateReturnPenaltyPerHour(dto.getLateReturnPenaltyPerHour());
        config.setRefundWindowDays(dto.getRefundWindowDays());
        config.setEnabled(dto.isEnabled());
        return mapToDTO(selfDriveConfigRepository.save(config));
    }

    @Override
    @Transactional
    public void delete(Long configId) {
        if (!selfDriveConfigRepository.existsById(configId)) {
            throw new SelfDriveConfigNotFoundException("Self-drive config not found: " + configId);
        }
        selfDriveConfigRepository.deleteById(configId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SelfDriveConfigDTO> getAll() {
        return selfDriveConfigRepository.findAllByOrderByCarCategoryAsc()
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public SelfDriveConfigDTO getByCategory(String carCategory) {
        Car.CarCategory category;
        try {
            category = Car.CarCategory.valueOf(carCategory.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new SelfDriveConfigNotFoundException("Unknown car category: " + carCategory);
        }
        SelfDriveConfig config = selfDriveConfigRepository.findByCarCategory(category)
                .orElseThrow(() -> new SelfDriveConfigNotFoundException(
                        "Self-drive is not configured yet for " + category + ". Ask an admin to set it up."));
        return mapToDTO(config);
    }

    private SelfDriveConfigDTO mapToDTO(SelfDriveConfig c) {
        return SelfDriveConfigDTO.builder()
                .configId(c.getConfigId())
                .carCategory(c.getCarCategory())
                .securityDeposit(c.getSecurityDeposit())
                .freeKmPerDay(c.getFreeKmPerDay())
                .overageRatePerKm(c.getOverageRatePerKm())
                .lateReturnPenaltyPerHour(c.getLateReturnPenaltyPerHour())
                .refundWindowDays(c.getRefundWindowDays())
                .enabled(c.isEnabled())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
