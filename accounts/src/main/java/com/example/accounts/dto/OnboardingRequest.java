package com.example.accounts.dto;

import lombok.Data;

@Data
public class OnboardingRequest {
    private String customerId;
    private String initialDeposit;
    private String accountType;
    private boolean requestCreditCard;
    private boolean checkLoanEligibility;
}