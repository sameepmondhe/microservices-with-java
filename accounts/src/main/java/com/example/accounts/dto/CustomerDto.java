package com.example.accounts.dto;

import lombok.Data;

@Data
public class CustomerDto {
    private String customerId;
    private String name;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private String status;
}