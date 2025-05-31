package com.example.accounts.controller;

import com.example.accounts.entity.Account;
import com.example.accounts.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class AccountController {

    @Autowired
    private AccountService accountService;

    // Define your endpoints here
    // For example:
    @GetMapping("/getAll")
     public List<Account> getAllAccounts() {
         return accountService.getAllAccounts();
     }

     @PostMapping("/create")
     public Account createAccount(@RequestBody Account account) {
         return accountService.createAccount(account);
     }

     @GetMapping("/{id}")
     public ResponseEntity<Account> getAccountById(@PathVariable String id) {
         return accountService.getAccountById(id)
                 .map(ResponseEntity::ok)
                 .orElse(ResponseEntity.notFound().build());
     }

     @PutMapping("/{id}")
     public Account updateAccount(@PathVariable String id, @RequestBody Account account) {
         return accountService.updateAccount(id, account);
     }

     @DeleteMapping("/{id}")
     public void deleteAccount(@PathVariable String id) {
         accountService.deleteAccount(id);
     }
}
