package com.example.accounts.service;

import com.example.accounts.entity.Account;
import com.example.accounts.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

    // Create
    public Account createAccount(Account account) {
        return accountRepository.save(account);
    }

    // Read all
    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    // Read by ID
    public Optional<Account> getAccountById(String accountId) {
        return accountRepository.findById(accountId);
    }

    // Update
    public Account updateAccount(String accountId, Account updatedAccount) {
        return accountRepository.findById(accountId)
                .map(account -> {
                    account.setAccountName(updatedAccount.getAccountName());
                    account.setAccountType(updatedAccount.getAccountType());
                    account.setAccountStatus(updatedAccount.getAccountStatus());
                    account.setAccountBalance(updatedAccount.getAccountBalance());
                    account.setAccountCurrency(updatedAccount.getAccountCurrency());
                    account.setAccountCreatedDate(updatedAccount.getAccountCreatedDate());
                    return accountRepository.save(account);
                })
                .orElseGet(() -> {
                    updatedAccount.setAccountId(accountId);
                    return accountRepository.save(updatedAccount);
                });
    }

    // Delete
    public void deleteAccount(String accountId) {
        accountRepository.deleteById(accountId);
    }
}
