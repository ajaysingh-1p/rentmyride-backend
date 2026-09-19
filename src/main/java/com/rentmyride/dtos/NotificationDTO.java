package com.rentmyride.dtos;

import com.rentmyride.entities.Notification;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDTO {
    private Long notificationId;
    private String title;
    private String message;
    private Notification.Type type;
    private Long relatedReservationId;
    private boolean read;
    private LocalDateTime createdAt;
}
