package com.rentmyride;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableAspectJAutoProxy
@EnableScheduling
@EnableAsync
@EnableCaching
public class RentMyRideApplication {

    // Issue #39 fix: LocalDateTime.now() (used all over — cancellation-window checks, OTP expiry,
    // rental timestamps, etc.) implicitly uses the JVM's default timezone, which is whatever the
    // HOST machine happens to be set to. A server provisioned in UTC (common on many cloud hosts)
    // would then compute "now" several hours off from what customers/staff in India actually mean
    // by "now" — e.g. the 12-hour free-cancellation cutoff could accept/reject a cancellation at
    // the wrong wall-clock moment. Pinning the JVM default to IST at startup makes every existing
    // (and future) LocalDateTime.now() call consistently correct, in one place, instead of
    // threading an explicit ZoneId through every one of the 12+ call sites that use it today.
    @PostConstruct
    public void setDefaultTimezone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata")); // also set before Spring context starts
        SpringApplication.run(RentMyRideApplication.class, args);
        System.out.println("==============================================");
        System.out.println("  🚗  RentMyRide Backend Started!     ");
        System.out.println("  📍  API   : http://localhost:8080/api       ");
        System.out.println("  📄  Swagger: http://localhost:8080/swagger-ui.html");
        System.out.println("==============================================");
    }
}
