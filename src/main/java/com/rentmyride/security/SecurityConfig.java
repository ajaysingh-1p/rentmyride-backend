package com.rentmyride.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Browser pre-flight OPTIONS checks ko allow karna zaroori hai
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(
                    "/api/customers/register",
                    "/api/customers/login",
                    "/api/customers/login/otp/send",
                    "/api/customers/login/otp/verify",
                    "/api/auth/login",
                    "/api/auth/login/otp/send",
                    "/api/auth/login/otp/verify",
                    "/api/auth/forgot-password/send-otp",
                    "/api/auth/forgot-password/verify-otp",
                    "/api/auth/forgot-password/reset",
                    "/api/customers/forgot-password/send-otp",
                    "/api/customers/forgot-password/verify-otp",
                    "/api/customers/forgot-password/reset",
                    "/api/drivers/login",
                    "/api/admin/login",
                    "/api/cars",
                    "/api/cars/{carId}",
                    "/api/cars/available",
                    "/api/cars/search",
                    "/api/cars/available-between",
                    "/api/cars/category/**",
                    "/api/cars/page",
                    "/api/locations/**",
                    "/api/reservations/car/*/availability",
                    "/api/feedback/ratings",
                    "/api/feedback/car/*",
                    "/api/payments/webhook",
                    "/uploads/cars/**"
                ).permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                    .hasRole("ADMIN")
                .requestMatchers("/uploads/documents/**").authenticated()
                .requestMatchers("/uploads/handover/**").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}