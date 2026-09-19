package com.rentmyride.service.impl;

import com.rentmyride.custom_exceptions.CustomerAlreadyExistsException;
import com.rentmyride.custom_exceptions.CustomerNotFoundException;
import com.rentmyride.custom_exceptions.InvalidCredentialsException;
import com.rentmyride.custom_exceptions.InvalidOtpException;
import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.dtos.CustomerDTO;
import com.rentmyride.entities.Customer;
import com.rentmyride.entities.OtpVerification;
import com.rentmyride.repository.CustomerRepository;
import com.rentmyride.security.JwtUtil;
import com.rentmyride.service.CustomerService;
import com.rentmyride.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OtpService otpService;
    private final com.rentmyride.service.NotificationService notificationService;
    private final com.rentmyride.service.BusinessSettingsService businessSettingsService;
    private final com.rentmyride.repository.DriverRepository driverRepository;
    private final com.rentmyride.repository.AdminRepository adminRepository;

    @Override
    @Transactional
    public CustomerDTO registerCustomer(CustomerDTO.RegisterRequest request) {
        // Issue #33 fix: normalize BEFORE any uniqueness check or save, so "User@Example.com"
        // and "user@example.com " (trailing space) are always treated as the exact same account.
        request.setEmail(com.rentmyride.util.NormalizationUtil.normalizeEmail(request.getEmail()));
        request.setMobileNumber(com.rentmyride.util.NormalizationUtil.normalizeMobile(request.getMobileNumber()));
        if (request.getAlternateMobile() != null)
            request.setAlternateMobile(com.rentmyride.util.NormalizationUtil.normalizeMobile(request.getAlternateMobile()));

        // Email must be unique ACROSS Customer/Driver/Admin, not just within this table (issue #3) —
        // otherwise the same email can log in as two different roles depending on which table
        // gets checked first, which is a mess for auth and support alike.
        if (customerRepository.existsByEmail(request.getEmail())
                || driverRepository.existsByEmail(request.getEmail())
                || adminRepository.existsByEmail(request.getEmail()))
            throw new CustomerAlreadyExistsException("Email already registered: " + request.getEmail());
        if (customerRepository.existsByMobileNumber(request.getMobileNumber()))
            throw new CustomerAlreadyExistsException("Mobile number already registered.");
        if (request.getAadharNumber() != null && customerRepository.existsByAadharNumber(request.getAadharNumber()))
            throw new CustomerAlreadyExistsException("Aadhar number already registered.");

        // Driving license is optional — treat blank as not provided so the unique constraint isn't tripped by "".
        String dlNumber = (request.getDrivingLicenseNumber() == null || request.getDrivingLicenseNumber().trim().isEmpty())
                ? null : request.getDrivingLicenseNumber().trim();

        // Every customer gets their own referral code to share with friends
        String myReferralCode = generateUniqueReferralCode(request.getFirstName());

        // If they signed up using a friend's code, validate it and queue up the signup bonus
        Customer referrer = null;
        String referredByCode = null;
        if (request.getReferredByCode() != null && !request.getReferredByCode().isBlank()) {
            referrer = customerRepository.findByReferralCode(request.getReferredByCode().trim().toUpperCase()).orElse(null);
            if (referrer != null) referredByCode = referrer.getReferralCode();
        }

        double referralBonus = businessSettingsService.getSettings().getReferralBonus();

        Customer customer = Customer.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .mobileNumber(request.getMobileNumber())
                .alternateMobile(request.getAlternateMobile())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender() != null && !request.getGender().isBlank()
                        ? Customer.Gender.valueOf(request.getGender().toUpperCase()) : null)
                .address(request.getAddress())
                .city(request.getCity())
                .state(request.getState())
                .pincode(request.getPincode())
                .drivingLicenseNumber(dlNumber)
                .drivingLicenseExpiry(request.getDrivingLicenseExpiry())
                .aadharNumber(request.getAadharNumber())
                .drivingLicenseImageUrl(request.getDrivingLicenseImageUrl())
                .aadharImageUrl(request.getAadharImageUrl())
                .referralCode(myReferralCode)
                .referredByCode(referredByCode)
                .walletBalance(referredByCode != null ? referralBonus : 0.0) // signup bonus for using a code
                .build();

        Customer saved = customerRepository.save(customer);

        if (referrer != null) {
            referrer.setWalletBalance((referrer.getWalletBalance() == null ? 0.0 : referrer.getWalletBalance()) + referralBonus);
            customerRepository.save(referrer);
        }

        return mapToDTO(saved);
    }

    private String generateUniqueReferralCode(String firstName) {
        String base = (firstName == null || firstName.isBlank() ? "RIDE" : firstName.trim().toUpperCase())
                .replaceAll("[^A-Z]", "");
        if (base.length() > 6) base = base.substring(0, 6);
        String code;
        do {
            code = base + (100 + new java.util.Random().nextInt(900)); // e.g. RAHUL482
        } while (customerRepository.existsByReferralCode(code));
        return code;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponseDTO loginCustomer(CustomerDTO.LoginRequest request) {
        // Same generic message whether the account doesn't exist or the password is wrong,
        // so a caller can't use this endpoint to discover which emails/mobiles are registered.
        final String GENERIC_LOGIN_FAILURE = "Invalid email/mobile or password.";

        // Issue #33 fix — normalize the same way registration did (email lowercased, since
        // that's how it's stored), so casing/whitespace can't cause a legitimate login to fail
        // to match the stored record. Mobile numbers are digits-only, so trimming is enough.
        String raw = request.getUsername() == null ? "" : request.getUsername().trim();
        String identifier = raw.contains("@") ? com.rentmyride.util.NormalizationUtil.normalizeEmail(raw) : raw;
        Customer customer = customerRepository.findByEmailOrMobile(identifier).orElse(null);
        if (customer == null || !passwordEncoder.matches(request.getPassword(), customer.getPassword()))
            return AuthResponseDTO.failure(GENERIC_LOGIN_FAILURE);

        if (customer.getAccountStatus() == Customer.AccountStatus.BLOCKED)
            return AuthResponseDTO.failure("Your account has been blocked. Contact support.");

        String token = jwtUtil.generateToken(customer.getEmail(), "CUSTOMER", customer.getCustomerId());
        return AuthResponseDTO.success(token, "CUSTOMER", customer.getCustomerId(),
                customer.getFirstName() + " " + customer.getLastName(), customer.getEmail());
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerDTO getCustomerById(Long customerId) {
        // IDOR fix: role-only @PreAuthorize let any logged-in customer read anyone else's profile
        // (name, email, mobile, Aadhar/DL numbers) just by changing the customerId in the URL.
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(customerId);
        return mapToDTO(customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId)));
    }

    @Override
    @Transactional
    public CustomerDTO updateCustomer(Long customerId, CustomerDTO dto) {
        // IDOR fix: without this, any logged-in customer could overwrite another customer's
        // profile (including contact details) by PUTting to that customer's id.
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(customerId);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        customer.setFirstName(dto.getFirstName());
        customer.setLastName(dto.getLastName());
        customer.setMobileNumber(dto.getMobileNumber());
        customer.setAlternateMobile(dto.getAlternateMobile());
        customer.setDateOfBirth(dto.getDateOfBirth());
        customer.setGender(dto.getGender());
        customer.setAddress(dto.getAddress());
        customer.setCity(dto.getCity());
        customer.setState(dto.getState());
        customer.setPincode(dto.getPincode());
        customer.setProfileImageUrl(dto.getProfileImageUrl());
        if (dto.getDrivingLicenseImageUrl() != null) customer.setDrivingLicenseImageUrl(dto.getDrivingLicenseImageUrl());
        if (dto.getAadharImageUrl() != null) customer.setAadharImageUrl(dto.getAadharImageUrl());
        return mapToDTO(customerRepository.save(customer));
    }

    @Override
    @Transactional
    public void deleteCustomer(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        customer.setAccountStatus(Customer.AccountStatus.INACTIVE);
        customerRepository.save(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerDTO> getAllCustomers() {
        return customerRepository.findAll().stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    // Issue #25 fix — paginated version for the admin customers table.
    @Override
    @Transactional(readOnly = true)
    public com.rentmyride.dtos.PageResponse<CustomerDTO> getAllCustomersPaged(int page, int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("createdAt").descending());
        return com.rentmyride.dtos.PageResponse.from(customerRepository.findAll(pageable).map(this::mapToDTO));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerDTO> searchCustomers(String keyword) {
        return customerRepository.searchCustomers(keyword).stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CustomerDTO updateAccountStatus(Long customerId, Customer.AccountStatus status) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        customer.setAccountStatus(status);
        return mapToDTO(customerRepository.save(customer));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerDTO getCustomerProfile(String email) {
        return mapToDTO(customerRepository.findByEmail(email)
                .orElseThrow(() -> new CustomerNotFoundException("No customer found with email: " + email)));
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalCustomerCount() {
        return customerRepository.count();
    }

    @Override
    @Transactional
    public void adjustTrustScore(Long customerId, int delta) {
        customerRepository.findById(customerId).ifPresent(c -> {
            int updated = (c.getTrustScore() == null ? 0 : c.getTrustScore()) + delta;
            c.setTrustScore(Math.max(0, Math.min(100, updated)));
            customerRepository.save(c);
        });
    }

    @Override
    @Transactional
    public void changePassword(Long customerId, CustomerDTO.ChangePasswordRequest req) {
        // IDOR fix — current-password check below already blocks a stranger from succeeding, but
        // this keeps the rule consistent everywhere and stops account enumeration via error timing.
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(customerId);
        Customer c = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        if (!passwordEncoder.matches(req.getCurrentPassword(), c.getPassword()))
            throw new InvalidCredentialsException("Current password is incorrect.");
        c.setPassword(passwordEncoder.encode(req.getNewPassword()));
        customerRepository.save(c);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerDTO.ReferralInfo getReferralInfo(Long customerId) {
        // IDOR fix: referral code + wallet balance are personal — lock this to the owner or admin.
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(customerId);
        Customer c = customerRepository.findById(customerId).orElseThrow(() -> new CustomerNotFoundException(customerId));
        long referredCount = c.getReferralCode() == null ? 0 : customerRepository.countByReferredByCode(c.getReferralCode());
        return CustomerDTO.ReferralInfo.builder()
                .referralCode(c.getReferralCode())
                .walletBalance(c.getWalletBalance() == null ? 0.0 : c.getWalletBalance())
                .referredCount(referredCount)
                .bonusPerReferral(businessSettingsService.getSettings().getReferralBonus())
                .build();
    }

    @Override
    @Transactional
    public double deductWalletBalance(Long customerId, double amount) {
        // Issue #14 fix: previously a plain findById() read the balance, then saved a new value
        // computed from that read — two simultaneous requests (e.g. two tabs, or a retried
        // request) could both read the same starting balance and each deduct from it, letting a
        // customer spend more wallet credit than they actually have. findByIdForUpdate() takes a
        // row lock for the rest of this transaction, so the second caller waits and re-reads the
        // already-updated balance instead of racing against the first.
        Customer c = customerRepository.findByIdForUpdate(customerId).orElse(null);
        if (c == null || c.getWalletBalance() == null || c.getWalletBalance() <= 0 || amount <= 0) return 0.0;
        double deducted = Math.min(c.getWalletBalance(), amount);
        c.setWalletBalance(Math.round((c.getWalletBalance() - deducted) * 100) / 100.0);
        customerRepository.save(c);
        return deducted;
    }

    // Issue #33 fix, part 2: loginCustomer() already normalized the identifier (trim + lowercase
    // email) before lookup, but every OTP/forgot-password method below was calling
    // findByEmailOrMobile() on the RAW, un-normalized input — so a customer whose email is
    // stored lowercase (registration normalizes it) but who typed it with a capital letter or a
    // stray leading space (extremely common on a mobile keyboard's auto-capitalize) got
    // "No account found" from OTP login / forgot password, even though the exact same identifier
    // would log in fine via the password form. This is the same normalization loginCustomer()
    // already does — just applied consistently everywhere an identifier is looked up.
    private String normalizeIdentifier(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.contains("@") ? com.rentmyride.util.NormalizationUtil.normalizeEmail(trimmed) : trimmed;
    }

    // ── OTP Login ────────────────────────────────────────────
    @Override
    @Transactional
    public void sendLoginOtp(String identifier) {
        // javac fix: the parameter can't be reassigned and then captured by the lambda below
        // ("local variables referenced from a lambda expression must be final or effectively
        // final") — normalizeIdentifier()'s result goes into its own final variable instead.
        final String normalizedIdentifier = normalizeIdentifier(identifier);
        Customer customer = customerRepository.findByEmailOrMobile(normalizedIdentifier)
                .orElseThrow(() -> new CustomerNotFoundException("No account found with: " + normalizedIdentifier));

        if (customer.getAccountStatus() == Customer.AccountStatus.BLOCKED)
            throw new InvalidOtpException("Your account has been blocked. Contact support.");

        // Feature: SMS OTP. The code is always STORED under the customer's email (same key
        // verifyLoginOtp() below always checks), so verification never changes — only the
        // DELIVERY channel does, based on how the customer chose to log in. Typing a mobile
        // number sends the code by SMS; typing an email sends it by email, exactly as before.
        boolean requestedViaMobile = !normalizedIdentifier.contains("@");
        if (requestedViaMobile && customer.getMobileNumber() != null && !customer.getMobileNumber().isBlank()) {
            String otp = otpService.generateOtp(customer.getEmail(), OtpVerification.OtpPurpose.LOGIN);
            notificationService.sendSms(customer.getMobileNumber(),
                    "Your RentMyRide login OTP is " + otp + ". Don't share this code with anyone.");
        } else {
            otpService.generateAndSendOtp(customer.getEmail(), OtpVerification.OtpPurpose.LOGIN);
        }
    }

    @Override
    @Transactional
    public AuthResponseDTO verifyLoginOtp(String identifier, String otp) {
        // javac fix: the parameter can't be reassigned and then captured by the lambda below
        // ("local variables referenced from a lambda expression must be final or effectively
        // final") — normalizeIdentifier()'s result goes into its own final variable instead.
        final String normalizedIdentifier = normalizeIdentifier(identifier);
        Customer customer = customerRepository.findByEmailOrMobile(normalizedIdentifier)
                .orElseThrow(() -> new CustomerNotFoundException("No account found with: " + normalizedIdentifier));

        otpService.verifyOtp(customer.getEmail(), otp, OtpVerification.OtpPurpose.LOGIN);

        String token = jwtUtil.generateToken(customer.getEmail(), "CUSTOMER", customer.getCustomerId());
        return AuthResponseDTO.success(token, "CUSTOMER", customer.getCustomerId(),
                customer.getFirstName() + " " + customer.getLastName(), customer.getEmail());
    }

    // ── Forgot Password (OTP based) ───────────────────────────
    @Override
    @Transactional
    public void sendForgotPasswordOtp(String identifier) {
        // javac fix: the parameter can't be reassigned and then captured by the lambda below
        // ("local variables referenced from a lambda expression must be final or effectively
        // final") — normalizeIdentifier()'s result goes into its own final variable instead.
        final String normalizedIdentifier = normalizeIdentifier(identifier);
        Customer customer = customerRepository.findByEmailOrMobile(normalizedIdentifier)
                .orElseThrow(() -> new CustomerNotFoundException("No account found with: " + normalizedIdentifier));

        otpService.generateAndSendOtp(customer.getEmail(), OtpVerification.OtpPurpose.FORGOT_PASSWORD);
    }

    @Override
    @Transactional
    public void verifyForgotPasswordOtp(String identifier, String otp) {
        // javac fix: the parameter can't be reassigned and then captured by the lambda below
        // ("local variables referenced from a lambda expression must be final or effectively
        // final") — normalizeIdentifier()'s result goes into its own final variable instead.
        final String normalizedIdentifier = normalizeIdentifier(identifier);
        Customer customer = customerRepository.findByEmailOrMobile(normalizedIdentifier)
                .orElseThrow(() -> new CustomerNotFoundException("No account found with: " + normalizedIdentifier));

        otpService.verifyOtp(customer.getEmail(), otp, OtpVerification.OtpPurpose.FORGOT_PASSWORD);
    }

    @Override
    @Transactional
    public void resetPassword(String identifier, String otp, String newPassword) {
        // javac fix: the parameter can't be reassigned and then captured by the lambda below
        // ("local variables referenced from a lambda expression must be final or effectively
        // final") — normalizeIdentifier()'s result goes into its own final variable instead.
        final String normalizedIdentifier = normalizeIdentifier(identifier);
        Customer customer = customerRepository.findByEmailOrMobile(normalizedIdentifier)
                .orElseThrow(() -> new CustomerNotFoundException("No account found with: " + normalizedIdentifier));

        // verifyForgotPasswordOtp() must have been called first and marked the OTP as verified.
        if (!otpService.isRecentlyVerified(customer.getEmail(), OtpVerification.OtpPurpose.FORGOT_PASSWORD)) {
            throw new InvalidOtpException("OTP not verified. Please verify the OTP before resetting your password.");
        }

        if (newPassword == null || newPassword.trim().length() < 6) {
            throw new InvalidOtpException("Password must be at least 6 characters long.");
        }

        customer.setPassword(passwordEncoder.encode(newPassword));
        customerRepository.save(customer);
    }

    // ── Mapper ───────────────────────────────────────────────
    private CustomerDTO mapToDTO(Customer c) {
        return CustomerDTO.builder()
                .customerId(c.getCustomerId())
                .firstName(c.getFirstName())
                .lastName(c.getLastName())
                .email(c.getEmail())
                .mobileNumber(c.getMobileNumber())
                .alternateMobile(c.getAlternateMobile())
                .dateOfBirth(c.getDateOfBirth())
                .gender(c.getGender())
                .address(c.getAddress())
                .city(c.getCity())
                .state(c.getState())
                .pincode(c.getPincode())
                .drivingLicenseNumber(c.getDrivingLicenseNumber())
                .drivingLicenseExpiry(c.getDrivingLicenseExpiry())
                .aadharNumber(c.getAadharNumber())
                .profileImageUrl(c.getProfileImageUrl())
                .drivingLicenseImageUrl(c.getDrivingLicenseImageUrl())
                .aadharImageUrl(c.getAadharImageUrl())
                .trustScore(c.getTrustScore())
                .referralCode(c.getReferralCode())
                .walletBalance(c.getWalletBalance())
                .accountStatus(c.getAccountStatus())
                .role(c.getRole())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
