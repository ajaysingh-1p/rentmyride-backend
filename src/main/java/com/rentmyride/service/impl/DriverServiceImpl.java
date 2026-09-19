package com.rentmyride.service.impl;

import com.rentmyride.custom_exceptions.DriverNotFoundException;
import com.rentmyride.custom_exceptions.DuplicateRegistrationException;
import com.rentmyride.custom_exceptions.InvalidCredentialsException;
import com.rentmyride.custom_exceptions.InvalidOtpException;
import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.dtos.DriverDTO;
import com.rentmyride.entities.Driver;
import com.rentmyride.entities.OtpVerification;
import com.rentmyride.entities.Rental;
import com.rentmyride.repository.DriverRepository;
import com.rentmyride.repository.FeedbackRepository;
import com.rentmyride.repository.RentalRepository;
import com.rentmyride.security.JwtUtil;
import com.rentmyride.service.DriverService;
import com.rentmyride.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DriverServiceImpl implements DriverService {

    private final DriverRepository driverRepository;
    private final RentalRepository rentalRepository;
    private final FeedbackRepository feedbackRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OtpService otpService;
    private final com.rentmyride.service.NotificationService notificationService;
    private final com.rentmyride.repository.CustomerRepository customerRepository;
    private final com.rentmyride.repository.AdminRepository adminRepository;

    // Same normalization CustomerServiceImpl uses (trim + lowercase email) — applied here too so
    // a driver typing their email with a stray capital letter or leading space (mobile
    // auto-capitalize) doesn't get a false "no account found" from OTP-login/forgot-password.
    private String normalizeIdentifier(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.contains("@") ? com.rentmyride.util.NormalizationUtil.normalizeEmail(trimmed) : trimmed;
    }

    // ── OTP Login (mirrors CustomerServiceImpl) ───────────────
    @Override
    @Transactional
    public void sendLoginOtp(String identifier) {
        // javac fix: same "must be final or effectively final" issue as CustomerServiceImpl —
        // reassigning the parameter and then capturing it in the lambda below doesn't compile.
        final String normalizedIdentifier = normalizeIdentifier(identifier);
        Driver driver = driverRepository.findByEmailOrMobile(normalizedIdentifier)
                .orElseThrow(() -> new DriverNotFoundException("No driver account found with: " + normalizedIdentifier));
        if (driver.getStatus() != Driver.Status.ACTIVE)
            throw new InvalidOtpException("Your account is inactive. Contact admin.");

        // Feature: SMS OTP — same pattern as CustomerServiceImpl.sendLoginOtp(). Stored under the
        // driver's email (what verifyLoginOtp() below always checks); only the delivery channel
        // changes based on whether the driver typed their email or mobile number to log in.
        boolean requestedViaMobile = !normalizedIdentifier.contains("@");
        if (requestedViaMobile && driver.getMobileNumber() != null && !driver.getMobileNumber().isBlank()) {
            String otp = otpService.generateOtp(driver.getEmail(), OtpVerification.OtpPurpose.LOGIN);
            notificationService.sendSms(driver.getMobileNumber(),
                    "Your RentMyRide login OTP is " + otp + ". Don't share this code with anyone.");
        } else {
            otpService.generateAndSendOtp(driver.getEmail(), OtpVerification.OtpPurpose.LOGIN);
        }
    }

    @Override
    @Transactional
    public AuthResponseDTO verifyLoginOtp(String identifier, String otp) {
        // javac fix: same "must be final or effectively final" issue as CustomerServiceImpl —
        // reassigning the parameter and then capturing it in the lambda below doesn't compile.
        final String normalizedIdentifier = normalizeIdentifier(identifier);
        Driver driver = driverRepository.findByEmailOrMobile(normalizedIdentifier)
                .orElseThrow(() -> new DriverNotFoundException("No driver account found with: " + normalizedIdentifier));
        otpService.verifyOtp(driver.getEmail(), otp, OtpVerification.OtpPurpose.LOGIN);
        String token = jwtUtil.generateToken(driver.getEmail(), "DRIVER", driver.getDriverId());
        return AuthResponseDTO.success(token, "DRIVER", driver.getDriverId(),
                driver.getFirstName() + " " + driver.getLastName(), driver.getEmail());
    }

    // ── Forgot Password (OTP based, mirrors CustomerServiceImpl) ──
    @Override
    @Transactional
    public void sendForgotPasswordOtp(String identifier) {
        // javac fix: same "must be final or effectively final" issue as CustomerServiceImpl —
        // reassigning the parameter and then capturing it in the lambda below doesn't compile.
        final String normalizedIdentifier = normalizeIdentifier(identifier);
        Driver driver = driverRepository.findByEmailOrMobile(normalizedIdentifier)
                .orElseThrow(() -> new DriverNotFoundException("No driver account found with: " + normalizedIdentifier));
        otpService.generateAndSendOtp(driver.getEmail(), OtpVerification.OtpPurpose.FORGOT_PASSWORD);
    }

    @Override
    @Transactional
    public void verifyForgotPasswordOtp(String identifier, String otp) {
        // javac fix: same "must be final or effectively final" issue as CustomerServiceImpl —
        // reassigning the parameter and then capturing it in the lambda below doesn't compile.
        final String normalizedIdentifier = normalizeIdentifier(identifier);
        Driver driver = driverRepository.findByEmailOrMobile(normalizedIdentifier)
                .orElseThrow(() -> new DriverNotFoundException("No driver account found with: " + normalizedIdentifier));
        otpService.verifyOtp(driver.getEmail(), otp, OtpVerification.OtpPurpose.FORGOT_PASSWORD);
    }

    @Override
    @Transactional
    public void resetPassword(String identifier, String otp, String newPassword) {
        // javac fix: same "must be final or effectively final" issue as CustomerServiceImpl —
        // reassigning the parameter and then capturing it in the lambda below doesn't compile.
        final String normalizedIdentifier = normalizeIdentifier(identifier);
        Driver driver = driverRepository.findByEmailOrMobile(normalizedIdentifier)
                .orElseThrow(() -> new DriverNotFoundException("No driver account found with: " + normalizedIdentifier));
        if (!otpService.isRecentlyVerified(driver.getEmail(), OtpVerification.OtpPurpose.FORGOT_PASSWORD)) {
            throw new InvalidOtpException("OTP not verified. Please verify the OTP before resetting your password.");
        }
        if (newPassword == null || newPassword.trim().length() < 6) {
            throw new InvalidOtpException("Password must be at least 6 characters long.");
        }
        driver.setPassword(passwordEncoder.encode(newPassword));
        driverRepository.save(driver);
    }

    @Override
    @Transactional
    public DriverDTO addDriver(DriverDTO.RegisterRequest req) {
        // Issue #33 fix — normalize before any uniqueness check or save (see CustomerServiceImpl
        // for the same pattern / rationale).
        req.setEmail(com.rentmyride.util.NormalizationUtil.normalizeEmail(req.getEmail()));
        req.setMobileNumber(com.rentmyride.util.NormalizationUtil.normalizeMobile(req.getMobileNumber()));

        // Cross-table uniqueness — see issue #3.
        if (driverRepository.existsByEmail(req.getEmail())
                || customerRepository.existsByEmail(req.getEmail())
                || adminRepository.existsByEmail(req.getEmail()))
            throw new DuplicateRegistrationException("Email already in use: " + req.getEmail());
        if (driverRepository.existsByMobileNumber(req.getMobileNumber()))
            throw new DuplicateRegistrationException("Mobile number already in use.");

        Driver driver = Driver.builder()
                .firstName(req.getFirstName()).lastName(req.getLastName())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .mobileNumber(req.getMobileNumber())
                .licenseNumber(req.getLicenseNumber())
                .status(Driver.Status.ACTIVE)
                .build();
        return mapToDTO(driverRepository.save(driver));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponseDTO loginDriver(DriverDTO.LoginRequest req) {
        // Generic message either way — see issue #4 (login enumeration).
        // Issue #33 fix — normalize before lookup, same as registration.
        String email = com.rentmyride.util.NormalizationUtil.normalizeEmail(req.getEmail());
        Driver driver = driverRepository.findByEmail(email).orElse(null);
        if (driver == null || !passwordEncoder.matches(req.getPassword(), driver.getPassword()))
            return AuthResponseDTO.failure("Invalid email or password.");
        if (driver.getStatus() != Driver.Status.ACTIVE)
            return AuthResponseDTO.failure("Account inactive. Contact admin.");

        String token = jwtUtil.generateToken(driver.getEmail(), "DRIVER", driver.getDriverId());
        return AuthResponseDTO.success(token, "DRIVER", driver.getDriverId(),
                driver.getFirstName() + " " + driver.getLastName(), driver.getEmail());
    }

    @Override
    @Transactional(readOnly = true)
    public DriverDTO getDriverById(Long id) {
        // IDOR fix: role-only check let any logged-in driver view any other driver's profile.
        com.rentmyride.security.SecurityUtils.assertOwnsAsDriver(id);
        return mapToDTO(driverRepository.findById(id).orElseThrow(() -> new DriverNotFoundException(id)));
    }

    @Override
    @Transactional
    public DriverDTO updateDriver(Long id, DriverDTO dto) {
        // IDOR fix: without this, any logged-in driver could edit another driver's profile
        // (name, mobile, license number/image) by PUTting to that driver's id.
        com.rentmyride.security.SecurityUtils.assertOwnsAsDriver(id);
        Driver d = driverRepository.findById(id).orElseThrow(() -> new DriverNotFoundException(id));
        d.setFirstName(dto.getFirstName());
        d.setLastName(dto.getLastName());
        d.setMobileNumber(dto.getMobileNumber());
        d.setLicenseNumber(dto.getLicenseNumber());
        if (dto.getLicenseImageUrl() != null) d.setLicenseImageUrl(dto.getLicenseImageUrl());

        // Bug fix: dto.getEmail() was never applied here, so an Admin (or the driver) editing
        // the profile and changing the email had that change silently dropped on every save —
        // the form appeared to succeed but the stored email never actually changed. Same
        // normalize-then-uniqueness-check pattern as addDriver(), but excluding this driver's
        // own current row from the uniqueness check (otherwise re-saving without changing the
        // email would incorrectly reject itself as "already in use").
        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            String newEmail = com.rentmyride.util.NormalizationUtil.normalizeEmail(dto.getEmail());
            if (!newEmail.equalsIgnoreCase(d.getEmail())) {
                if (driverRepository.existsByEmailAndDriverIdNot(newEmail, id)
                        || customerRepository.existsByEmail(newEmail)
                        || adminRepository.existsByEmail(newEmail))
                    throw new DuplicateRegistrationException("Email already in use: " + newEmail);
                d.setEmail(newEmail);
            }
        }
        return mapToDTO(driverRepository.save(d));
    }

    @Override
    @Transactional
    public void deleteDriver(Long id) {
        Driver d = driverRepository.findById(id).orElseThrow(() -> new DriverNotFoundException(id));
        d.setStatus(Driver.Status.INACTIVE);
        driverRepository.save(d);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DriverDTO> getAllDrivers() {
        return driverRepository.findAll().stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    // Issue #25 fix — paginated version for the admin drivers table.
    @Override
    @Transactional(readOnly = true)
    public com.rentmyride.dtos.PageResponse<DriverDTO> getAllDriversPaged(int page, int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("createdAt").descending());
        return com.rentmyride.dtos.PageResponse.from(driverRepository.findAll(pageable).map(this::mapToDTO));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DriverDTO> getDriversByStatus(Driver.Status status) {
        return driverRepository.findByStatus(status).stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DriverDTO updateStatus(Long id, Driver.Status status) {
        Driver d = driverRepository.findById(id).orElseThrow(() -> new DriverNotFoundException(id));
        d.setStatus(status);
        return mapToDTO(driverRepository.save(d));
    }

    @Override
    @Transactional
    public void changePassword(Long id, DriverDTO.ChangePasswordRequest req) {
        com.rentmyride.security.SecurityUtils.assertOwnsAsDriver(id);
        Driver d = driverRepository.findById(id).orElseThrow(() -> new DriverNotFoundException(id));
        if (!passwordEncoder.matches(req.getCurrentPassword(), d.getPassword()))
            throw new InvalidCredentialsException("Current password is incorrect.");
        d.setPassword(passwordEncoder.encode(req.getNewPassword()));
        driverRepository.save(d);
    }

    @Override
    @Transactional(readOnly = true)
    public DriverDTO.StatsResponse getStats(Long id) {
        com.rentmyride.security.SecurityUtils.assertOwnsAsDriver(id);
        driverRepository.findById(id).orElseThrow(() -> new DriverNotFoundException(id));

        List<Rental> all = rentalRepository.findByDriver_DriverId(id);
        List<Rental> completed = all.stream()
                .filter(r -> r.getRentalStatus() == Rental.RentalStatus.COMPLETED)
                .collect(Collectors.toList());

        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).toLocalDate().atStartOfDay();
        List<Rental> completedThisMonth = completed.stream()
                .filter(r -> r.getActualReturnDatetime() != null && r.getActualReturnDatetime().isAfter(monthStart))
                .collect(Collectors.toList());

        double totalKm = completed.stream().mapToDouble(r -> r.getTotalKmDriven() != null ? r.getTotalKmDriven() : 0.0).sum();
        double kmThisMonth = completedThisMonth.stream().mapToDouble(r -> r.getTotalKmDriven() != null ? r.getTotalKmDriven() : 0.0).sum();

        Object[] ratingRow = feedbackRepository.findDriverRatingSummary(id).stream().findFirst().orElse(new Object[]{null, 0L});
        Double avgRating = ratingRow[0] != null ? Math.round(((Number) ratingRow[0]).doubleValue() * 10) / 10.0 : null;
        long ratingCount = ratingRow[1] != null ? ((Number) ratingRow[1]).longValue() : 0L;

        return DriverDTO.StatsResponse.builder()
                .completedTrips(completed.size())
                .tripsThisMonth(completedThisMonth.size())
                .totalKmDriven(totalKm)
                .kmDrivenThisMonth(kmThisMonth)
                .averageRating(avgRating)
                .totalRatings(ratingCount)
                .build();
    }

    private DriverDTO mapToDTO(Driver d) {
        return DriverDTO.builder()
                .driverId(d.getDriverId())
                .firstName(d.getFirstName()).lastName(d.getLastName())
                .email(d.getEmail()).mobileNumber(d.getMobileNumber())
                .licenseNumber(d.getLicenseNumber()).licenseImageUrl(d.getLicenseImageUrl())
                .status(d.getStatus()).dateOfJoining(d.getDateOfJoining())
                .createdAt(d.getCreatedAt())
                .build();
    }
}
