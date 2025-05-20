package com.example.loans.repository;

import com.example.loans.entity.Loan;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LoanRepository extends MongoRepository<Loan, String>  {
}
