package com.example.accounts.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotNull;


import java.lang.annotation.Documented;

@Data
@Document(collection = "account")
public class Account {

    @Id
    @NotNull(message = "Account ID cannot be null")
    private String accountId;
    private String accountName;
    private String accountType;
    private String accountStatus;
    private String accountBalance;
    private String accountCurrency;
    private String accountCreatedDate;
}
