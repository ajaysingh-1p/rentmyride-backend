package com.rentmyride.service;

import com.rentmyride.dtos.BusinessSettingsDTO;

public interface BusinessSettingsService {
    BusinessSettingsDTO getSettings();
    BusinessSettingsDTO updateSettings(BusinessSettingsDTO dto);
}
