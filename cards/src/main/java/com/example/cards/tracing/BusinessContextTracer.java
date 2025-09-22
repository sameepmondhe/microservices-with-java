package com.example.cards.tracing;

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
        
        // Card attributes (primary focus for cards service)
        public BusinessContext cardId(String cardId) {
            attributes.put("business.card.id", cardId);
            return this;
        }
        
        public BusinessContext cardType(String cardType) {
            attributes.put("business.card.type", cardType);
            return this;
        }
        
        public BusinessContext cardNumber(String cardNumber) {
            // Mask card number for security
            String maskedNumber = cardNumber.length() > 4 ? 
                "*".repeat(cardNumber.length() - 4) + cardNumber.substring(cardNumber.length() - 4) : cardNumber;
            attributes.put("business.card.number_masked", maskedNumber);
            return this;
        }
        
        public BusinessContext cardStatus(String status) {
            attributes.put("business.card.status", status);
            return this;
        }
        
        public BusinessContext cardLimit(BigDecimal limit) {
            attributes.put("business.card.limit", limit.toString());
            return this;
        }
        
        public BusinessContext cardBalance(BigDecimal balance) {
            attributes.put("business.card.balance", balance.toString());
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
        
        public BusinessContext merchantName(String merchantName) {
            attributes.put("business.transaction.merchant", merchantName);
            return this;
        }
        
        // Card workflow attributes
        public BusinessContext cardOperation(String operation) {
            attributes.put("business.card.operation", operation);
            return this;
        }
        
        public BusinessContext cardActivationStep(String step) {
            attributes.put("business.card.activation.step", step);
            return this;
        }
        
        public BusinessContext riskLevel(String riskLevel) {
            attributes.put("business.risk.level", riskLevel);
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
    
    /**
     * Start a child span with business context for service layer operations
     */
    public Span startChildSpan(String operationName, BusinessContext context) {
        Span span = tracer.spanBuilder(operationName)
                .setParent(io.opentelemetry.context.Context.current())
                .startSpan();
        
        // Add business context to the span
        if (context != null) {
            span.setAllAttributes(context.toOtelAttributes());
        }
        
        return span;
    }
}