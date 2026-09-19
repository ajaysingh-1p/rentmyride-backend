package com.rentmyride.service;

import com.rentmyride.dtos.SelfDriveHandoverDTO;

import java.util.List;

/**
 * Self-drive trip lifecycle (Option A — OTP handover). Deliberately separate from
 * RentalService: the chauffeur flow there is driver-bound end to end
 * (assertOwnsAsDriver on both initiateRental and completeRental), and a self-drive
 * booking has no driver at all, so forcing both flows through the same methods would
 * mean gutting those ownership checks. This service owns the self-drive path only.
 */
public interface SelfDriveHandoverService {

    // ── Customer actions ──
    /** Customer taps "Start Trip" — returns a short-lived OTP to show the admin at the hub. */
    SelfDriveHandoverDTO.OtpResponse requestPickupOtp(Long reservationId);

    /** Customer taps "Return Car" — returns a fresh OTP for the drop-off handover. */
    SelfDriveHandoverDTO.OtpResponse requestReturnOtp(Long rentalId);

    /** The customer's currently-out self-drive car, or null if they have none. */
    SelfDriveHandoverDTO.ActiveTripResponse getMyActiveTrip(Long customerId);

    // ── Admin actions ──
    /** Self-drive bookings eligible for handover today (paid in full, no rental started yet). */
    List<SelfDriveHandoverDTO.PendingHandover> getPendingHandovers();

    /** Self-drive cars currently out with customers. */
    List<SelfDriveHandoverDTO.ActiveHandover> getActiveHandovers();

    /** Verifies the customer's pickup OTP and starts the rental (driver = null). */
    SelfDriveHandoverDTO.ActiveHandover confirmPickup(SelfDriveHandoverDTO.PickupRequest request);

    /** Verifies the customer's return OTP, reprices the trip and settles the deposit. */
    SelfDriveHandoverDTO.SettlementResponse confirmReturn(SelfDriveHandoverDTO.ReturnRequest request);
}
