package com.rentmyride.service;

import com.rentmyride.dtos.SavedAddressDTO;

import java.util.List;

public interface SavedAddressService {
    SavedAddressDTO addAddress(Long customerId, SavedAddressDTO.SaveRequest request);
    List<SavedAddressDTO> getAddressesForCustomer(Long customerId);
    void deleteAddress(Long customerId, Long savedAddressId);
}
