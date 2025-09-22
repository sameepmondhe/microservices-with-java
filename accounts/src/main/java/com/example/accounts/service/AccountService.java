package com.example.accounts.service;

import com.example.accounts.entity.Account;
import com.example.accounts.repository.AccountRepository;
import com.example.accounts.tracing.BusinessContextTracer;
import io.opentelemetry.api.trace.Span;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@Service
public class AccountService {

    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BusinessContextTracer businessContextTracer;

    // Create
    public Account createAccount(Account account) {
        // Create child span for service layer operation
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("account-service", "createAccount")
                .accountType(account.getAccountType())
                .customerId(account.getCustomerId())
                .transactionType("ACCOUNT_CREATION");
        
        Span span = businessContextTracer.startChildSpan("accounts.service.createAccount", context);
        
        try (var scope = span.makeCurrent()) {
            long startTime = System.currentTimeMillis();
            logger.info("Service: Creating account for customer: {} with type: {}", 
                       account.getCustomerId(), account.getAccountType());
            
            // Perform account creation with repository call
            Account createdAccount = accountRepository.save(account);
            
            // Add success business context with results
            span.setAllAttributes(businessContextTracer.createContext()
                .accountId(createdAccount.getAccountId())
                .accountType(createdAccount.getAccountType())
                .toOtelAttributes());
            
            long duration = System.currentTimeMillis() - startTime;
            span.setAllAttributes(businessContextTracer.createContext()
                .processingTime(duration)
                .toOtelAttributes());
            
            // Record business event for account creation
            businessContextTracer.recordBusinessEvent("account.service.created", 
                businessContextTracer.createContext()
                    .accountId(createdAccount.getAccountId())
                    .customerId(createdAccount.getCustomerId())
                    .accountType(createdAccount.getAccountType()));
            
            logger.info("Service: Account created successfully: {} for customer: {} in {}ms", 
                       createdAccount.getAccountId(), createdAccount.getCustomerId(), duration);
            
            return createdAccount;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setAllAttributes(businessContextTracer.createContext()
                .errorCode("ACCOUNT_SERVICE_CREATION_FAILED")
                .errorCategory("SERVICE_LAYER_ERROR")
                .toOtelAttributes());
            
            logger.error("Service: Failed to create account for customer: {}", account.getCustomerId(), e);
            throw e;
        } finally {
            span.end();
        }
    }

    // Read all
    public List<Account> getAllAccounts() {
        // Create child span for service layer operation
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("account-service", "getAllAccounts");
        
        Span span = businessContextTracer.startChildSpan("accounts.service.getAllAccounts", context);
        
        try (var scope = span.makeCurrent()) {
            long startTime = System.currentTimeMillis();
            logger.info("Service: Fetching all accounts");
            
            List<Account> accounts = accountRepository.findAll();
            
            // Add result context
            span.setAllAttributes(businessContextTracer.createContext()
                .batchSize(accounts.size())
                .toOtelAttributes());
            
            long duration = System.currentTimeMillis() - startTime;
            span.setAllAttributes(businessContextTracer.createContext()
                .processingTime(duration)
                .toOtelAttributes());
            
            logger.info("Service: Retrieved {} accounts in {}ms", accounts.size(), duration);
            return accounts;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setAllAttributes(businessContextTracer.createContext()
                .errorCode("ACCOUNT_SERVICE_FETCH_ALL_FAILED")
                .errorCategory("SERVICE_LAYER_ERROR")
                .toOtelAttributes());
            
            logger.error("Service: Failed to fetch all accounts", e);
            throw e;
        } finally {
            span.end();
        }
    }

    // Read by ID
    public Account getAccountById(String id) {
        // Create child span for service layer operation
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("account-service", "getAccountById")
                .accountId(id);
        
        Span span = businessContextTracer.startChildSpan("accounts.service.getAccountById", context);
        
        try (var scope = span.makeCurrent()) {
            long startTime = System.currentTimeMillis();
            logger.info("Service: Fetching account with ID: {}", id);
            
            Optional<Account> account = accountRepository.findById(id);
            
            if (account.isPresent()) {
                // Add success context
                span.setAllAttributes(businessContextTracer.createContext()
                    .accountType(account.get().getAccountType())
                    .customerId(account.get().getCustomerId())
                    .toOtelAttributes());
                
                long duration = System.currentTimeMillis() - startTime;
                span.setAllAttributes(businessContextTracer.createContext()
                    .processingTime(duration)
                    .toOtelAttributes());
                
                logger.info("Service: Found account {} in {}ms", id, duration);
                return account.get();
            } else {
                // Add not found context
                span.setAllAttributes(businessContextTracer.createContext()
                    .errorCode("ACCOUNT_NOT_FOUND")
                    .errorCategory("BUSINESS_LOGIC_ERROR")
                    .toOtelAttributes());
                
                logger.warn("Service: Account with ID {} not found", id);
                throw new RuntimeException("Account not found with ID: " + id);
            }
            
        } catch (Exception e) {
            span.recordException(e);
            span.setAllAttributes(businessContextTracer.createContext()
                .errorCode("ACCOUNT_SERVICE_FETCH_BY_ID_FAILED")
                .errorCategory("SERVICE_LAYER_ERROR")
                .toOtelAttributes());
            
            logger.error("Service: Failed to fetch account with ID: {}", id, e);
            throw e;
        } finally {
            span.end();
        }
    }

