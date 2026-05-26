package com.ia.project.dynamicstudyplanner.util;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Singleton providing a centralized Random instance.
 * Using a singleton rather than DI for these low-level GA operators avoids
 * massive refactoring of all existing operator constructors while still
 * providing a centralized point to lock the seed during tests.
 */
public class RandomProvider {

    private static Random instance = new SecureRandom();

    private RandomProvider() {
    }

    public static Random getInstance() {
        return instance;
    }

    /**
     * For testing purposes only. Allows fixing the seed for deterministic behavior.
     */
    public static void setInstance(Random random) {
        instance = random;
    }
}
