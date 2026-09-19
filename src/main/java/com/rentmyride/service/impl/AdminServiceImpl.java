package com.rentmyride.service.impl;

import com.rentmyride.custom_exceptions.AdminNotFoundException;
import com.rentmyride.custom_exceptions.DuplicateRegistrationException;
import com.rentmyride.custom_exceptions.InvalidCredentialsException;
import com.rentmyride.dtos.AdminDTO;
import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.entities.Admin;
import com.rentmyride.repository.AdminRepository;
import com.rentmyride.security.JwtUtil;
import com.rentmyride.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional(readOnly = true)
    public AuthResponseDTO loginAdmin(AdminDTO.LoginRequest req) {
        // "username" field carries the admin's email (matches existing frontend AdminLogin form)
        // Generic message either way — see issue #4 (login enumeration).
        // Issue #33 fix — normalize before lookup, same as registration.
        String email = com.rentmyride.util.NormalizationUtil.normalizeEmail(req.getUsername());
        Admin admin = adminRepository.findByEmail(email).orElse(null);
        if (admin == null || !passwordEncoder.matches(req.getPassword(), admin.getPassword()))
            return AuthResponseDTO.failure("Invalid email or password.");

        String token = jwtUtil.generateToken(admin.getEmail(), "ADMIN", admin.getAdminId());
        return AuthResponseDTO.success(token, "ADMIN", admin.getAdminId(), admin.getName(), admin.getEmail());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDTO getProfile(Long adminId) {
        return mapToDTO(adminRepository.findById(adminId)
                .orElseThrow(() -> new AdminNotFoundException(adminId)));
    }

    @Override
    @Transactional
    public AdminDTO updateProfile(Long adminId, AdminDTO.UpdateProfileRequest req) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new AdminNotFoundException(adminId));

        if (!admin.getEmail().equalsIgnoreCase(req.getEmail())
                && adminRepository.existsByEmail(req.getEmail()))
            throw new DuplicateRegistrationException("Email already in use: " + req.getEmail());

        admin.setName(req.getName());
        admin.setEmail(req.getEmail());
        return mapToDTO(adminRepository.save(admin));
    }

    @Override
    @Transactional
    public void changePassword(Long adminId, AdminDTO.ChangePasswordRequest req) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new AdminNotFoundException(adminId));

        if (!passwordEncoder.matches(req.getCurrentPassword(), admin.getPassword()))
            throw new InvalidCredentialsException("Current password is incorrect.");

        admin.setPassword(passwordEncoder.encode(req.getNewPassword()));
        adminRepository.save(admin);
    }

    private AdminDTO mapToDTO(Admin a) {
        return AdminDTO.builder()
                .adminId(a.getAdminId())
                .name(a.getName())
                .email(a.getEmail())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
