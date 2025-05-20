package com.example.loans.service;

import com.example.loans.entity.Loan;
import com.example.loans.repository.LoanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LoanService {

    @Autowired
    private LoanRepository loanRepository;

    // Create
    public Loan createLoan(Loan loan) {
        return loanRepository.save(loan);
    }

    // Read all
    public List<Loan> getAllLoans() {
        return loanRepository.findAll();
    }

    // Read by ID
    public Optional<Loan> getLoanById(String loanId) {
        return loanRepository.findById(loanId);
    }

    // Update
    public Loan updateLoan(String loanId, Loan updatedLoan) {
        return loanRepository.findById(loanId)
                .map(loan -> {
                    loan.setLoanType(updatedLoan.getLoanType());
                    loan.setLoanAmount(updatedLoan.getLoanAmount());
                    loan.setLoanStatus(updatedLoan.getLoanStatus());
                    loan.setLoanStartDate(updatedLoan.getLoanStartDate());
                    loan.setLoanEndDate(updatedLoan.getLoanEndDate());
                    loan.setLoanInterestRate(updatedLoan.getLoanInterestRate());
                    return loanRepository.save(loan);
                })
                .orElseGet(() -> {
                    updatedLoan.setLoanId(loanId);
                    return loanRepository.save(updatedLoan);
                });
    }

    // Delete
    public void deleteLoan(String loanId) {
        loanRepository.deleteById(loanId);
    }
}
