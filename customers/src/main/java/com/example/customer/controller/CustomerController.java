package com.example.customer.controller;

import com.example.customer.entity.Customer;
import com.example.customer.service.CustomerService;
import com.example.customers.tracing.BusinessContextTracer;
import io.opentelemetry.api.trace.Span;
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
import java.util.UUID;

@RestController
public class CustomerController {

    private static final Logger logger = LoggerFactory.getLogger(CustomerController.class);

    @Autowired
    private CustomerService customerService;

    @Autowired
    private BusinessContextTracer businessContextTracer;

    // Helper method to start business span with request context
    private Span startRequestSpan(HttpServletRequest request, String operation) {
        String correlationId = UUID.randomUUID().toString();
        
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("customers-service", operation)
                .transactionId(correlationId);
        
        Span span = businessContextTracer.startBusinessSpan("customers." + operation, context);
        
        // Add HTTP context as span attributes
        span.setAttribute("http.method", request.getMethod());
        span.setAttribute("http.url", request.getRequestURI());
        span.setAttribute("http.client_ip", request.getRemoteAddr());
        span.setAttribute("http.user_agent", request.getHeader("User-Agent") != null ? request.getHeader("User-Agent") : "");
        span.setAttribute("correlation.id", correlationId);
        
        logger.info("Processing request: {} {} - Operation: {}", 
                    request.getMethod(), 
                    request.getRequestURI(), 
                    operation);
        
        return span;
    }

    @PostMapping("/create")
    public ResponseEntity<Customer> createCustomer(@Valid @RequestBody Customer customer, HttpServletRequest request) {
        Span span = startRequestSpan(request, "createCustomer");
        long startTime = System.currentTimeMillis();

        try {
            logger.info("Request received to create a new customer: {}", customer.toString());
            
            // Add business context before creating customer
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext()
                    .customerId(customer.getCustomerId())
                    .customerType(customer.getName() != null ? "INDIVIDUAL" : "UNKNOWN")
                    .serviceCall("customers-service", "createCustomer")
            );
            
            Customer createdCustomer = customerService.createCustomer(customer);

            // Add business context after successful creation
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext()
                    .customerId(createdCustomer.getCustomerId())
            );

            long duration = System.currentTimeMillis() - startTime;
            
            // Add performance context
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext().processingTime(duration)
            );
            
            logger.info("Customer created successfully with ID: {} in {}ms", createdCustomer.getCustomerId(), duration);
            return new ResponseEntity<>(createdCustomer, HttpStatus.CREATED);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    @GetMapping("/getAll")
    public ResponseEntity<List<Customer>> getAllCustomers(HttpServletRequest request) {
        Span span = startRequestSpan(request, "getAllCustomers");
        long startTime = System.currentTimeMillis();

        try {
            logger.info("Request received to fetch all customers");
            List<Customer> customers = customerService.getAllCustomers();

            // Add business context
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext()
                    .batchSize(customers.size())
                    .serviceCall("customers-service", "getAllCustomers")
            );

            long duration = System.currentTimeMillis() - startTime;
            
            // Add performance context
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext().processingTime(duration)
            );
            
            logger.info("getAllCustomers completed in {}ms - Returning {} customers", duration, customers.size());
            return new ResponseEntity<>(customers, HttpStatus.OK);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getCustomerById(@PathVariable("id") String customerId, HttpServletRequest request) {
        Span span = startRequestSpan(request, "getCustomerById");
        long startTime = System.currentTimeMillis();

        try {
            logger.info("Request received to fetch customer with ID: {}", customerId);
            
            // Add business context
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext()
                    .customerId(customerId)
                    .serviceCall("customers-service", "getCustomerById")
            );
            
            Optional<Customer> customer = customerService.getCustomerById(customerId);

            long duration = System.currentTimeMillis() - startTime;
            
            // Add performance context
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext().processingTime(duration)
            );
            
            return customer.map(value -> {
                    // Add additional business context for found customer
                    businessContextTracer.addBusinessAttributes(
                        businessContextTracer.createContext()
                            .customerType(value.getName() != null ? "INDIVIDUAL" : "UNKNOWN")
                    );
                    logger.info("Customer found with ID: {} in {}ms", customerId, duration);
                    return new ResponseEntity<>(value, HttpStatus.OK);
                }).orElseGet(() -> {
                    logger.warn("Customer not found with ID: {} after {}ms", customerId, duration);
                    span.addEvent("customer.not.found");
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                });
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Customer> updateCustomer(@PathVariable("id") String customerId,
                                                 @Valid @RequestBody Customer customer,
                                                 HttpServletRequest request) {
        Span span = startRequestSpan(request, "updateCustomer");
        long startTime = System.currentTimeMillis();

        try {
            logger.info("Request received to update customer with ID: {}", customerId);
            
            // Add business context
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext()
                    .customerId(customerId)
                    .customerType(customer.getName() != null ? "INDIVIDUAL" : "UNKNOWN")
                    .serviceCall("customers-service", "updateCustomer")
            );
            
            customer.setCustomerId(customerId);
            Customer updatedCustomer = customerService.updateCustomer(customer);

            long duration = System.currentTimeMillis() - startTime;
            
            // Add performance context
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext().processingTime(duration)
            );
            
            logger.info("Customer updated successfully with ID: {} in {}ms", customerId, duration);
            return new ResponseEntity<>(updatedCustomer, HttpStatus.OK);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<HttpStatus> deleteCustomer(@PathVariable("id") String customerId, HttpServletRequest request) {
        Span span = startRequestSpan(request, "deleteCustomer");
        long startTime = System.currentTimeMillis();

        try {
            logger.info("Request received to delete customer with ID: {}", customerId);
            
            // Add business context
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext()
                    .customerId(customerId)
                    .serviceCall("customers-service", "deleteCustomer")
            );
            
            customerService.deleteCustomer(customerId);

            long duration = System.currentTimeMillis() - startTime;
            
            // Add performance context
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext().processingTime(duration)
            );
            
            logger.info("Customer deleted successfully with ID: {} in {}ms", customerId, duration);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
}
