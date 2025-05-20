package com.example.loans.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collation = "loan")
public class Loan {
    @Id
    private String loanId;
    private String loanType;
    private String loanAmount;
    private String loanStatus;
    private String loanStartDate;
    private String loanEndDate;
    private String loanInterestRate;
}
