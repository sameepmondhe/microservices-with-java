package com.example.gateway.tracing;

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
        
        // Gateway-specific attributes (primary focus for gateway service)
        public BusinessContext routeId(String routeId) {
            attributes.put("business.gateway.route_id", routeId);
            return this;
        }
        
        public BusinessContext targetService(String serviceName) {
            attributes.put("business.gateway.target_service", serviceName);
            return this;
        }
        
        public BusinessContext routePath(String path) {
            attributes.put("business.gateway.route_path", path);
            return this;
        }
        
        public BusinessContext requestId(String requestId) {
            attributes.put("business.gateway.request_id", requestId);
            return this;
        }
        
        public BusinessContext userAgent(String userAgent) {
            attributes.put("business.gateway.user_agent", userAgent);
            return this;
        }
        
        public BusinessContext clientIp(String clientIp) {
            attributes.put("business.gateway.client_ip", clientIp);
            return this;
        }
        
        public BusinessContext authenticationStatus(String status) {
            attributes.put("business.gateway.auth_status", status);
            return this;
        }
        
        public BusinessContext userId(String userId) {
            attributes.put("business.gateway.user_id", userId);
            return this;
        }
        
        public BusinessContext apiVersion(String version) {
            attributes.put("business.gateway.api_version", version);
            return this;
        }
        
        // Request/Response attributes
        public BusinessContext requestSize(long bytes) {
            attributes.put("business.gateway.request_size_bytes", bytes);
            return this;
        }
        
        public BusinessContext responseSize(long bytes) {
            attributes.put("business.gateway.response_size_bytes", bytes);
            return this;
        }
        
        public BusinessContext httpMethod(String method) {
            attributes.put("business.gateway.http_method", method);
            return this;
        }
        
        public BusinessContext httpStatus(int status) {
            attributes.put("business.gateway.http_status", status);
            return this;
        }
        
        // Load balancing and circuit breaker attributes
        public BusinessContext loadBalancerInstance(String instance) {
            attributes.put("business.gateway.lb_instance", instance);
            return this;
        }
        
        public BusinessContext circuitBreakerState(String state) {
            attributes.put("business.gateway.circuit_breaker_state", state);
            return this;
        }
        
        public BusinessContext retryAttempt(int attempt) {
            attributes.put("business.gateway.retry_attempt", attempt);
            return this;
        }
        
        // Business operation attributes
        public BusinessContext businessOperation(String operation) {
            attributes.put("business.operation", operation);
            return this;
        }
        
        public BusinessContext transactionId(String transactionId) {
            attributes.put("business.transaction.id", transactionId);
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
        
        public BusinessContext upstreamLatency(long milliseconds) {
            attributes.put("business.performance.upstream_latency_ms", milliseconds);
            return this;
        }
        
        public BusinessContext requestLatency(long milliseconds) {
            attributes.put("business.performance.request_latency_ms", milliseconds);
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
}