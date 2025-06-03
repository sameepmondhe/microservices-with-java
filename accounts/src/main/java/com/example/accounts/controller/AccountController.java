package com.example.accounts.controller;

import com.example.accounts.entity.Account;
import com.example.accounts.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class AccountController {

    private static final Logger logger = LoggerFactory.getLogger(AccountController.class);

    @Autowired
    private AccountService accountService;

    // Helper method to log request details
    private void logRequestDetails(HttpServletRequest request, String operation) {
        logger.info("Request: {} {} from IP: {} - Operation: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getRemoteAddr(),
                    operation);
    }

    @GetMapping("/getAll")
    public List<Account> getAllAccounts(HttpServletRequest request) {
        logRequestDetails(request, "getAllAccounts");
        long startTime = System.currentTimeMillis();

        logger.info("Request received to fetch all accounts");
        List<Account> accounts = accountService.getAllAccounts();

        long duration = System.currentTimeMillis() - startTime;
        logger.info("getAllAccounts completed in {}ms - Returning {} accounts", duration, accounts.size());
        return accounts;
    }

    @PostMapping("/create")
    public Account createAccount(@RequestBody Account account, HttpServletRequest request) {
        logRequestDetails(request, "createAccount");
        long startTime = System.currentTimeMillis();

        logger.info("Request received to create a new account: {}", Map.of(
            "accountDetails", account.toString()
        ));

        Account createdAccount = accountService.createAccount(account);

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Account created in {}ms", duration);
        return createdAccount;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Account> getAccountById(@PathVariable String id, HttpServletRequest request) {
        logRequestDetails(request, "getAccountById");
        long startTime = System.currentTimeMillis();

        logger.info("Request received to fetch account with ID: {}", id);
        return accountService.getAccountById(id)
                .map(account -> {
                    long duration = System.currentTimeMillis() - startTime;
                    logger.info("Account found with ID: {} in {}ms", id, duration);
                    return ResponseEntity.ok(account);
                })
                .orElseGet(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    logger.warn("Account not found with ID: {} after {}ms", id, duration);
                    return ResponseEntity.notFound().build();
                });
    }

    @PutMapping("/{id}")
    public Account updateAccount(@PathVariable String id, @RequestBody Account account, HttpServletRequest request) {
        logRequestDetails(request, "updateAccount");
        long startTime = System.currentTimeMillis();

        logger.info("Request received to update account with ID: {}", id);
        Account updatedAccount = accountService.updateAccount(id, account);

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Account updated successfully with ID: {} in {}ms", id, duration);
        return updatedAccount;
    }

    @DeleteMapping("/{id}")
    public void deleteAccount(@PathVariable String id, HttpServletRequest request) {
        logRequestDetails(request, "deleteAccount");
        long startTime = System.currentTimeMillis();

        logger.info("Request received to delete account with ID: {}", id);
        accountService.deleteAccount(id);

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Account deleted successfully with ID: {} in {}ms", id, duration);
    }
}
