package com.example.customer.controller;

import com.example.customer.entity.Customer;
import com.example.customer.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;

@RestController
public class CustomerController {

    private static final Logger logger = LoggerFactory.getLogger(CustomerController.class);

    @Autowired
    private CustomerService customerService;

    // Helper method to log request details
    private void logRequestDetails(HttpServletRequest request, String operation) {
        logger.info("Request: {} {} from IP: {} - Operation: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getRemoteAddr(),
                    operation);
    }

    @PostMapping("/create")
    public ResponseEntity<Customer> createCustomer(@Valid @RequestBody Customer customer, HttpServletRequest request) {
        logRequestDetails(request, "createCustomer");
        long startTime = System.currentTimeMillis();

        logger.info("Request received to create a new customer: {}", customer.toString());
        Customer createdCustomer = customerService.createCustomer(customer);

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Customer created successfully with ID: {} in {}ms", createdCustomer.getCustomerId(), duration);
        return new ResponseEntity<>(createdCustomer, HttpStatus.CREATED);
    }

    @GetMapping("/getAll")
    public ResponseEntity<List<Customer>> getAllCustomers(HttpServletRequest request) {
        logRequestDetails(request, "getAllCustomers");
        long startTime = System.currentTimeMillis();

        logger.info("Request received to fetch all customers");
        List<Customer> customers = customerService.getAllCustomers();

        long duration = System.currentTimeMillis() - startTime;
        logger.info("getAllCustomers completed in {}ms - Returning {} customers", duration, customers.size());
        return new ResponseEntity<>(customers, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getCustomerById(@PathVariable("id") String customerId, HttpServletRequest request) {
        logRequestDetails(request, "getCustomerById");
        long startTime = System.currentTimeMillis();

        logger.info("Request received to fetch customer with ID: {}", customerId);
        Optional<Customer> customer = customerService.getCustomerById(customerId);

        long duration = System.currentTimeMillis() - startTime;
        return customer.map(value -> {
                logger.info("Customer found with ID: {} in {}ms", customerId, duration);
                return new ResponseEntity<>(value, HttpStatus.OK);
            }).orElseGet(() -> {
                logger.warn("Customer not found with ID: {} after {}ms", customerId, duration);
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            });
    }

    @PutMapping("/{id}")
    public ResponseEntity<Customer> updateCustomer(@PathVariable("id") String customerId,
                                                 @Valid @RequestBody Customer customer,
                                                 HttpServletRequest request) {
        logRequestDetails(request, "updateCustomer");
        long startTime = System.currentTimeMillis();

        logger.info("Request received to update customer with ID: {}", customerId);
        customer.setCustomerId(customerId);
        Customer updatedCustomer = customerService.updateCustomer(customer);

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Customer updated successfully with ID: {} in {}ms", customerId, duration);
        return new ResponseEntity<>(updatedCustomer, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<HttpStatus> deleteCustomer(@PathVariable("id") String customerId, HttpServletRequest request) {
        logRequestDetails(request, "deleteCustomer");
        long startTime = System.currentTimeMillis();

        logger.info("Request received to delete customer with ID: {}", customerId);
        customerService.deleteCustomer(customerId);

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Customer deleted successfully with ID: {} in {}ms", customerId, duration);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
