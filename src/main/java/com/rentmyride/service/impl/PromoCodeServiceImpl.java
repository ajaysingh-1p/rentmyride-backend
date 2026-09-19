package com.rentmyride.service.impl;

import com.rentmyride.custom_exceptions.PromoCodeException;
import com.rentmyride.dtos.PromoCodeDTO;
import com.rentmyride.entities.PromoCode;
import com.rentmyride.repository.PromoCodeRepository;
import com.rentmyride.service.PromoCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromoCodeServiceImpl implements PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;

    @Override
    @Transactional
    public PromoCodeDTO create(PromoCodeDTO dto) {
        PromoCode promo = PromoCode.builder()
                .code(dto.getCode().trim().toUpperCase())
                .description(dto.getDescription())
                .discountType(dto.getDiscountType())
                .discountValue(dto.getDiscountValue())
                .maxDiscountAmount(dto.getMaxDiscountAmount())
                .minBookingAmount(dto.getMinBookingAmount())
                .validFrom(dto.getValidFrom())
                .validUntil(dto.getValidUntil())
                .usageLimit(dto.getUsageLimit())
                .active(dto.isActive())
                .build();
        return mapToDTO(promoCodeRepository.save(promo));
    }

    @Override
    @Transactional
    public PromoCodeDTO update(Long promoId, PromoCodeDTO dto) {
        PromoCode promo = promoCodeRepository.findById(promoId)
                .orElseThrow(() -> new RuntimeException("Promo code not found: " + promoId));
        promo.setDescription(dto.getDescription());
        promo.setDiscountType(dto.getDiscountType());
        promo.setDiscountValue(dto.getDiscountValue());
        promo.setMaxDiscountAmount(dto.getMaxDiscountAmount());
        promo.setMinBookingAmount(dto.getMinBookingAmount());
        promo.setValidFrom(dto.getValidFrom());
        promo.setValidUntil(dto.getValidUntil());
        promo.setUsageLimit(dto.getUsageLimit());
        promo.setActive(dto.isActive());
        return mapToDTO(promoCodeRepository.save(promo));
    }

    @Override
    @Transactional
    public void delete(Long promoId) {
        promoCodeRepository.deleteById(promoId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromoCodeDTO> getAll() {
        return promoCodeRepository.findAllByOrderByCreatedAtDesc().stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PromoCodeDTO.ValidateResponse validate(String code, Double bookingAmount) {
        double amount = bookingAmount == null ? 0 : bookingAmount;
        PromoCode promo = promoCodeRepository.findByCodeIgnoreCase(code == null ? "" : code.trim()).orElse(null);

        String error = checkEligibility(promo, amount);
        if (error != null) {
            return PromoCodeDTO.ValidateResponse.builder().valid(false).message(error).discountAmount(0.0).finalAmount(amount).build();
        }

        double discount = calculateDiscount(promo, amount);
        return PromoCodeDTO.ValidateResponse.builder()
                .valid(true).message("Promo code applied!")
                .discountAmount(discount).finalAmount(Math.max(0, amount - discount))
                .build();
    }

    @Override
    @Transactional
    public double applyAndConsume(String code, double bookingAmount) {
        if (code == null || code.isBlank()) return 0.0;
        // Issue #15 fix: lock the promo row before checking eligibility/incrementing usedCount,
        // so a code can't be redeemed more times than its usageLimit by concurrent requests
        // racing each other (see repository note on findByCodeIgnoreCaseForUpdate).
        PromoCode promo = promoCodeRepository.findByCodeIgnoreCaseForUpdate(code.trim()).orElse(null);

        // Issue #45 fix: surface the SPECIFIC reason instead of silently returning 0 discount —
        // see PromoCodeException. The caller explicitly provided a code, so they should either
        // get the discount or a clear rejection, not a full-price charge with no explanation.
        String ineligibleReason = checkEligibility(promo, bookingAmount);
        if (ineligibleReason != null) throw new PromoCodeException(ineligibleReason);

        double discount = calculateDiscount(promo, bookingAmount);
        promo.setUsedCount(promo.getUsedCount() + 1);
        promoCodeRepository.save(promo);
        return discount;
    }

    // Returns an error message if ineligible, or null if the code can be used
    private String checkEligibility(PromoCode promo, double bookingAmount) {
        if (promo == null) return "Invalid promo code.";
        if (!promo.isActive()) return "This promo code is no longer active.";
        LocalDate today = LocalDate.now();
        if (promo.getValidFrom() != null && today.isBefore(promo.getValidFrom())) return "This promo code is not active yet.";
        if (promo.getValidUntil() != null && today.isAfter(promo.getValidUntil())) return "This promo code has expired.";
        if (promo.getUsageLimit() != null && promo.getUsedCount() >= promo.getUsageLimit()) return "This promo code has reached its usage limit.";
        if (promo.getMinBookingAmount() != null && bookingAmount < promo.getMinBookingAmount())
            return "Minimum booking amount for this code is ₹" + promo.getMinBookingAmount().intValue() + ".";
        return null;
    }

    private double calculateDiscount(PromoCode promo, double bookingAmount) {
        double discount;
        if (promo.getDiscountType() == PromoCode.DiscountType.PERCENTAGE) {
            discount = bookingAmount * (promo.getDiscountValue() / 100.0);
            if (promo.getMaxDiscountAmount() != null) discount = Math.min(discount, promo.getMaxDiscountAmount());
        } else {
            discount = promo.getDiscountValue();
        }
        return Math.min(Math.round(discount * 100) / 100.0, bookingAmount);
    }

    private PromoCodeDTO mapToDTO(PromoCode p) {
        return PromoCodeDTO.builder()
                .promoId(p.getPromoId()).code(p.getCode()).description(p.getDescription())
                .discountType(p.getDiscountType()).discountValue(p.getDiscountValue())
                .maxDiscountAmount(p.getMaxDiscountAmount()).minBookingAmount(p.getMinBookingAmount())
                .validFrom(p.getValidFrom()).validUntil(p.getValidUntil())
                .usageLimit(p.getUsageLimit()).usedCount(p.getUsedCount())
                .active(p.isActive()).createdAt(p.getCreatedAt())
                .build();
    }
}
