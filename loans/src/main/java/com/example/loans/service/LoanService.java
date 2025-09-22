package com.example.loans.service;

import com.example.loans.entity.Loan;
import com.example.loans.repository.LoanRepository;
import com.example.loans.tracing.BusinessContextTracer;
import io.opentelemetry.api.trace.Span;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

@Service
public class LoanService {

    private static final Logger logger = LoggerFactory.getLogger(LoanService.class);

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private BusinessContextTracer businessContextTracer;

    // Create
    public Loan createLoan(Loan loan) {
        // Create child span for service layer operation
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("loan-service", "createLoan")
                .loanAmount(new BigDecimal(loan.getLoanAmount()))
                .customerId(loan.getCustomerId())
                .transactionType("LOAN_CREATION");
        
        Span span = businessContextTracer.startChildSpan("loans.service.createLoan", context);
        
        try (var scope = span.makeCurrent()) {
            long startTime = System.currentTimeMillis();
            logger.info("Service: Creating loan for customer: {} with amount: {}", 
                       loan.getCustomerId(), loan.getLoanAmount());
            
            // Perform loan creation with repository call
            Loan createdLoan = loanRepository.save(loan);
            
            // Add success business context with results
            span.setAllAttributes(businessContextTracer.createContext()
                .loanAmount(new BigDecimal(createdLoan.getLoanAmount()))
                .customerId(createdLoan.getCustomerId())
                .toOtelAttributes());
            
            long duration = System.currentTimeMillis() - startTime;
            span.setAllAttributes(businessContextTracer.createContext()
                .processingTime(duration)
                .toOtelAttributes());
            
            logger.info("Service: Created loan {} in {}ms", createdLoan.getLoanId(), duration);
            return createdLoan;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setAllAttributes(businessContextTracer.createContext()
                .errorCode("LOAN_SERVICE_CREATE_FAILED")
                .errorCategory("SERVICE_LAYER_ERROR")
                .toOtelAttributes());
            
            logger.error("Service: Failed to create loan for customer: {}", loan.getCustomerId(), e);
            throw e;
        } finally {
            span.end();
        }
    }

    // Read all
    public List<Loan> getAllLoans() {
        // Create child span for service layer operation
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("loan-service", "getAllLoans");
        
        Span span = businessContextTracer.startChildSpan("loans.service.getAllLoans", context);
        
        try (var scope = span.makeCurrent()) {
            long startTime = System.currentTimeMillis();
            logger.info("Service: Fetching all loans");
            
            List<Loan> loans = loanRepository.findAll();
            
            // Add result context
            span.setAllAttributes(businessContextTracer.createContext()
                .batchSize(loans.size())
                .toOtelAttributes());
            
            long duration = System.currentTimeMillis() - startTime;
            span.setAllAttributes(businessContextTracer.createContext()
                .processingTime(duration)
                .toOtelAttributes());
            
            logger.info("Service: Retrieved {} loans in {}ms", loans.size(), duration);
            return loans;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setAllAttributes(businessContextTracer.createContext()
                .errorCode("LOAN_SERVICE_FETCH_ALL_FAILED")
                .errorCategory("SERVICE_LAYER_ERROR")
                .toOtelAttributes());
            
            logger.error("Service: Failed to fetch all loans", e);
            throw e;
        } finally {
            span.end();
        }
    }

    // Read by ID
    public Optional<Loan> getLoanById(String loanId) {
        // Create child span for service layer operation
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("loan-service", "getLoanById");
        
        Span span = businessContextTracer.startChildSpan("loans.service.getLoanById", context);
        
        try (var scope = span.makeCurrent()) {
            long startTime = System.currentTimeMillis();
            logger.info("Service: Fetching loan with ID: {}", loanId);
            
            Optional<Loan> loan = loanRepository.findById(loanId);
            
            if (loan.isPresent()) {
                // Add success context
                span.setAllAttributes(businessContextTracer.createContext()
                    .customerId(loan.get().getCustomerId())
                    .toOtelAttributes());
                
                long duration = System.currentTimeMillis() - startTime;
                span.setAllAttributes(businessContextTracer.createContext()
                    .processingTime(duration)
                    .toOtelAttributes());
                
                logger.info("Service: Found loan {} in {}ms", loanId, duration);
            } else {
                // Add not found context
                span.setAllAttributes(businessContextTracer.createContext()
                    .errorCode("LOAN_NOT_FOUND")
                    .errorCategory("BUSINESS_LOGIC_ERROR")
                    .toOtelAttributes());
                
                logger.warn("Service: Loan with ID {} not found", loanId);
            }
            
            return loan;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setAllAttributes(businessContextTracer.createContext()
                .errorCode("LOAN_SERVICE_FETCH_BY_ID_FAILED")
                .errorCategory("SERVICE_LAYER_ERROR")
                .toOtelAttributes());
            
            logger.error("Service: Failed to fetch loan with ID: {}", loanId, e);
            throw e;
        } finally {
            span.end();
        }
    }

