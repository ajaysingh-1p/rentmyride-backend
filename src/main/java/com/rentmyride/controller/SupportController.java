package com.rentmyride.controller;

import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.dtos.BusinessSettingsDTO;
import com.rentmyride.entities.SupportQuery;
import com.rentmyride.repository.CustomerRepository;
import com.rentmyride.repository.DriverRepository;
import com.rentmyride.repository.SupportQueryRepository;
import com.rentmyride.service.BusinessSettingsService;
import com.rentmyride.service.NotificationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

// Help & Support for customers and drivers: a safe, read-only slice of the admin-only
// business settings (just the contact channels — not GST/pricing config), a "send us a
// message" form that both emails the support inbox AND saves the query so admin can see
// it in-app (Manage Support), and admin endpoints to review/resolve those queries.
@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportController {

    private final BusinessSettingsService businessSettingsService;
    private final NotificationService notificationService;
    private final CustomerRepository customerRepository;
    private final DriverRepository driverRepository;
    private final SupportQueryRepository supportQueryRepository;

    @GetMapping("/contact-info")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER','DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getContactInfo() {
        BusinessSettingsDTO settings = businessSettingsService.getSettings();
        Map<String, String> info = new HashMap<>();
        info.put("supportEmail", settings.getSupportEmail());
        info.put("supportPhone", settings.getSupportPhone());
        info.put("supportHours", settings.getSupportHours());
        info.put("address", settings.getAddress());
        info.put("homeBaseLocation", settings.getHomeBaseLocation());
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Contact info.", info));
    }

    @PostMapping("/contact")
    @PreAuthorize("hasAnyRole('CUSTOMER','DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> submitQuery(@RequestBody ContactRequest req) {
        BusinessSettingsDTO settings = businessSettingsService.getSettings();

        // IDOR/spoofing fix: userId and role used to be trusted straight from the request body,
        // so anyone logged in as CUSTOMER or DRIVER could file a support ticket (with a real
        // email sent to the support inbox) impersonating a different customer/driver by id.
        // Derive who's actually asking from the JWT instead of the client-supplied fields.
        var currentUser = com.rentmyride.security.SecurityUtils.currentUser();
        Long userId = currentUser.getUserId();
        String role = currentUser.isDriver() ? "DRIVER" : "CUSTOMER";

        String senderName;
        String senderContact;
        if ("DRIVER".equals(role)) {
            var driver = driverRepository.findById(userId).orElse(null);
            senderName = driver != null ? driver.getFirstName() + " " + driver.getLastName() : "Driver #" + userId;
            senderContact = driver != null ? driver.getEmail() + " / " + driver.getMobileNumber() : "unknown";
        } else {
            var customer = customerRepository.findById(userId).orElse(null);
            senderName = customer != null ? customer.getFirstName() + " " + customer.getLastName() : "Customer #" + userId;
            senderContact = customer != null ? customer.getEmail() + " / " + customer.getMobileNumber() : "unknown";
        }

        supportQueryRepository.save(SupportQuery.builder()
                .senderRole(role)
                .senderId(userId)
                .senderName(senderName)
                .senderContact(senderContact)
                .subject(req.getSubject())
                .message(req.getMessage())
                .build());

        notificationService.sendEmail(
                settings.getSupportEmail(),
                "[Support] " + req.getSubject() + " — from " + senderName,
                "From: " + senderName + " (" + role + ")\n" +
                        "Contact: " + senderContact + "\n\n" +
                        "Subject: " + req.getSubject() + "\n\n" +
                        req.getMessage()
        );

        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success(
                "Your message has been sent. We'll get back to you soon!", null));
    }

    // ── Admin: view & resolve support queries ──
    @GetMapping("/queries")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getAllQueries() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Support queries.",
                supportQueryRepository.findAllByOrderByCreatedAtDesc()));
    }

    @GetMapping("/queries/unresolved")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getUnresolvedQueries() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Unresolved queries.",
                supportQueryRepository.findByResolvedFalseOrderByCreatedAtDesc()));
    }

    @PatchMapping("/queries/{queryId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> resolveQuery(@PathVariable Long queryId) {
        SupportQuery query = supportQueryRepository.findById(queryId)
                .orElseThrow(() -> new RuntimeException("Query not found: " + queryId));
        query.setResolved(true);
        supportQueryRepository.save(query);
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Marked resolved.", null));
    }

    @Data
    public static class ContactRequest {
        private Long userId;
        private String role; // "CUSTOMER" or "DRIVER"
        private String subject;
        private String message;
    }
}

