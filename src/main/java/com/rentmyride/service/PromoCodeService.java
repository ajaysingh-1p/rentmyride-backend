package com.rentmyride.service;

import com.rentmyride.dtos.PromoCodeDTO;

import java.util.List;

public interface PromoCodeService {
    PromoCodeDTO create(PromoCodeDTO dto);
    PromoCodeDTO update(Long promoId, PromoCodeDTO dto);
    void delete(Long promoId);
    List<PromoCodeDTO> getAll();
    PromoCodeDTO.ValidateResponse validate(String code, Double bookingAmount);

    /**
     * Validates + increments usage count. Returns the discount amount to subtract from the
     * booking (0 if invalid). Used internally during reservation creation.
     */
    double applyAndConsume(String code, double bookingAmount);
}
