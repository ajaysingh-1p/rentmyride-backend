package com.rentmyride.aspects;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    @Pointcut("execution(* com.rentmyride.service.impl.*.*(..))")
    public void serviceMethods() {}

    @Pointcut("execution(* com.rentmyride.controller.*.*(..))")
    public void controllerMethods() {}

    @Before("serviceMethods() || controllerMethods()")
    public void logBefore(JoinPoint joinPoint) {
        log.info("[DDT] >>> {}.{}() args: {}",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                Arrays.toString(joinPoint.getArgs()));
    }

    @AfterReturning(pointcut = "serviceMethods()", returning = "result")
    public void logAfterReturning(JoinPoint joinPoint, Object result) {
        log.info("[DDT] <<< {}.{}() returned: {}",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                result != null ? result.getClass().getSimpleName() : "void");
    }

    @AfterThrowing(pointcut = "serviceMethods() || controllerMethods()", throwing = "ex")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable ex) {
        log.error("[DDT] !!! {}.{}() threw: {} — {}",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                ex.getClass().getSimpleName(),
                ex.getMessage());
    }

    @Around("serviceMethods()")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long time = System.currentTimeMillis() - start;
        log.info("[DDT] ⏱ {}.{}() in {} ms",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(), time);
        return result;
    }
}
