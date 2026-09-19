package com.rentmyride.service.impl;

import com.rentmyride.custom_exceptions.CustomerNotFoundException;
import com.rentmyride.custom_exceptions.SavedAddressNotFoundException;
import com.rentmyride.custom_exceptions.UnauthorizedAccessException;
import com.rentmyride.dtos.SavedAddressDTO;
import com.rentmyride.entities.Customer;
import com.rentmyride.entities.SavedAddress;
import com.rentmyride.repository.CustomerRepository;
import com.rentmyride.repository.SavedAddressRepository;
import com.rentmyride.service.SavedAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SavedAddressServiceImpl implements SavedAddressService {

    private final SavedAddressRepository savedAddressRepository;
    private final CustomerRepository customerRepository;

    @Override
    @Transactional
    public SavedAddressDTO addAddress(Long customerId, SavedAddressDTO.SaveRequest req) {
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(customerId);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        SavedAddress address = SavedAddress.builder()
                .customer(customer)
                .label(req.getLabel())
                .address(req.getAddress())
                .build();
        return mapToDTO(savedAddressRepository.save(address));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavedAddressDTO> getAddressesForCustomer(Long customerId) {
        // IDOR fix: home/office addresses are sensitive (real-world safety risk if leaked) —
        // without this, any customer could read anyone's saved addresses via the URL.
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(customerId);
        return savedAddressRepository.findByCustomer_CustomerIdOrderByCreatedAtDesc(customerId)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteAddress(Long customerId, Long savedAddressId) {
        // IDOR fix: the existing "does this address belong to customerId" check below isn't
        // enough on its own — it only protects the address if the attacker doesn't know the
        // victim's customerId. This confirms the CALLER really is that customerId.
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(customerId);
        SavedAddress address = savedAddressRepository.findById(savedAddressId)
                .orElseThrow(() -> new SavedAddressNotFoundException(savedAddressId));
        if (!address.getCustomer().getCustomerId().equals(customerId)) {
            throw new UnauthorizedAccessException("This address doesn't belong to you.");
        }
        savedAddressRepository.delete(address);
    }

    private SavedAddressDTO mapToDTO(SavedAddress a) {
        return SavedAddressDTO.builder()
                .savedAddressId(a.getSavedAddressId())
                .customerId(a.getCustomer().getCustomerId())
                .label(a.getLabel())
                .address(a.getAddress())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
