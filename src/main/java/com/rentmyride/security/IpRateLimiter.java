package com.rentmyride.security;

import com.rentmyride.custom_exceptions.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fixed-window IP-based rate limiter.
 *
 * Before this, OTP send endpoints only tracked attempts PER IDENTIFIER (issue #9) — so an
 * attacker sitting behind one IP could still hammer the email/SMS provider by cycling through
 * many different phone numbers/emails, running up cost and spamming real users. This limits
 * requests per *source IP*, regardless of which identifier they're targeting, as a second,
 * independent layer on top of the existing per-identifier counter.
 *
 * In-memory, so (like TokenBlacklistService) it resets on restart and isn't shared across
 * multiple backend instances — fine for a single instance, would need a shared store (Redis)
 * behind a load balancer.
 */
@Component
public class IpRateLimiter {

    // Bug fix / dev convenience: these were hardcoded, so a developer repeatedly testing the OTP
    // flow from one machine (send, fail, resend, fail, resend...) trips this after the 5th
    // request in 15 minutes and gets a generic "Too many requests" error that looks exactly like
    // a broken OTP flow — it's not broken, it's this limiter. Now configurable per environment
    // instead of forcing a code change (or a 15-minute wait) to test locally.
    @org.springframework.beans.factory.annotation.Value("${otp.rate-limit.max-requests:5}")
    private int maxRequests;

    @org.springframework.beans.factory.annotation.Value("${otp.rate-limit.window-minutes:15}")
    private int windowMinutes;

    // key = bucketName + ":" + ip  ->  list of request timestamps within the current window
    private final Map<String, List<Long>> requestLog = new ConcurrentHashMap<>();

    /** @param bucket a name for the action being limited, e.g. "otp-send", so different actions don't share one counter */
    public void checkAllowed(HttpServletRequest request, String bucket) {
        String ip = extractClientIp(request);
        String key = bucket + ":" + ip;
        long now = Instant.now().toEpochMilli();

        long windowMillis = windowMinutes * 60_000L;
        List<Long> timestamps = requestLog.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        timestamps.removeIf(ts -> now - ts > windowMillis);

        if (timestamps.size() >= maxRequests) {
            throw new TooManyRequestsException(
                    "Too many requests from this network. Please try again in a few minutes.");
        }
        timestamps.add(now);
    }

    private String extractClientIp(HttpServletRequest request) {
        // Respect a reverse proxy's forwarded header if present (common in production deploys),
        // falling back to the direct connection's remote address otherwise.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
