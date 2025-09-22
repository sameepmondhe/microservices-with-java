package com.example.loans.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
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
        
        // Loan attributes (primary focus for loans service)
        public BusinessContext loanId(String loanId) {
            attributes.put("business.loan.id", loanId);
            return this;
        }
        
        public BusinessContext loanType(String loanType) {
            attributes.put("business.loan.type", loanType);
            return this;
        }
        
        public BusinessContext loanAmount(BigDecimal amount) {
            attributes.put("business.loan.amount", amount.toString());
            return this;
        }
        
        public BusinessContext loanTerm(int termMonths) {
            attributes.put("business.loan.term_months", termMonths);
            return this;
        }
        
        public BusinessContext interestRate(BigDecimal rate) {
            attributes.put("business.loan.interest_rate", rate.toString());
            return this;
        }
        
        public BusinessContext loanStatus(String status) {
            attributes.put("business.loan.status", status);
            return this;
        }
        
        public BusinessContext loanPurpose(String purpose) {
            attributes.put("business.loan.purpose", purpose);
            return this;
        }
        
        public BusinessContext collateralType(String collateralType) {
            attributes.put("business.loan.collateral_type", collateralType);
            return this;
        }
        
        public BusinessContext collateralValue(BigDecimal value) {
            attributes.put("business.loan.collateral_value", value.toString());
            return this;
        }
        
        // Loan workflow attributes
        public BusinessContext loanOperation(String operation) {
            attributes.put("business.loan.operation", operation);
            return this;
        }
        
        public BusinessContext underwritingStep(String step) {
            attributes.put("business.loan.underwriting.step", step);
            return this;
        }
        
        public BusinessContext underwritingStatus(String status) {
            attributes.put("business.loan.underwriting.status", status);
            return this;
        }
        
        public BusinessContext creditScore(int score) {
            attributes.put("business.loan.credit_score", score);
            return this;
        }
        
        public BusinessContext debtToIncomeRatio(BigDecimal ratio) {
            attributes.put("business.loan.debt_to_income_ratio", ratio.toString());
            return this;
        }
        
        public BusinessContext loanEligible(boolean eligible) {
            attributes.put("business.loan.eligible", eligible);
            return this;
        }
        
        public BusinessContext approvalLevel(String level) {
            attributes.put("business.loan.approval_level", level);
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
                } else if (value instanceof Integer) {
                    builder.put(AttributeKey.longKey(key), (Integer) value);
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
                .setParent(Context.current())
                .startSpan();
        
        // Add business context to the span
        if (context != null) {
            span.setAllAttributes(context.toOtelAttributes());
        }
        
        return span;
    }
}