package com.rentmyride.service;

import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.dtos.DriverDTO;
import com.rentmyride.entities.Driver;

import java.util.List;

public interface DriverService {
    DriverDTO addDriver(DriverDTO.RegisterRequest request); // admin only
    AuthResponseDTO loginDriver(DriverDTO.LoginRequest request);
    DriverDTO getDriverById(Long driverId);
    DriverDTO updateDriver(Long driverId, DriverDTO dto);
    void deleteDriver(Long driverId);
    List<DriverDTO> getAllDrivers();
    com.rentmyride.dtos.PageResponse<DriverDTO> getAllDriversPaged(int page, int size);
    List<DriverDTO> getDriversByStatus(Driver.Status status);
    DriverDTO updateStatus(Long driverId, Driver.Status status);
    void changePassword(Long driverId, DriverDTO.ChangePasswordRequest request);
    DriverDTO.StatsResponse getStats(Long driverId);

    // ── OTP login + forgot password (mirrors CustomerService) ──
    void sendLoginOtp(String identifier);
    AuthResponseDTO verifyLoginOtp(String identifier, String otp);
    void sendForgotPasswordOtp(String identifier);
    void verifyForgotPasswordOtp(String identifier, String otp);
    void resetPassword(String identifier, String otp, String newPassword);
}
