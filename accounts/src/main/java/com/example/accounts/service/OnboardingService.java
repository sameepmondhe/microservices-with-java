package com.example.accounts.service;

import com.example.accounts.dto.CustomerDto;
import com.example.accounts.dto.OnboardingRequest;
import com.example.accounts.dto.OnboardingResponse;
import com.example.accounts.entity.Account;
import com.example.accounts.tracing.BusinessContextTracer;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OnboardingService {
    
    private static final Logger logger = LoggerFactory.getLogger(OnboardingService.class);
    
    private static final String GATEWAY_URL = "http://host.docker.internal:8072";
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private AccountService accountService;
    
    @Autowired
    private BusinessContextTracer businessTracer;
    
    public OnboardingResponse processCustomerOnboarding(OnboardingRequest request) {
        long startTime = System.currentTimeMillis();
        String correlationId = UUID.randomUUID().toString();
        
        // Initialize response and errors outside try block
        OnboardingResponse response = new OnboardingResponse();
        response.setCustomerId(request.getCustomerId());
        List<String> errors = new ArrayList<>();
        
        // Create business context for the entire onboarding workflow
        var businessContext = businessTracer.createContext()
                .customerId(request.getCustomerId())
                .accountType(request.getAccountType())
                .transactionAmount(new BigDecimal(request.getInitialDeposit()))
                .onboardingStep("START")
                .onboardingStatus("IN_PROGRESS")
                .transactionType("ONBOARDING");
        
        // Start business span for the entire onboarding process
        Span onboardingSpan = businessTracer.startBusinessSpan("customer.onboarding.workflow", businessContext);
        
        try {
            // Add correlation ID to span for tracing
            onboardingSpan.setAttribute("correlation.id", correlationId);
            onboardingSpan.setAttribute("operation", "customer_onboarding");
            
            logger.info("Starting customer onboarding process for customer: {}", request.getCustomerId());
            
            // Step 1: Verify customer exists
            businessTracer.recordBusinessEvent("onboarding.step.start", 
                businessTracer.createContext().onboardingStep("CUSTOMER_VERIFICATION"));
            
            CustomerDto customer = verifyCustomer(request.getCustomerId());
            if (customer == null) {
                errors.add("Customer not found");
                response.setStatus("FAILED");
                response.setErrors(errors);
                
                // Record failure in business context
                businessTracer.addBusinessAttributes(
                    businessTracer.createContext()
                        .onboardingStatus("FAILED")
                        .errorCode("CUSTOMER_NOT_FOUND")
                        .errorCategory("VALIDATION_ERROR"));
                
                // Still set processing time for failed requests
                long duration = System.currentTimeMillis() - startTime;
                response.setProcessingTime(duration + "ms");
                
                businessTracer.addBusinessAttributes(
                    businessTracer.createContext().processingTime(duration));
                
                logger.warn("Customer onboarding failed - customer not found: {} in {}ms", 
                           request.getCustomerId(), duration);
                return response;
            }
            
            // Record successful customer verification
            businessTracer.recordBusinessEvent("onboarding.step.completed", 
                businessTracer.createContext()
                    .onboardingStep("CUSTOMER_VERIFICATION")
                    .customerType(customer.getName() != null ? "EXISTING" : "NEW"));
            
            // Step 2: Create primary account
            businessTracer.recordBusinessEvent("onboarding.step.start", 
                businessTracer.createContext().onboardingStep("ACCOUNT_CREATION"));
            
            Account account = createPrimaryAccount(request);
            if (account != null) {
                response.setAccountId(account.getAccountId());
                
                // Record account creation success
                businessTracer.addBusinessAttributes(
                    businessTracer.createContext()
                        .accountId(account.getAccountId())
                        .accountBalance(new BigDecimal(account.getAccountBalance())));
                
                businessTracer.recordBusinessEvent("onboarding.step.completed", 
                    businessTracer.createContext()
                        .onboardingStep("ACCOUNT_CREATION")
                        .accountId(account.getAccountId()));
                
                logger.info("Account created successfully: {}", account.getAccountId());
            } else {
                errors.add("Failed to create account");
                businessTracer.addBusinessAttributes(
                    businessTracer.createContext()
                        .errorCode("ACCOUNT_CREATION_FAILED")
                        .errorCategory("SERVICE_ERROR"));
            }
            
            // Step 3: Issue credit card (if requested)
            if (request.isRequestCreditCard() && account != null) {
                String cardId = issueCreditCard(request.getCustomerId(), account.getAccountId());
                if (cardId != null) {
                    response.setCardId(cardId);
                    logger.info("Credit card issued successfully: {}", cardId);
                } else {
                    errors.add("Failed to issue credit card");
                }
            }
            
            // Step 4: Check loan eligibility (if requested)
            if (request.isCheckLoanEligibility() && account != null) {
                boolean eligible = checkLoanEligibility(request.getCustomerId(), account.getAccountId());
                response.setLoanEligible(eligible);
                logger.info("Loan eligibility check completed: {}", eligible);
            }
            
            // Set final status
            response.setStatus(errors.isEmpty() ? "SUCCESS" : "PARTIAL_SUCCESS");
            response.setErrors(errors);
            
            long duration = System.currentTimeMillis() - startTime;
            response.setProcessingTime(duration + "ms");
            
            logger.info("Customer onboarding completed for: {} in {}ms with status: {}", 
                       request.getCustomerId(), duration, response.getStatus());
            
        } catch (Exception e) {
            logger.error("Customer onboarding failed for: {}. Error: {}", request.getCustomerId(), e.getMessage(), e);
            errors.add("Onboarding process failed: " + e.getMessage());
            response.setStatus("FAILED");
            response.setErrors(errors);
        } finally {
            // Close the business span
            onboardingSpan.end();
        }
        
        return response;
    }
    
    private CustomerDto verifyCustomer(String customerId) {
        logger.info("Step 1: Verifying customer exists: {}", customerId);
        
        try {
            String url = GATEWAY_URL + "/customers/" + customerId;
            CustomerDto customer = restTemplate.getForObject(url, CustomerDto.class);
            
            if (customer != null) {
                logger.info("Successfully verified customer: {}", customerId);
                return customer;
            } else {
                logger.warn("Customer not found: {}", customerId);
                return null;
            }
        } catch (RestClientException e) {
            logger.error("Failed to verify customer: {}. Error: {}", customerId, e.getMessage());
            return null;
        }
    }
    
    private Account createPrimaryAccount(OnboardingRequest request) {
        logger.info("Step 2: Creating primary account for customer: {}", request.getCustomerId());
        
        Account account = new Account();
        account.setAccountId("ACC-" + UUID.randomUUID().toString().substring(0, 8));
        account.setCustomerId(request.getCustomerId());
        account.setAccountName("Primary Savings Account");
        account.setAccountType(request.getAccountType() != null ? request.getAccountType() : "SAVINGS");
        account.setAccountStatus("ACTIVE");
        account.setAccountBalance(request.getInitialDeposit() != null ? request.getInitialDeposit() : "0.00");
        account.setAccountCurrency("USD");
        account.setAccountCreatedDate(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        return accountService.createAccount(account);
    }
    
    private String issueCreditCard(String customerId, String accountId) {
        try {
            logger.info("Step 3: Issuing credit card for customer: {} and account: {}", customerId, accountId);
            
            Map<String, String> cardRequest = Map.of(
                "customerId", customerId,
                "accountId", accountId,
                "cardType", "CREDIT",
                "cardStatus", "ACTIVE",
                "cardHolderName", "Customer " + customerId
            );
            
            String url = GATEWAY_URL + "/cards/create";
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, cardRequest, Map.class);
            
            if (response != null && response.containsKey("cardId")) {
                String cardId = (String) response.get("cardId");
                logger.info("Credit card issued successfully: {}", cardId);
                return cardId;
            } else {
                logger.warn("Credit card creation failed - no cardId in response");
                return null;
            }
            
        } catch (RestClientException e) {
            logger.error("Failed to call cards service for customer: {}. Error: {}", customerId, e.getMessage());
            return null;
        }
    }
    
    private boolean checkLoanEligibility(String customerId, String accountId) {
        try {
            logger.info("Step 4: Checking loan eligibility for customer: {} and account: {}", customerId, accountId);
            
            String url = GATEWAY_URL + "/loans/eligibility?customerId=" + customerId + "&accountId=" + accountId;
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && response.containsKey("eligible")) {
                boolean eligible = (Boolean) response.get("eligible");
                logger.info("Loan eligibility check completed: {}", eligible);
                return eligible;
            } else {
                logger.warn("Loan eligibility check failed - no eligible field in response");
                return false;
            }
            
        } catch (RestClientException e) {
            logger.error("Failed to call loans service for customer: {}. Error: {}", customerId, e.getMessage());
            return false;
        }
    }
}