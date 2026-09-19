package com.rentmyride.service;

import com.rentmyride.dtos.SelfDriveConfigDTO;

import java.util.List;

public interface SelfDriveConfigService {
    SelfDriveConfigDTO create(SelfDriveConfigDTO dto);
    SelfDriveConfigDTO update(Long configId, SelfDriveConfigDTO dto);
    void delete(Long configId);
    List<SelfDriveConfigDTO> getAll();
    SelfDriveConfigDTO getByCategory(String carCategory);
}
