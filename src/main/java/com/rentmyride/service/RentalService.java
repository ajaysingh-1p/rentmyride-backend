package com.rentmyride.service;

import com.rentmyride.dtos.RentalDTO;
import com.rentmyride.entities.Rental;
import java.util.List;

public interface RentalService {
    RentalDTO initiateRental(RentalDTO.PickupRequest request);
    RentalDTO completeRental(Long rentalId, RentalDTO.ReturnRequest request);
    // Admin-only decision on a Driver-reported damage charge that exceeded the auto-approval
    // threshold (issue #21) — approving applies it to the bill, rejecting zeroes it out.
    RentalDTO approveDamageCharge(Long rentalId);
    RentalDTO rejectDamageCharge(Long rentalId);
    List<RentalDTO> getPendingDamageApprovals();
    RentalDTO.ExtendResponse extendRental(Long rentalId, RentalDTO.ExtendRequest request);
    RentalDTO updateLocation(Long rentalId, Long driverId, RentalDTO.LocationUpdateRequest request);
    RentalDTO submitRebookPoll(Long rentalId, Long customerId, RentalDTO.RebookPollRequest request);
    RentalDTO getRentalById(Long rentalId);
    List<RentalDTO> getAllRentals();
    com.rentmyride.dtos.PageResponse<RentalDTO> getAllRentalsPaged(int page, int size);
    List<RentalDTO> getRentalsByCustomer(Long customerId);
    List<RentalDTO> getRentalsByDriver(Long driverId);
    List<RentalDTO> getRentalsByStatus(Rental.RentalStatus status);
    List<RentalDTO> getActiveRentals();
    Double getTotalRevenue();
    Double getMonthlyRevenue(int month, int year);
}
