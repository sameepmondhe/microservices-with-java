package com.example.accounts.dto;

import lombok.Data;
import java.util.List;

@Data
public class OnboardingResponse {
    private String customerId;
    private String accountId;
    private String cardId;
    private boolean loanEligible;
    private String status;
    private List<String> errors;
    private String processingTime;
}