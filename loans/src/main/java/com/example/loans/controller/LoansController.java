package com.example.loans.controller;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/loans")
public class LoansController {

    @GetMapping("/getAll")
    public List<String> getAllLoans() {
        // Replace with actual logic
        return List.of("Loan1", "Loan2");
    }

    @GetMapping("/{id}")
    public String getLoanById(@PathVariable String id) {
        // Replace with actual logic
        return "Loan with id: " + id;
    }

    @PostMapping("/create")
    public String createLoan(@RequestBody String loan) {
        // Replace with actual logic
        return "Created loan: " + loan;
    }

    @PutMapping("/{id}")
    public String updateLoan(@PathVariable String id, @RequestBody String loan) {
        // Replace with actual logic
        return "Updated loan with id: " + id;
    }

    @DeleteMapping("/{id}")
    public String deleteLoan(@PathVariable String id) {
        // Replace with actual logic
        return "Deleted loan with id: " + id;
    }
}
