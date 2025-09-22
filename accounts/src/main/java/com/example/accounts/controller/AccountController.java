package com.example.accounts.controller;

import com.example.accounts.entity.Account;
import com.example.accounts.service.AccountService;
import com.example.accounts.service.OnboardingService;
import com.example.accounts.dto.OnboardingRequest;
import com.example.accounts.dto.OnboardingResponse;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class AccountController {

    private static final Logger logger = LoggerFactory.getLogger(AccountController.class);

    @Autowired
    private AccountService accountService;

    @Autowired
    private OnboardingService onboardingService;

    // Custom metrics - injected from MetricsConfig
    @Autowired
    private Counter accountCreationCounter;
    
    @Autowired
    private Counter accountCreationFailureCounter;
    
    @Autowired
    private Counter accountRetrievalCounter;

    // Helper method to log request details with structured context
    private void logRequestDetails(HttpServletRequest request, String operation) {
        // Generate correlation ID for this request
        String correlationId = UUID.randomUUID().toString();
        
        // Add structured context to MDC
        MDC.put("correlationId", correlationId);
        MDC.put("operation", operation);
        MDC.put("clientIp", request.getRemoteAddr());
        MDC.put("userAgent", request.getHeader("User-Agent"));
        
        logger.info("Processing request: {} {} - Operation: {}", 
                    request.getMethod(), 
                    request.getRequestURI(), 
                    operation);
    }
    
    // Helper method to clear MDC context
    private void clearMDC() {
        MDC.clear();
    }

    @GetMapping("/getAll")
    @Timed(value = "accounts.getAll", description = "Time taken to fetch all accounts")
    public List<Account> getAllAccounts(HttpServletRequest request) {
        logRequestDetails(request, "getAllAccounts");
        
        try {
            logger.info("Request received to fetch all accounts");
            List<Account> accounts = accountService.getAllAccounts();
            
            // Record retrieval metric
            accountRetrievalCounter.increment();
            
            logger.info("getAllAccounts completed - Returning {} accounts", accounts.size());
            return accounts;
        } finally {
            clearMDC();
        }
    }

    @PostMapping("/create")
    @Timed(value = "accounts.create", description = "Time taken to create an account")
    public Account createAccount(@RequestBody Account account, HttpServletRequest request) {
        logRequestDetails(request, "createAccount");
        
        try {
            logger.info("Request received to create a new account: {}", Map.of(
                "accountDetails", account.toString()
            ));

            Account createdAccount = accountService.createAccount(account);
            
            // Record successful creation
            accountCreationCounter.increment();
            
            logger.info("Account created successfully with ID: {}", createdAccount.getAccountId());
            return createdAccount;
        } catch (Exception e) {
            // Record failure
            accountCreationFailureCounter.increment();
            logger.error("Failed to create account: {}", e.getMessage(), e);
            throw e;
        } finally {
            clearMDC();
        }
    }

    @GetMapping("/{id}")
    @Timed(value = "accounts.getById", description = "Time taken to fetch account by ID")
    public ResponseEntity<Account> getAccountById(@PathVariable String id, HttpServletRequest request) {
        logRequestDetails(request, "getAccountById");
        
        try {
            logger.info("Request received to fetch account with ID: {}", id);
            return accountService.getAccountById(id)
                    .map(account -> {
                        accountRetrievalCounter.increment();
                        logger.info("Account found with ID: {}", id);
                        return ResponseEntity.ok(account);
                    })
                    .orElseGet(() -> {
                        logger.warn("Account not found with ID: {}", id);
                        return ResponseEntity.notFound().build();
                    });
        } finally {
            clearMDC();
        }
    }

    @PutMapping("/{id}")
    @Timed(value = "accounts.update", description = "Time taken to update an account")
    public Account updateAccount(@PathVariable String id, @RequestBody Account account, HttpServletRequest request) {
        logRequestDetails(request, "updateAccount");
        
        try {
            logger.info("Request received to update account with ID: {}", id);
            Account updatedAccount = accountService.updateAccount(id, account);
            logger.info("Account updated successfully with ID: {}", id);
            return updatedAccount;
        } finally {
            clearMDC();
        }
    }

    @DeleteMapping("/{id}")
    @Timed(value = "accounts.delete", description = "Time taken to delete an account")
    public void deleteAccount(@PathVariable String id, HttpServletRequest request) {
        logRequestDetails(request, "deleteAccount");
        
        try {
            logger.info("Request received to delete account with ID: {}", id);
            accountService.deleteAccount(id);
            logger.info("Account deleted successfully with ID: {}", id);
        } finally {
            clearMDC();
        }
    }

    @PostMapping("/onboarding")
    @Timed(value = "accounts.onboarding", description = "Time taken for customer onboarding process")
    public ResponseEntity<OnboardingResponse> processOnboarding(@RequestBody OnboardingRequest request, HttpServletRequest httpRequest) {
        logRequestDetails(httpRequest, "processOnboarding");
        
        try {
            logger.info("Starting customer onboarding process for customer: {}", request.getCustomerId());
            
            OnboardingResponse response = onboardingService.processCustomerOnboarding(request);
            
            if ("SUCCESS".equals(response.getStatus())) {
                accountCreationCounter.increment();
            } else if ("FAILED".equals(response.getStatus())) {
                accountCreationFailureCounter.increment();
            }
            
            logger.info("Customer onboarding completed for: {} with status: {}", 
                       request.getCustomerId(), response.getStatus());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            accountCreationFailureCounter.increment();
            logger.error("Customer onboarding failed for: {}. Error: {}", request.getCustomerId(), e.getMessage(), e);
            throw e;
        } finally {
            clearMDC();
        }
    }
}
