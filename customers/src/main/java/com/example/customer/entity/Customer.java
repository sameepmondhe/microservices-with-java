package com.example.customer.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;

@Data
@Document(collection = "customer")
public class Customer {

    @Id
    @NotNull(message = "Customer ID cannot be null")
    private String customerId;

    @NotNull(message = "Customer name cannot be null")
    private String name;

    @Email(message = "Please provide a valid email address")
    private String email;

    private String phone;
    private String address;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private String createdDate;
    private String status;
}
