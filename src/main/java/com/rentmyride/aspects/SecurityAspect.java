package com.rentmyride.aspects;

import com.rentmyride.custom_exceptions.UnauthorizedAccessException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class SecurityAspect {

    // ── Sensitive Operations ───────────────────────────────
    @Pointcut("execution(* com.rentmyride.service.impl.*.delete*(..)) || " +
              "execution(* com.rentmyride.service.impl.*.updateAccountStatus*(..))")
    public void sensitiveOperations() {}

    @Before("sensitiveOperations()")
    public void logSecurityContext(JoinPoint joinPoint) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !auth.getName().equals("anonymousUser")) {
            log.warn("[DDT-SECURITY] Sensitive op: {}.{}() by: {} roles: {}",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    auth.getName(), auth.getAuthorities());
        } else {
            throw new UnauthorizedAccessException("Authentication required.");
        }
    }

    // ── Audit all service operations ───────────────────────
    @Pointcut("execution(* com.rentmyride.service.impl.*.*(..))")
    public void allServiceOperations() {}

    @Before("allServiceOperations()")
    public void auditServiceOperation(JoinPoint joinPoint) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String user = (auth != null && !auth.getName().equals("anonymousUser"))
                ? auth.getName() : "ANONYMOUS";
        log.info("[DDT-AUDIT] Service: {}.{}() by: {}",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(), user);
    }
}