    // Update
    public Account updateAccount(String id, Account account) {
        // Create child span for service layer operation
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("account-service", "updateAccount")
                .accountId(id)
                .accountType(account.getAccountType())
                .customerId(account.getCustomerId())
                .transactionType("UPDATE");
        
        Span span = businessContextTracer.startChildSpan("accounts.service.updateAccount", context);
        
        try (var scope = span.makeCurrent()) {
            long startTime = System.currentTimeMillis();
            logger.info("Service: Updating account with ID: {}", id);
            
            // Check if account exists
            Optional<Account> existingAccount = accountRepository.findById(id);
            
            if (existingAccount.isPresent()) {
                // Set the ID to ensure we're updating the correct account
                account.setAccountId(id);
                
                // Preserve original creation date if not provided
                if (account.getAccountCreatedDate() == null || account.getAccountCreatedDate().isEmpty()) {
                    account.setAccountCreatedDate(existingAccount.get().getAccountCreatedDate());
                }
                
                Account updatedAccount = accountRepository.save(account);
                
                // Add success context
                span.setAllAttributes(businessContextTracer.createContext()
                    .accountType(updatedAccount.getAccountType())
                    .customerId(updatedAccount.getCustomerId())
                    .toOtelAttributes());
                
                long duration = System.currentTimeMillis() - startTime;
                span.setAllAttributes(businessContextTracer.createContext()
                    .processingTime(duration)
                    .toOtelAttributes());
                
                logger.info("Service: Updated account {} in {}ms", id, duration);
                return updatedAccount;
            } else {
                // Add not found context
                span.setAllAttributes(businessContextTracer.createContext()
                    .errorCode("ACCOUNT_NOT_FOUND")
                    .errorCategory("BUSINESS_LOGIC_ERROR")
                    .toOtelAttributes());
                
                logger.warn("Service: Cannot update - Account with ID {} not found", id);
                throw new RuntimeException("Account not found with ID: " + id);
            }
            
        } catch (Exception e) {
            span.recordException(e);
            span.setAllAttributes(businessContextTracer.createContext()
                .errorCode("ACCOUNT_SERVICE_UPDATE_FAILED")
                .errorCategory("SERVICE_LAYER_ERROR")
                .toOtelAttributes());
            
            logger.error("Service: Failed to update account with ID: {}", id, e);
            throw e;
        } finally {
            span.end();
        }
    }

        // Delete
    public void deleteAccount(String id) {
        // Create child span for service layer operation
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("account-service", "deleteAccount")
                .accountId(id)
                .transactionType("DELETE");
        
        Span span = businessContextTracer.startChildSpan("accounts.service.deleteAccount", context);
        
        try (var scope = span.makeCurrent()) {
            long startTime = System.currentTimeMillis();
            logger.info("Service: Deleting account with ID: {}", id);
            
            // Check if account exists first
            Optional<Account> existingAccount = accountRepository.findById(id);
            
            if (existingAccount.isPresent()) {
                // Add account context before deletion
                span.setAllAttributes(businessContextTracer.createContext()
                    .accountType(existingAccount.get().getAccountType())
                    .customerId(existingAccount.get().getCustomerId())
                    .toOtelAttributes());
                
                accountRepository.deleteById(id);
                
                long duration = System.currentTimeMillis() - startTime;
                span.setAllAttributes(businessContextTracer.createContext()
                    .processingTime(duration)
                    .toOtelAttributes());
                
                logger.info("Service: Deleted account {} in {}ms", id, duration);
            } else {
                // Add not found context
                span.setAllAttributes(businessContextTracer.createContext()
                    .errorCode("ACCOUNT_NOT_FOUND")
                    .errorCategory("BUSINESS_LOGIC_ERROR")
                    .toOtelAttributes());
                
                logger.warn("Service: Cannot delete - Account with ID {} not found", id);
                throw new RuntimeException("Account not found with ID: " + id);
            }
            
        } catch (Exception e) {
            span.recordException(e);
            span.setAllAttributes(businessContextTracer.createContext()
                .errorCode("ACCOUNT_SERVICE_DELETE_FAILED")
                .errorCategory("SERVICE_LAYER_ERROR")
                .toOtelAttributes());
            
            logger.error("Service: Failed to delete account with ID: {}", id, e);
            throw e;
        } finally {
            span.end();
        }
    }
}
