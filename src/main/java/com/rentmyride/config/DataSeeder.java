package com.rentmyride.config;

import com.rentmyride.entities.Admin;
import com.rentmyride.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the default admin row on first startup.
 * Login: admin@rentmyride.in / Admin@123
 * Safe to run on every startup: it only inserts when the table is empty.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final AdminRepository adminRepository;

    @Override
    public void run(String... args) {
        if (adminRepository.count() == 0) {
            Admin admin = Admin.builder()
                    .name("Admin")
                    .email("admin@rentmyride.in")
                    .password("$2b$12$HdgXvk6RAvLZdonhPAvTrOraDbDavtlKEI0yvf8KpdBqlKHRwFDYS") // Admin@123
                    .build();
            adminRepository.save(admin);
            log.info("[DDT] Default admin seeded: admin@rentmyride.in / Admin@123");
        }
    }
}
