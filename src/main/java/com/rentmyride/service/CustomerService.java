package com.rentmyride.service;

import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.dtos.CustomerDTO;
import com.rentmyride.entities.Customer;
import java.util.List;

public interface CustomerService {
    CustomerDTO registerCustomer(CustomerDTO.RegisterRequest request);
    AuthResponseDTO loginCustomer(CustomerDTO.LoginRequest request);
    CustomerDTO getCustomerById(Long customerId);
    CustomerDTO updateCustomer(Long customerId, CustomerDTO customerDTO);
    void deleteCustomer(Long customerId);
    List<CustomerDTO> getAllCustomers();
    com.rentmyride.dtos.PageResponse<CustomerDTO> getAllCustomersPaged(int page, int size);
    List<CustomerDTO> searchCustomers(String keyword);
    CustomerDTO updateAccountStatus(Long customerId, Customer.AccountStatus status);
    CustomerDTO getCustomerProfile(String email);
    long getTotalCustomerCount();

    // Adjusts the customer's trust score by delta (positive or negative), clamped 0-100
    void adjustTrustScore(Long customerId, int delta);
    void changePassword(Long customerId, CustomerDTO.ChangePasswordRequest request);

    // ── Referral Program ──
    CustomerDTO.ReferralInfo getReferralInfo(Long customerId);
    /** Deducts up to `amount` from wallet balance (never below 0); returns the amount actually deducted. */
    double deductWalletBalance(Long customerId, double amount);

    // ── OTP Login ────────────────────────────────────────
    void sendLoginOtp(String identifier);
    AuthResponseDTO verifyLoginOtp(String identifier, String otp);

    // ── Forgot Password (OTP based) ───────────────────────
    void sendForgotPasswordOtp(String identifier);
    void verifyForgotPasswordOtp(String identifier, String otp);
    void resetPassword(String identifier, String otp, String newPassword);
}
