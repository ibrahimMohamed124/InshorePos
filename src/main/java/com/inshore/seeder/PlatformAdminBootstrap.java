package com.inshore.seeder;

import java.time.LocalDateTime;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.inshore.user.domain.User;
import com.inshore.user.domain.UserRole;
import com.inshore.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(1) // run before DataSeeder so the admin account always exists first
public class PlatformAdminBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${PLATFORM_ADMIN_EMAIL:admin@inshore.local}")
    private String adminEmail;

    @Value("${PLATFORM_ADMIN_PASSWORD:ChangeMe123!}")
    private String adminPassword;

    @Value("${PLATFORM_ADMIN_USERNAME:platform-admin}")
    private String adminUsername;

    @Value("${PLATFORM_ADMIN_PHONE:+200000000000}")
    private String adminPhone;

    @Override
    public void run(String @NonNull ... args) {
        if (userRepository.existsByRole(UserRole.ROLE_ADMIN)) {
            return; // already bootstrapped - never touch it again
        }

        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setPhone(adminPhone);
        admin.setRole(UserRole.ROLE_ADMIN);
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        userRepository.save(admin);

        log.warn("==============================================================");
        log.warn("Created the first platform-admin account (no ROLE_ADMIN user existed yet).");
        log.warn("  email:    {}", adminEmail);
        log.warn("  password: {}", adminPassword.equals("ChangeMe123!")
                ? "ChangeMe123! (default - please log in and change it now)"
                : "(set from PLATFORM_ADMIN_PASSWORD)");
        log.warn("Log in with these credentials, open the Platform admin screen (/admin),"
                + " and change the password from the profile page.");
        log.warn("==============================================================");
    }
}