package com.ia.project.dynamicstudyplanner.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration class for the application.
 * <p>
 * This class configures Spring Security to allow public access to all API endpoints
 * under "/api/v1/**" without requiring authentication. It also disables CSRF protection,
 * which is a common practice for stateless REST APIs.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Defines the security filter chain for the application.
     *
     * @param http The HttpSecurity object to configure.
     * @return The configured SecurityFilterChain.
     * @throws Exception If an error occurs during configuration.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Disable CSRF (Cross-Site Request Forgery) protection.
                // This is common for stateless REST APIs that are not called from a browser form.
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Configure authorization rules for HTTP requests.
                .authorizeHttpRequests(auth -> auth
                        // Allow OpenAPI and Swagger UI to be accessed without authentication.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Allow all requests to endpoints under "/api/v1/**" to be accessed without authentication.
                        .requestMatchers("/api/v1/**").permitAll()
                        // You can add other rules here, for example:
                        // .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // By default, deny any other request that is not explicitly matched.
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
