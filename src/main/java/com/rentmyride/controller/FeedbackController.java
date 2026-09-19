package com.rentmyride.controller;

import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.dtos.FeedbackDTO;
import com.rentmyride.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    // Customer submits feedback for one of their own completed rentals
    @PostMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> submit(@PathVariable Long customerId,
            @Valid @RequestBody FeedbackDTO.SubmitRequest request) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Thank you for your feedback!",
                feedbackService.submitFeedback(customerId, request)));
    }

    // Customer can see their own past feedback (e.g. to know they've already rated a rental)
    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getForCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Your feedback.",
                feedbackService.getFeedbackForCustomer(customerId)));
    }

    @GetMapping("/rental/{rentalId}/exists")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> exists(@PathVariable Long rentalId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Checked.",
                feedbackService.hasFeedbackForRental(rentalId)));
    }

    // Public — shown on car listing/detail pages so customers can compare before booking
    @GetMapping("/ratings")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getAllRatings() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Car rating summary.",
                feedbackService.getRatingSummaryForAllCars()));
    }

    @GetMapping("/car/{carId}")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getFeedbackForCar(@PathVariable Long carId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Reviews for this car.",
                feedbackService.getFeedbackForCar(carId)));
    }

    // Admin-only: view all customer feedback
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getAll() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("All feedback.",
                feedbackService.getAllFeedback()));
    }
}
