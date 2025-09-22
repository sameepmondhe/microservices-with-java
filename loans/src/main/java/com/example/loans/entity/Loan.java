package com.example.loans.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "loan")
public class Loan {
    @Id
    private String loanId;
    
    private String customerId;       // Link to customer
    private String accountId;        // Link to account
    
    private String loanType;
    private String loanAmount;
    private String loanStatus;
    private String loanStartDate;
    private String loanEndDate;
    private String loanInterestRate;
}
