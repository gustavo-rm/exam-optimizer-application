package com.ia.project.dynamicstudyplanner.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Provides a centralized Random instance.
 * Allows the system to use SecureRandom in production but easily swap
 * to a fixed-seed Random in tests to ensure deterministic GA execution.
 */
@Configuration
public class RandomConfig {

    @Bean
    public Random randomProvider() {
        return new SecureRandom();
    }
}