    // Update
    public Loan updateLoan(String loanId, Loan updatedLoan) {
        // Create child span for service layer operation
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("loan-service", "updateLoan")
                .customerId(updatedLoan.getCustomerId())
                .transactionType("UPDATE");
        
        Span span = businessContextTracer.startChildSpan("loans.service.updateLoan", context);
        
        try (var scope = span.makeCurrent()) {
            long startTime = System.currentTimeMillis();
            logger.info("Service: Updating loan with ID: {}", loanId);
            
            Loan resultLoan = loanRepository.findById(loanId)
                    .map(loan -> {
                        // Update existing loan
                        loan.setLoanType(updatedLoan.getLoanType());
                        loan.setLoanAmount(updatedLoan.getLoanAmount());
                        loan.setLoanStatus(updatedLoan.getLoanStatus());
                        loan.setLoanStartDate(updatedLoan.getLoanStartDate());
                        loan.setLoanEndDate(updatedLoan.getLoanEndDate());
                        loan.setLoanInterestRate(updatedLoan.getLoanInterestRate());
                        return loanRepository.save(loan);
                    })
                    .orElseGet(() -> {
                        // Create new loan if not found
                        updatedLoan.setLoanId(loanId);
                        return loanRepository.save(updatedLoan);
                    });
            
            // Add success context
            span.setAllAttributes(businessContextTracer.createContext()
                .customerId(resultLoan.getCustomerId())
                .toOtelAttributes());
            
            long duration = System.currentTimeMillis() - startTime;
            span.setAllAttributes(businessContextTracer.createContext()
                .processingTime(duration)
                .toOtelAttributes());
            
            logger.info("Service: Updated loan {} in {}ms", loanId, duration);
            return resultLoan;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setAllAttributes(businessContextTracer.createContext()
                .errorCode("LOAN_SERVICE_UPDATE_FAILED")
                .errorCategory("SERVICE_LAYER_ERROR")
                .toOtelAttributes());
            
            logger.error("Service: Failed to update loan with ID: {}", loanId, e);
            throw e;
        } finally {
            span.end();
        }
    }

    // Delete
    public void deleteLoan(String loanId) {
        // Create child span for service layer operation
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("loan-service", "deleteLoan")
                .transactionType("DELETE");
        
        Span span = businessContextTracer.startChildSpan("loans.service.deleteLoan", context);
        
        try (var scope = span.makeCurrent()) {
            long startTime = System.currentTimeMillis();
            logger.info("Service: Deleting loan with ID: {}", loanId);
            
            // Check if loan exists first to add business context
            Optional<Loan> existingLoan = loanRepository.findById(loanId);
            
            if (existingLoan.isPresent()) {
                // Add loan context before deletion
                span.setAllAttributes(businessContextTracer.createContext()
                    .customerId(existingLoan.get().getCustomerId())
                    .toOtelAttributes());
                
                loanRepository.deleteById(loanId);
                
                long duration = System.currentTimeMillis() - startTime;
                span.setAllAttributes(businessContextTracer.createContext()
                    .processingTime(duration)
                    .toOtelAttributes());
                
                logger.info("Service: Deleted loan {} in {}ms", loanId, duration);
            } else {
                // Add not found context
                span.setAllAttributes(businessContextTracer.createContext()
                    .errorCode("LOAN_NOT_FOUND")
                    .errorCategory("BUSINESS_LOGIC_ERROR")
                    .toOtelAttributes());
                
                logger.warn("Service: Cannot delete - Loan with ID {} not found", loanId);
                throw new RuntimeException("Loan not found with ID: " + loanId);
            }
            
        } catch (Exception e) {
            span.recordException(e);
            span.setAllAttributes(businessContextTracer.createContext()
                .errorCode("LOAN_SERVICE_DELETE_FAILED")
                .errorCategory("SERVICE_LAYER_ERROR")
                .toOtelAttributes());
            
            logger.error("Service: Failed to delete loan with ID: {}", loanId, e);
            throw e;
        } finally {
            span.end();
        }
    }
}
