package com.example.accounts.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Business Context Tracer for capturing domain-specific attributes in distributed traces
 */
@Component
public class BusinessContextTracer {
    
    private final Tracer tracer;
    
    public BusinessContextTracer() {
        this.tracer = GlobalOpenTelemetry.getTracer("banking-microservices", "1.0.0");
    }
    
    /**
     * Business Context Builder for fluent API
     */
    public static class BusinessContext {
        private final Map<String, Object> attributes = new HashMap<>();
        
        // Customer attributes
        public BusinessContext customerId(String customerId) {
            attributes.put("business.customer.id", customerId);
            return this;
        }
        
        public BusinessContext customerType(String customerType) {
            attributes.put("business.customer.type", customerType);
            return this;
        }
        
        public BusinessContext customerTier(String tier) {
            attributes.put("business.customer.tier", tier);
            return this;
        }
        
        // Account attributes
        public BusinessContext accountId(String accountId) {
            attributes.put("business.account.id", accountId);
            return this;
        }
        
        public BusinessContext accountType(String accountType) {
            attributes.put("business.account.type", accountType);
            return this;
        }
        
        public BusinessContext accountBalance(BigDecimal balance) {
            attributes.put("business.account.balance", balance.toString());
            return this;
        }
        
        // Transaction attributes
        public BusinessContext transactionId(String transactionId) {
            attributes.put("business.transaction.id", transactionId);
            return this;
        }
        
        public BusinessContext transactionAmount(BigDecimal amount) {
            attributes.put("business.transaction.amount", amount.toString());
            return this;
        }
        
        public BusinessContext transactionType(String type) {
            attributes.put("business.transaction.type", type);
            return this;
        }
        
        // Onboarding workflow attributes
        public BusinessContext onboardingStep(String step) {
            attributes.put("business.onboarding.step", step);
            return this;
        }
        
        public BusinessContext onboardingStatus(String status) {
            attributes.put("business.onboarding.status", status);
            return this;
        }
        
        public BusinessContext riskLevel(String riskLevel) {
            attributes.put("business.risk.level", riskLevel);
            return this;
        }
        
        // Card attributes
        public BusinessContext cardId(String cardId) {
            attributes.put("business.card.id", cardId);
            return this;
        }
        
        public BusinessContext cardType(String cardType) {
            attributes.put("business.card.type", cardType);
            return this;
        }
        
        // Loan attributes
        public BusinessContext loanEligible(boolean eligible) {
            attributes.put("business.loan.eligible", eligible);
            return this;
        }
        
        public BusinessContext loanAmount(BigDecimal amount) {
            attributes.put("business.loan.amount", amount.toString());
            return this;
        }
        
        // Service integration attributes
        public BusinessContext serviceCall(String service, String operation) {
            attributes.put("business.service.name", service);
            attributes.put("business.service.operation", operation);
            return this;
        }
        
        // Error attributes
        public BusinessContext errorCode(String errorCode) {
            attributes.put("business.error.code", errorCode);
            return this;
        }
        
        public BusinessContext errorCategory(String category) {
            attributes.put("business.error.category", category);
            return this;
        }
        
        // Performance attributes
        public BusinessContext processingTime(long milliseconds) {
            attributes.put("business.performance.processing_time_ms", milliseconds);
            return this;
        }
        
        public BusinessContext batchSize(int size) {
            attributes.put("business.batch.size", size);
            return this;
        }
        
        public Map<String, Object> getAttributes() {
            return new HashMap<>(attributes);
        }
        
        /**
         * Convert to OpenTelemetry Attributes
         */
        public Attributes toOtelAttributes() {
            var builder = Attributes.builder();
            attributes.forEach((key, value) -> {
                if (value instanceof String) {
                    builder.put(AttributeKey.stringKey(key), (String) value);
                } else if (value instanceof Long) {
                    builder.put(AttributeKey.longKey(key), (Long) value);
                } else if (value instanceof Double) {
                    builder.put(AttributeKey.doubleKey(key), (Double) value);
                } else if (value instanceof Boolean) {
                    builder.put(AttributeKey.booleanKey(key), (Boolean) value);
                } else {
                    builder.put(AttributeKey.stringKey(key), value.toString());
                }
            });
            return builder.build();
        }
    }
    
    /**
     * Create a new business context
     */
    public BusinessContext createContext() {
        return new BusinessContext();
    }
    
    /**
     * Start a new span with business context
     */
    public Span startBusinessSpan(String operationName, BusinessContext context) {
        Span span = tracer.spanBuilder(operationName)
                .setAllAttributes(context.toOtelAttributes())
                .startSpan();
        
        return span;
    }
    
    /**
     * Start a child span with business context
     */
    public Span startChildSpan(String operationName, BusinessContext context) {
        Span span = tracer.spanBuilder(operationName)
                .setParent(io.opentelemetry.context.Context.current())
                .setAllAttributes(context.toOtelAttributes())
                .startSpan();
        
        return span;
    }
    
    /**
     * Add business attributes to current span
     */
    public void addBusinessAttributes(BusinessContext context) {
        Span currentSpan = Span.current();
        if (currentSpan != null) {
            currentSpan.setAllAttributes(context.toOtelAttributes());
        }
    }
    
    /**
     * Record a business event
     */
    public void recordBusinessEvent(String eventName, BusinessContext context) {
        Span currentSpan = Span.current();
        if (currentSpan != null) {
            currentSpan.addEvent(eventName, context.toOtelAttributes());
        }
    }
}