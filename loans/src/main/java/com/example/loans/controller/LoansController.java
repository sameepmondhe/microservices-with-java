package com.example.loans.controller;

import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;

@RestController
public class LoansController {

    private static final Logger logger = LoggerFactory.getLogger(LoansController.class);

    // Helper method to log request details
    private void logRequestDetails(HttpServletRequest request, String operation) {
        logger.info("Request: {} {} from IP: {} - Operation: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getRemoteAddr(),
                    operation);
    }

    @GetMapping("/getAll")
    public List<String> getAllLoans(HttpServletRequest request) {
        logRequestDetails(request, "getAllLoans");
        long startTime = System.currentTimeMillis();

        logger.info("Request received to fetch all loans");
        // Replace with actual logic
        List<String> loans = List.of("Loan1", "Loan2");

        long duration = System.currentTimeMillis() - startTime;
        logger.info("getAllLoans completed in {}ms - Returning {} loans", duration, loans.size());
        return loans;
    }

    @GetMapping("/{id}")
    public String getLoanById(@PathVariable String id, HttpServletRequest request) {
        logRequestDetails(request, "getLoanById");
        long startTime = System.currentTimeMillis();

        logger.info("Request received to fetch loan with ID: {}", id);
        // Replace with actual logic
        String loan = "Loan with id: " + id;

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Loan found with ID: {} in {}ms", id, duration);
        return loan;
    }

    @PostMapping("/create")
    public String createLoan(@RequestBody String loan, HttpServletRequest request) {
        logRequestDetails(request, "createLoan");
        long startTime = System.currentTimeMillis();

        logger.info("Request received to create a new loan: {}", loan);
        // Replace with actual logic
        String createdLoan = "Created loan: " + loan;

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Loan created successfully in {}ms", duration);
        return createdLoan;
    }

    @PutMapping("/{id}")
    public String updateLoan(@PathVariable String id, @RequestBody String loan, HttpServletRequest request) {
        logRequestDetails(request, "updateLoan");
        long startTime = System.currentTimeMillis();

        logger.info("Request received to update loan with ID: {}", id);
        // Replace with actual logic
        String updatedLoan = "Updated loan with id: " + id;

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Loan updated successfully with ID: {} in {}ms", id, duration);
        return updatedLoan;
    }

    @DeleteMapping("/{id}")
    public String deleteLoan(@PathVariable String id, HttpServletRequest request) {
        logRequestDetails(request, "deleteLoan");
        long startTime = System.currentTimeMillis();

        logger.info("Request received to delete loan with ID: {}", id);
        // Replace with actual logic
        String result = "Deleted loan with id: " + id;

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Loan deleted successfully with ID: {} in {}ms", id, duration);
        return result;
    }

    @GetMapping("/eligibility")
    public Map<String, Object> checkLoanEligibility(@RequestParam String customerId, 
                                                   @RequestParam String accountId, 
                                                   HttpServletRequest request) {
        logRequestDetails(request, "checkLoanEligibility");
        long startTime = System.currentTimeMillis();

        logger.info("Request received to check loan eligibility for customer: {} and account: {}", customerId, accountId);
        
        // Simple eligibility logic - in real scenario this would involve credit checks, account history, etc.
        boolean eligible = true; // Default to eligible for demo
        String reason = "Customer meets basic eligibility criteria";
        double maxLoanAmount = 50000.0;
        
        // Simulate some business logic
        if (customerId == null || customerId.isEmpty()) {
            eligible = false;
            reason = "Invalid customer ID";
        } else if (accountId == null || accountId.isEmpty()) {
            eligible = false;
            reason = "Invalid account ID";
        }
        
        Map<String, Object> response = Map.of(
            "customerId", customerId,
            "accountId", accountId,
            "eligible", eligible,
            "reason", reason,
            "maxLoanAmount", maxLoanAmount,
            "interestRate", 5.5
        );

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Loan eligibility check completed for customer: {} in {}ms - Eligible: {}", 
                   customerId, duration, eligible);
        
        return response;
    }
}
