package com.example.accounts.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Customer Analytics Publisher - Sends customer events to Alloy Customer Analytics Engine
 * Extends existing BusinessContextTracer without disrupting current functionality
 */
@Component
public class CustomerAnalyticsPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomerAnalyticsPublisher.class);
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${customer.analytics.endpoint:http://alloy-customer-analytics:12346/loki/api/v1/push}")
    private String analyticsEndpoint;
    
    @Value("${customer.analytics.enabled:false}")
    private boolean analyticsEnabled;
    
    public CustomerAnalyticsPublisher() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Publish customer transaction event for analytics
     */
    public void publishTransactionEvent(String customerId, String accountType, 
                                      Double transactionAmount, String eventType) {
        if (!analyticsEnabled) {
            logger.debug("Customer analytics disabled, skipping event publication");
            return;
        }
        
        try {
            Map<String, Object> event = createCustomerEvent(
                customerId, accountType, transactionAmount, eventType, "accounts"
            );
            
            publishEvent(event);
            
            logger.info("Published customer analytics event: customerId={}, amount={}, type={}", 
                       customerId, transactionAmount, eventType);
                       
        } catch (Exception e) {
            logger.warn("Failed to publish customer analytics event", e);
        }
    }
    
    /**
     * Publish customer login event
     */
    public void publishLoginEvent(String customerId) {
        publishCustomerEvent(customerId, null, null, "login");
    }
    
    /**
     * Publish customer inquiry event
     */
    public void publishInquiryEvent(String customerId, String accountType) {
        publishCustomerEvent(customerId, accountType, null, "inquiry");
    }
    
    /**
     * Publish account creation event
     */
    public void publishAccountCreationEvent(String customerId, String accountType, Double initialDeposit) {
        publishTransactionEvent(customerId, accountType, initialDeposit, "account_creation");
    }
    
    /**
     * Generic customer event publisher
     */
    public void publishCustomerEvent(String customerId, String accountType, 
                                   Double amount, String eventType) {
        if (!analyticsEnabled) return;
        
        try {
            Map<String, Object> event = createCustomerEvent(
                customerId, accountType, amount, eventType, "accounts"
            );
            publishEvent(event);
        } catch (Exception e) {
            logger.warn("Failed to publish customer event: {}", e.getMessage());
        }
    }
    
    /**
     * Create customer event map
     */
    private Map<String, Object> createCustomerEvent(String customerId, String accountType,
                                                   Double amount, String eventType, String service) {
        Map<String, Object> event = new HashMap<>();
        
        // Core event data
        event.put("customer_id", customerId);
        event.put("event_type", eventType);
        event.put("service", service);
        event.put("timestamp", Instant.now().toString());
        event.put("session_id", generateSessionId(customerId));
        
        // Optional fields
        if (accountType != null) {
            event.put("account_type", accountType);
        }
        if (amount != null) {
            event.put("transaction_amount", amount);
        }
        
        // Add business context
        event.put("source", "accounts-microservice");
        event.put("domain", "banking");
        event.put("analytics_version", "1.0");
        
        return event;
    }
    
    /**
     * Publish event to Alloy Customer Analytics Engine
     */
    private void publishEvent(Map<String, Object> event) {
        try {
            // Create Loki log entry format
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("stream", Map.of(
                "source", "customer_analytics",
                "service", "accounts"
            ));
            logEntry.put("values", new Object[][]{
                {String.valueOf(System.currentTimeMillis() * 1000000), objectMapper.writeValueAsString(event)}
            });
            
            Map<String, Object> payload = Map.of("streams", new Object[]{logEntry});
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Customer-Analytics", "banking-microservices");
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            
            restTemplate.postForEntity(analyticsEndpoint, request, String.class);
            
        } catch (Exception e) {
            logger.error("Failed to send customer analytics event", e);
        }
    }
    
    /**
     * Generate session ID for tracking user sessions
     */
    private String generateSessionId(String customerId) {
        return customerId + "_" + System.currentTimeMillis();
    }
    
    /**
     * Customer Analytics Builder for fluent API
     */
    public static class CustomerEventBuilder {
        private String customerId;
        private String accountType;
        private Double amount;
        private String eventType;
        private final CustomerAnalyticsPublisher publisher;
        
        public CustomerEventBuilder(CustomerAnalyticsPublisher publisher) {
            this.publisher = publisher;
        }
        
        public CustomerEventBuilder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }
        
        public CustomerEventBuilder accountType(String accountType) {
            this.accountType = accountType;
            return this;
        }
        
        public CustomerEventBuilder amount(Double amount) {
            this.amount = amount;
            return this;
        }
        
        public CustomerEventBuilder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }
        
        public void publish() {
            publisher.publishCustomerEvent(customerId, accountType, amount, eventType);
        }
    }
    
    /**
     * Create fluent builder for customer events
     */
    public CustomerEventBuilder event() {
        return new CustomerEventBuilder(this);
    }
}