package com.inshore.shared.security;

import java.time.Duration;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final RateLimiterFilter rateLimiterFilter;

    public SecurityConfig(RateLimiterFilter rateLimiterFilter) {
        this.rateLimiterFilter = rateLimiterFilter;
    }

    @SuppressWarnings("null")
@Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtValidator jwtValidator = new JwtValidator(jwtSecret);
        return http
                .sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.requestMatchers("/api/**").authenticated()
                        .requestMatchers("/api/super-admin/**")
                        .hasRole("ADMIN")
                        .anyRequest().permitAll())
                .addFilterBefore(jwtValidator, BasicAuthenticationFilter.class)
                // Runs right after JWT validation so an authenticated caller can be rate-limited
                // per user (falls back to per-IP for anonymous/public/auth-endpoint traffic).
                .addFilterAfter(rateLimiterFilter, JwtValidator.class)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> corsConfigurationSource()).build();
    }

    /**
     * RateLimiterFilter is a {@code @Component} (so Spring can inject its dependencies) that is
     * also manually wired into the security filter chain above. Without this, Spring Boot would
     * ALSO auto-register it as a generic servlet filter applied to every request via its own
     * FilterRegistrationBean machinery - meaning every request would be rate-limited twice, with
     * two Redis round-trips and (on a block) two conflicting attempts to write the response body.
     * Disabling the bean here keeps the filter chain's single, intentional invocation as the only one.
     */
    @Bean
    public FilterRegistrationBean<RateLimiterFilter> disableRateLimiterFilterAutoRegistration(
            RateLimiterFilter rateLimiterFilter) {
        FilterRegistrationBean<RateLimiterFilter> registration = new FilterRegistrationBean<>(rateLimiterFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     *
     */
    private void corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "http://localhost:8080"));
        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"));
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        configuration.setAllowedHeaders(Collections.singletonList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

    }

    @Bean 
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
