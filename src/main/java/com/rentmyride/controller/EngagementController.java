package com.rentmyride.controller;

import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.dtos.EngagementDTO;
import com.rentmyride.service.EngagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/engagement")
@RequiredArgsConstructor
public class EngagementController {

    private final EngagementService engagementService;

    @GetMapping("/customer/{customerId}/summary")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getSummary(@PathVariable Long customerId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Engagement summary.",
                engagementService.getSummary(customerId)));
    }

    @GetMapping("/referral-leaderboard")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getLeaderboard() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Referral leaderboard.",
                engagementService.getReferralLeaderboard()));
    }

    // Fired when a customer picks dates on a car's booking page — powers the
    // abandoned-booking reminder job. Cleared automatically once they actually book.
    @PostMapping("/booking-intent")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> recordIntent(@RequestBody EngagementDTO.BookingIntentRequest req) {
        engagementService.recordBookingIntent(req.getCustomerId(), req.getCarId());
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Recorded.", null));
    }
}
