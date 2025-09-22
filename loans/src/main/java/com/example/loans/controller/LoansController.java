package com.example.loans.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.example.loans.tracing.BusinessContextTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import io.opentelemetry.api.trace.Span;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class LoansController {

    private static final Logger logger = LoggerFactory.getLogger(LoansController.class);

    @Autowired
    private BusinessContextTracer businessContextTracer;

    @GetMapping("/getAll")
    public List<String> getAllLoans(HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString();
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("loans-service", "getAllLoans")
                .transactionId(correlationId);
        
        Span span = businessContextTracer.startBusinessSpan("loans.getAllLoans", context);
        
        try {
            long startTime = System.currentTimeMillis();
            logger.info("Request received to fetch all loans");
            
            // Replace with actual logic
            List<String> loans = List.of("Loan1", "Loan2");

            // Add business context
            span.setAllAttributes(businessContextTracer.createContext()
                .batchSize(loans.size())
                .serviceCall("loans-service", "getAllLoans")
                .toOtelAttributes());

            long duration = System.currentTimeMillis() - startTime;
            
            // Add performance context
            span.setAllAttributes(businessContextTracer.createContext()
                .processingTime(duration)
                .toOtelAttributes());
            
            logger.info("getAllLoans completed in {}ms - Returning {} loans", duration, loans.size());
            return loans;
            
        } catch (Exception e) {
            span.recordException(e);
            logger.error("Error in getAllLoans", e);
            throw e;
        } finally {
            span.end();
        }
    }

    @GetMapping("/{id}")
    public String getLoanById(@PathVariable String id, HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString();
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("loans-service", "getLoanById")
                .transactionId(correlationId)
                .loanId(id);
        
        Span span = businessContextTracer.startBusinessSpan("loans.getLoanById", context);
        
        try {
            long startTime = System.currentTimeMillis();
            logger.info("Request received to fetch loan with ID: {}", id);
            
            // Add business context for loan ID
            span.setAllAttributes(businessContextTracer.createContext()
                .serviceCall("loans-service", "getLoanById")
                .loanId(id)
                .toOtelAttributes());
            
            // Replace with actual logic
            String loan = "Loan with id: " + id;

            long duration = System.currentTimeMillis() - startTime;
            
            // Add performance context
            span.setAllAttributes(businessContextTracer.createContext()
                .processingTime(duration)
                .toOtelAttributes());
            
            logger.info("Loan found with ID: {} in {}ms", id, duration);
            return loan;
            
        } catch (Exception e) {
            span.recordException(e);
            logger.error("Error in getLoanById for ID: {}", id, e);
            throw e;
        } finally {
            span.end();
        }
    }

    @PostMapping("/create")
    public String createLoan(@RequestBody String loan, HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString();
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("loans-service", "createLoan")
                .transactionId(correlationId)
                .transactionType("LOAN_CREATION");
        
        Span span = businessContextTracer.startBusinessSpan("loans.createLoan", context);
        
        try {
            long startTime = System.currentTimeMillis();
            logger.info("Request received to create a new loan: {}", loan);
            
            // Add business context for loan creation
            span.setAllAttributes(businessContextTracer.createContext()
                .serviceCall("loans-service", "createLoan")
                .transactionType("LOAN_CREATION")
                .loanOperation("CREATE")
                .toOtelAttributes());
            
            // Replace with actual logic
            String createdLoan = "Created loan: " + loan;

            long duration = System.currentTimeMillis() - startTime;
            
            // Add performance context
            span.setAllAttributes(businessContextTracer.createContext()
                .processingTime(duration)
                .toOtelAttributes());
            
            logger.info("Loan created successfully in {}ms", duration);
            return createdLoan;
            
        } catch (Exception e) {
            span.recordException(e);
            logger.error("Error in createLoan", e);
            throw e;
        } finally {
            span.end();
        }
    }

    @PutMapping("/{id}")
    public String updateLoan(@PathVariable String id, @RequestBody String loan, HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString();
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("loans-service", "updateLoan")
                .transactionId(correlationId)
                .loanId(id);
        
        Span span = businessContextTracer.startBusinessSpan("loans.updateLoan", context);
        
        try {
            long startTime = System.currentTimeMillis();
            logger.info("Request received to update loan with ID: {}", id);
            
            // Add business context for loan update
            span.setAllAttributes(businessContextTracer.createContext()
                .serviceCall("loans-service", "updateLoan")
                .loanId(id)
                .loanOperation("UPDATE")
                .toOtelAttributes());
            
            // Replace with actual logic
            String updatedLoan = "Updated loan with id: " + id;

            long duration = System.currentTimeMillis() - startTime;
            
            // Add performance context
            span.setAllAttributes(businessContextTracer.createContext()
                .processingTime(duration)
                .toOtelAttributes());
            
            logger.info("Loan updated successfully with ID: {} in {}ms", id, duration);
            return updatedLoan;
            
        } catch (Exception e) {
            span.recordException(e);
            logger.error("Error in updateLoan for ID: {}", id, e);
            throw e;
        } finally {
            span.end();
        }
    }

    @DeleteMapping("/{id}")
    public String deleteLoan(@PathVariable String id, HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString();
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("loans-service", "deleteLoan")
                .transactionId(correlationId)
                .loanId(id);
        
        Span span = businessContextTracer.startBusinessSpan("loans.deleteLoan", context);
        
        try {
            long startTime = System.currentTimeMillis();
            logger.info("Request received to delete loan with ID: {}", id);
            
            // Add business context for loan deletion
            span.setAllAttributes(businessContextTracer.createContext()
                .serviceCall("loans-service", "deleteLoan")
                .loanId(id)
                .loanOperation("DELETE")
                .toOtelAttributes());
            
            // Replace with actual logic
            String result = "Deleted loan with id: " + id;

            long duration = System.currentTimeMillis() - startTime;
            
            // Add performance context
            span.setAllAttributes(businessContextTracer.createContext()
                .processingTime(duration)
                .toOtelAttributes());
            
            logger.info("Loan deleted successfully with ID: {} in {}ms", id, duration);
            return result;
            
        } catch (Exception e) {
            span.recordException(e);
            logger.error("Error in deleteLoan for ID: {}", id, e);
            throw e;
        } finally {
            span.end();
        }
    }

    @GetMapping("/eligibility")
    public Map<String, Object> checkLoanEligibility(@RequestParam String customerId, 
                                                   @RequestParam String accountId, 
                                                   HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString();
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("loans-service", "checkLoanEligibility")
                .transactionId(correlationId)
                .customerId(customerId)
                .accountId(accountId);
        
        Span span = businessContextTracer.startBusinessSpan("loans.checkLoanEligibility", context);
        
        try {
            long startTime = System.currentTimeMillis();
            logger.info("Request received to check loan eligibility for customer: {} and account: {}", customerId, accountId);
            
            // Add business context for loan eligibility check
            span.setAllAttributes(businessContextTracer.createContext()
                .customerId(customerId)
                .accountId(accountId)
                .serviceCall("loans-service", "checkLoanEligibility")
                .underwritingStep("INITIAL_ELIGIBILITY_CHECK")
                .toOtelAttributes());
            
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
            
            // Add business context based on eligibility result
            span.setAllAttributes(businessContextTracer.createContext()
                .loanEligible(eligible)
                .underwritingStatus(eligible ? "APPROVED" : "DENIED")
                .toOtelAttributes());
            
            Map<String, Object> response = Map.of(
                "customerId", customerId,
                "accountId", accountId,
                "eligible", eligible,
                "reason", reason,
                "maxLoanAmount", maxLoanAmount,
                "interestRate", 5.5
            );

            long duration = System.currentTimeMillis() - startTime;
            
            // Add performance context
            span.setAllAttributes(businessContextTracer.createContext()
                .processingTime(duration)
                .toOtelAttributes());
            
            // Record business event
            businessContextTracer.recordBusinessEvent(
                eligible ? "loan.eligibility.approved" : "loan.eligibility.denied",
                businessContextTracer.createContext()
                    .customerId(customerId)
                    .loanEligible(eligible)
            );
            
            logger.info("Loan eligibility check completed for customer: {} in {}ms - Eligible: {}", 
                       customerId, duration, eligible);
            
            return response;
            
        } catch (Exception e) {
            span.recordException(e);
            logger.error("Error in checkLoanEligibility for customer: {}, account: {}", customerId, accountId, e);
            throw e;
        } finally {
            span.end();
        }
    }
}
