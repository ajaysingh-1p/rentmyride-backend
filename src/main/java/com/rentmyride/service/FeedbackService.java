package com.rentmyride.service;

import com.rentmyride.dtos.FeedbackDTO;

import java.util.List;
import java.util.Map;

public interface FeedbackService {
    FeedbackDTO.Response submitFeedback(Long customerId, FeedbackDTO.SubmitRequest request);
    List<FeedbackDTO.Response> getAllFeedback();          // admin only
    List<FeedbackDTO.Response> getFeedbackForCustomer(Long customerId);
    boolean hasFeedbackForRental(Long rentalId);

    // Public — shown to customers browsing/viewing cars
    Map<Long, FeedbackDTO.CarRatingSummary> getRatingSummaryForAllCars();
    List<FeedbackDTO.Response> getFeedbackForCar(Long carId);
}
