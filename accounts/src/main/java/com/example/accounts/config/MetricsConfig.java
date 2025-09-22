package com.example.accounts.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Custom metrics configuration for Accounts service
 * Defines business-specific metrics for monitoring
 */
@Configuration
public class MetricsConfig {

    /**
     * Counter for successful account creations
     */
    @Bean
    public Counter accountCreationCounter(MeterRegistry meterRegistry) {
        return Counter.builder("accounts.created.total")
                .description("Total number of accounts successfully created")
                .tag("service", "accounts")
                .tag("type", "business")
                .register(meterRegistry);
    }

    /**
     * Counter for account creation failures
     */
    @Bean
    public Counter accountCreationFailureCounter(MeterRegistry meterRegistry) {
        return Counter.builder("accounts.creation.failures.total")
                .description("Total number of failed account creation attempts")
                .tag("service", "accounts")
                .tag("type", "business")
                .register(meterRegistry);
    }

    /**
     * Counter for account retrievals
     */
    @Bean
    public Counter accountRetrievalCounter(MeterRegistry meterRegistry) {
        return Counter.builder("accounts.retrieved.total")
                .description("Total number of account retrieval operations")
                .tag("service", "accounts")
                .tag("type", "business")
                .register(meterRegistry);
    }

    /**
     * Counter for validation errors
     */
    @Bean
    public Counter validationErrorCounter(MeterRegistry meterRegistry) {
        return Counter.builder("accounts.validation.errors.total")
                .description("Total number of validation errors")
                .tag("service", "accounts")
                .tag("type", "validation")
                .register(meterRegistry);
    }

    /**
     * Timer for database operations
     */
    @Bean
    public Timer databaseOperationTimer(MeterRegistry meterRegistry) {
        return Timer.builder("accounts.database.operation.duration")
                .description("Time taken for database operations")
                .tag("service", "accounts")
                .tag("type", "database")
                .register(meterRegistry);
    }

    /**
     * Timer for business logic execution
     */
    @Bean
    public Timer businessLogicTimer(MeterRegistry meterRegistry) {
        return Timer.builder("accounts.business.logic.duration")
                .description("Time taken for business logic execution")
                .tag("service", "accounts")
                .tag("type", "business")
                .register(meterRegistry);
    }
}