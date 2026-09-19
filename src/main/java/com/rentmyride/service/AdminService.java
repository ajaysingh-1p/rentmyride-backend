package com.rentmyride.service;

import com.rentmyride.dtos.AdminDTO;
import com.rentmyride.dtos.AuthResponseDTO;

public interface AdminService {
    AuthResponseDTO loginAdmin(AdminDTO.LoginRequest request);
    AdminDTO getProfile(Long adminId);
    AdminDTO updateProfile(Long adminId, AdminDTO.UpdateProfileRequest request);
    void changePassword(Long adminId, AdminDTO.ChangePasswordRequest request);
}
