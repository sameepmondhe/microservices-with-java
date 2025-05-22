package com.example.customer.repository;

import com.example.customer.entity.Customer;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CustomerRepository extends MongoRepository<Customer, String> {
    // You can add custom query methods here if needed
}
