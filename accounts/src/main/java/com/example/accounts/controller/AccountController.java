package com.example.accounts.controller;

import com.example.accounts.entity.Account;
import com.example.accounts.service.AccountService;
import com.example.accounts.service.OnboardingService;
import com.example.accounts.dto.OnboardingRequest;
import com.example.accounts.dto.OnboardingResponse;
import com.example.accounts.tracing.BusinessContextTracer;
import com.example.accounts.analytics.CustomerAnalyticsPublisher;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class AccountController {

    private static final Logger logger = LoggerFactory.getLogger(AccountController.class);

    @Autowired
    private AccountService accountService;

    @Autowired
    private OnboardingService onboardingService;

    @Autowired
    private BusinessContextTracer businessContextTracer;

    @Autowired
    private CustomerAnalyticsPublisher customerAnalyticsPublisher;

    // Custom metrics - injected from MetricsConfig
    @Autowired
    private Counter accountCreationCounter;
    
    @Autowired
    private Counter accountCreationFailureCounter;
    
    @Autowired
    private Counter accountRetrievalCounter;

    // Helper method to start business span with request context
    private Span startRequestSpan(HttpServletRequest request, String operation) {
        String correlationId = UUID.randomUUID().toString();
        
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("accounts-service", operation)
                .transactionId(correlationId);
        
        Span span = businessContextTracer.startBusinessSpan("accounts." + operation, context);
        
        // Add HTTP context as span attributes
        span.setAttribute("http.method", request.getMethod());
        span.setAttribute("http.url", request.getRequestURI());
        span.setAttribute("http.client_ip", request.getRemoteAddr());
        span.setAttribute("http.user_agent", request.getHeader("User-Agent") != null ? request.getHeader("User-Agent") : "");
        span.setAttribute("correlation.id", correlationId);
        
        logger.info("Processing request: {} {} - Operation: {}", 
                    request.getMethod(), 
                    request.getRequestURI(), 
                    operation);
        
        return span;
    }

    @GetMapping("/getAll")
    @Timed(value = "accounts.getAll", description = "Time taken to fetch all accounts")
    public List<Account> getAllAccounts(HttpServletRequest request) {
        Span span = startRequestSpan(request, "getAllAccounts");
        
        try {
            logger.info("Request received to fetch all accounts");
            List<Account> accounts = accountService.getAllAccounts();
            
            // Add business context
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext()
                    .batchSize(accounts.size())
                    .serviceCall("accounts-service", "getAllAccounts")
            );
            
            // Record retrieval metric
            accountRetrievalCounter.increment();
            
            logger.info("getAllAccounts completed - Returning {} accounts", accounts.size());
            return accounts;
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    @PostMapping("/create")
    @Timed(value = "accounts.create", description = "Time taken to create an account")
    public Account createAccount(@RequestBody Account account, HttpServletRequest request) {
        Span span = startRequestSpan(request, "createAccount");
        
        try {
            logger.info("Request received to create a new account: {}", account.toString());

            // Add business context before creating account
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext()
                    .customerId(account.getCustomerId())
                    .accountType(account.getAccountType())
                    .serviceCall("accounts-service", "createAccount")
            );

            Account createdAccount = accountService.createAccount(account);
            
            // Add more business context after successful creation
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext()
                    .accountId(createdAccount.getAccountId())
            );
            
            // 🎯 CUSTOMER ANALYTICS: Publish account creation event
            Double balanceAmount = null;
            try {
                balanceAmount = account.getAccountBalance() != null ? 
                    Double.parseDouble(account.getAccountBalance()) : 0.0;
            } catch (NumberFormatException e) {
                balanceAmount = 0.0;
            }
            
            customerAnalyticsPublisher.publishAccountCreationEvent(
                account.getCustomerId(),
                account.getAccountType(),
                balanceAmount
            );
            
            // Record successful creation
            accountCreationCounter.increment();
            
            logger.info("Account created successfully with ID: {}", createdAccount.getAccountId());
            return createdAccount;
        } catch (Exception e) {
            // Record failure
            accountCreationFailureCounter.increment();
            span.recordException(e);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
            logger.error("Failed to create account: {}", e.getMessage(), e);
            throw e;
        } finally {
            span.end();
        }
    }

    @GetMapping("/{id}")
    @Timed(value = "accounts.getById", description = "Time taken to fetch account by ID")
    public ResponseEntity<Account> getAccountById(@PathVariable String id, HttpServletRequest request) {
        Span span = startRequestSpan(request, "getAccountById");
        
        try {
            logger.info("Request received to fetch account with ID: {}", id);
            
            // Add business context
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext()
                    .accountId(id)
                    .serviceCall("accounts-service", "getAccountById")
            );
            
            Account account = accountService.getAccountById(id);
            accountRetrievalCounter.increment();
            logger.info("Account found with ID: {}", id);
            
            // Add additional business context for found account
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext()
                    .customerId(account.getCustomerId())
                    .accountType(account.getAccountType())
            );
            
            return ResponseEntity.ok(account);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    @PutMapping("/{id}")
    @Timed(value = "accounts.update", description = "Time taken to update an account")
    public Account updateAccount(@PathVariable String id, @RequestBody Account account, HttpServletRequest request) {
        Span span = startRequestSpan(request, "updateAccount");
        
        try {
            logger.info("Request received to update account with ID: {}", id);
            
            // Add business context
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext()
                    .accountId(id)
                    .customerId(account.getCustomerId())
                    .accountType(account.getAccountType())
                    .serviceCall("accounts-service", "updateAccount")
            );
            
            Account updatedAccount = accountService.updateAccount(id, account);
            logger.info("Account updated successfully with ID: {}", id);
            return updatedAccount;
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    @DeleteMapping("/{id}")
    @Timed(value = "accounts.delete", description = "Time taken to delete an account")
    public void deleteAccount(@PathVariable String id, HttpServletRequest request) {
        Span span = startRequestSpan(request, "deleteAccount");
        
        try {
            logger.info("Request received to delete account with ID: {}", id);
            
            // Add business context
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext()
                    .accountId(id)
                    .serviceCall("accounts-service", "deleteAccount")
            );
            
            accountService.deleteAccount(id);
            logger.info("Account deleted successfully with ID: {}", id);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    @PostMapping("/onboarding")
    @Timed(value = "accounts.onboarding", description = "Time taken for customer onboarding process")
    public ResponseEntity<OnboardingResponse> processOnboarding(@RequestBody OnboardingRequest request, HttpServletRequest httpRequest) {
        Span span = startRequestSpan(httpRequest, "processOnboarding");
        
        try {
            logger.info("Starting customer onboarding process for customer: {}", request.getCustomerId());
            
            // Add business context for onboarding process
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext()
                    .customerId(request.getCustomerId())
                    .onboardingStep("start")
                    .serviceCall("accounts-service", "processOnboarding")
            );
            
            OnboardingResponse response = onboardingService.processCustomerOnboarding(request);
            
            // Add business context based on result
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext()
                    .onboardingStatus(response.getStatus())
                    .onboardingStep("complete")
            );
            
            if ("SUCCESS".equals(response.getStatus())) {
                accountCreationCounter.increment();
                businessContextTracer.recordBusinessEvent("onboarding.success", 
                    businessContextTracer.createContext().customerId(request.getCustomerId()));
            } else if ("FAILED".equals(response.getStatus())) {
                accountCreationFailureCounter.increment();
                businessContextTracer.recordBusinessEvent("onboarding.failure", 
                    businessContextTracer.createContext()
                        .customerId(request.getCustomerId())
                        .errorCode(response.getErrors() != null && !response.getErrors().isEmpty() 
                            ? response.getErrors().get(0) : "UNKNOWN"));
            }
            
            logger.info("Customer onboarding completed for: {} with status: {}", 
                       request.getCustomerId(), response.getStatus());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            accountCreationFailureCounter.increment();
            span.recordException(e);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
            
            businessContextTracer.recordBusinessEvent("onboarding.exception", 
                businessContextTracer.createContext()
                    .customerId(request.getCustomerId())
                    .errorCategory("system_error"));
                    
            logger.error("Customer onboarding failed for: {}. Error: {}", request.getCustomerId(), e.getMessage(), e);
            throw e;
        } finally {
            span.end();
        }
    }
}
