package com.rentmyride.controller;

import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getForCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Notifications.",
                notificationService.getNotificationsForCustomer(customerId)));
    }

    @GetMapping("/customer/{customerId}/unread-count")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getUnreadCount(@PathVariable Long customerId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Unread count.",
                notificationService.getUnreadCount(customerId)));
    }

    @PatchMapping("/{notificationId}/read")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER','DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> markAsRead(@PathVariable Long notificationId) {
        notificationService.markAsRead(notificationId);
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Marked as read.", null));
    }

    @PatchMapping("/customer/{customerId}/read-all")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> markAllAsRead(@PathVariable Long customerId) {
        notificationService.markAllAsRead(customerId);
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("All marked as read.", null));
    }

    // ── Driver notifications ──
    @GetMapping("/driver/{driverId}")
    @PreAuthorize("hasAnyRole('ADMIN','DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getForDriver(@PathVariable Long driverId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Notifications.",
                notificationService.getNotificationsForDriver(driverId)));
    }

    @GetMapping("/driver/{driverId}/unread-count")
    @PreAuthorize("hasAnyRole('ADMIN','DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getUnreadCountForDriver(@PathVariable Long driverId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Unread count.",
                notificationService.getUnreadCountForDriver(driverId)));
    }

    @PatchMapping("/driver/{driverId}/read-all")
    @PreAuthorize("hasAnyRole('ADMIN','DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> markAllAsReadForDriver(@PathVariable Long driverId) {
        notificationService.markAllAsReadForDriver(driverId);
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("All marked as read.", null));
    }

    // ── Admin notifications (bell icon in the admin panel) ──
    // Broadcast alerts (new booking, payment received) — same list for every admin, so no
    // adminId in the path, unlike the per-user customer/driver endpoints above.
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getForAdmin() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Notifications.",
                notificationService.getNotificationsForAdmin()));
    }

    @GetMapping("/admin/unread-count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getUnreadCountForAdmin() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Unread count.",
                notificationService.getUnreadCountForAdmin()));
    }

    @PatchMapping("/admin/read-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> markAllAsReadForAdmin() {
        notificationService.markAllAsReadForAdmin();
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("All marked as read.", null));
    }
}
