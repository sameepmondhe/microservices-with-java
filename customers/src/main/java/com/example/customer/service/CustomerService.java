package com.example.customer.service;

import com.example.customer.entity.Customer;
import com.example.customer.repository.CustomerRepository;
import com.example.customers.tracing.BusinessContextTracer;
import io.opentelemetry.api.trace.Span;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {

    private static final Logger logger = LoggerFactory.getLogger(CustomerService.class);

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private BusinessContextTracer businessContextTracer;

    // Create a new customer
    public Customer createCustomer(Customer customer) {
        // Create child span for service layer operation
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("customer-service", "createCustomer")
                .customerId(customer.getCustomerId())
                .transactionType("CUSTOMER_CREATION");
        
        Span span = businessContextTracer.startChildSpan("customers.service.createCustomer", context);
        
        try (var scope = span.makeCurrent()) {
            long startTime = System.currentTimeMillis();
            logger.info("Service: Creating customer: {} with name: {}", 
                       customer.getCustomerId(), customer.getName());
            
            // Perform customer creation with repository call
            Customer createdCustomer = customerRepository.save(customer);
            
            // Add success business context with results
            span.setAllAttributes(businessContextTracer.createContext()
                .customerId(createdCustomer.getCustomerId())
                .toOtelAttributes());
            
            long duration = System.currentTimeMillis() - startTime;
            span.setAllAttributes(businessContextTracer.createContext()
                .processingTime(duration)
                .toOtelAttributes());
            
            logger.info("Service: Created customer {} in {}ms", createdCustomer.getCustomerId(), duration);
            return createdCustomer;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setAllAttributes(businessContextTracer.createContext()
                .errorCode("CUSTOMER_SERVICE_CREATE_FAILED")
                .errorCategory("SERVICE_LAYER_ERROR")
                .toOtelAttributes());
            
            logger.error("Service: Failed to create customer: {}", customer.getCustomerId(), e);
            throw e;
        } finally {
            span.end();
        }
    }

    // Get all customers
    public List<Customer> getAllCustomers() {
        // Create child span for service layer operation
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("customer-service", "getAllCustomers");
        
        Span span = businessContextTracer.startChildSpan("customers.service.getAllCustomers", context);
        
        try (var scope = span.makeCurrent()) {
            long startTime = System.currentTimeMillis();
            logger.info("Service: Fetching all customers");
            
            List<Customer> customers = customerRepository.findAll();
            
            // Add result context
            span.setAllAttributes(businessContextTracer.createContext()
                .batchSize(customers.size())
                .toOtelAttributes());
            
            long duration = System.currentTimeMillis() - startTime;
            span.setAllAttributes(businessContextTracer.createContext()
                .processingTime(duration)
                .toOtelAttributes());
            
            logger.info("Service: Retrieved {} customers in {}ms", customers.size(), duration);
            return customers;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setAllAttributes(businessContextTracer.createContext()
                .errorCode("CUSTOMER_SERVICE_FETCH_ALL_FAILED")
                .errorCategory("SERVICE_LAYER_ERROR")
                .toOtelAttributes());
            
            logger.error("Service: Failed to fetch all customers", e);
            throw e;
        } finally {
            span.end();
        }
    }

    // Get customer by ID
    public Optional<Customer> getCustomerById(String customerId) {
        // Create child span for service layer operation
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("customer-service", "getCustomerById")
                .customerId(customerId);
        
        Span span = businessContextTracer.startChildSpan("customers.service.getCustomerById", context);
        
        try (var scope = span.makeCurrent()) {
            long startTime = System.currentTimeMillis();
            logger.info("Service: Fetching customer with ID: {}", customerId);
            
            Optional<Customer> customer = customerRepository.findById(customerId);
            
            if (customer.isPresent()) {
                // Add success context
                span.setAllAttributes(businessContextTracer.createContext()
                    .customerId(customer.get().getCustomerId())
                    .toOtelAttributes());
                
                long duration = System.currentTimeMillis() - startTime;
                span.setAllAttributes(businessContextTracer.createContext()
                    .processingTime(duration)
                    .toOtelAttributes());
                
                logger.info("Service: Found customer {} in {}ms", customerId, duration);
            } else {
                // Add not found context
                span.setAllAttributes(businessContextTracer.createContext()
                    .errorCode("CUSTOMER_NOT_FOUND")
                    .errorCategory("BUSINESS_LOGIC_ERROR")
                    .toOtelAttributes());
                
                logger.warn("Service: Customer with ID {} not found", customerId);
            }
            
            return customer;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setAllAttributes(businessContextTracer.createContext()
                .errorCode("CUSTOMER_SERVICE_FETCH_BY_ID_FAILED")
                .errorCategory("SERVICE_LAYER_ERROR")
                .toOtelAttributes());
            
            logger.error("Service: Failed to fetch customer with ID: {}", customerId, e);
            throw e;
        } finally {
            span.end();
        }
    }

    // Update customer
    public Customer updateCustomer(Customer customer) {
        // Create child span for service layer operation
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("customer-service", "updateCustomer")
                .customerId(customer.getCustomerId())
                .transactionType("UPDATE");
        
        Span span = businessContextTracer.startChildSpan("customers.service.updateCustomer", context);
        
        try (var scope = span.makeCurrent()) {
            long startTime = System.currentTimeMillis();
            logger.info("Service: Updating customer with ID: {}", customer.getCustomerId());
            
            // Check if customer exists first
            Optional<Customer> existingCustomer = customerRepository.findById(customer.getCustomerId());
            
            if (existingCustomer.isPresent()) {
                // Update existing customer
                Customer updatedCustomer = customerRepository.save(customer);
                
                // Add success context
                span.setAllAttributes(businessContextTracer.createContext()
                    .customerId(updatedCustomer.getCustomerId())
                    .toOtelAttributes());
                
                long duration = System.currentTimeMillis() - startTime;
                span.setAllAttributes(businessContextTracer.createContext()
                    .processingTime(duration)
                    .toOtelAttributes());
                
                logger.info("Service: Updated customer {} in {}ms", customer.getCustomerId(), duration);
                return updatedCustomer;
            } else {
                // Add not found context
                span.setAllAttributes(businessContextTracer.createContext()
                    .errorCode("CUSTOMER_NOT_FOUND")
                    .errorCategory("BUSINESS_LOGIC_ERROR")
                    .toOtelAttributes());
                
                logger.warn("Service: Cannot update - Customer with ID {} not found", customer.getCustomerId());
                throw new RuntimeException("Customer not found with ID: " + customer.getCustomerId());
            }
            
        } catch (Exception e) {
            span.recordException(e);
            span.setAllAttributes(businessContextTracer.createContext()
                .errorCode("CUSTOMER_SERVICE_UPDATE_FAILED")
                .errorCategory("SERVICE_LAYER_ERROR")
                .toOtelAttributes());
            
            logger.error("Service: Failed to update customer with ID: {}", customer.getCustomerId(), e);
            throw e;
        } finally {
            span.end();
        }
    }

    // Delete customer
    public void deleteCustomer(String customerId) {
        // Create child span for service layer operation
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("customer-service", "deleteCustomer")
                .customerId(customerId)
                .transactionType("DELETE");
        
        Span span = businessContextTracer.startChildSpan("customers.service.deleteCustomer", context);
        
        try (var scope = span.makeCurrent()) {
            long startTime = System.currentTimeMillis();
            logger.info("Service: Deleting customer with ID: {}", customerId);
            
            // Check if customer exists first to add business context
            Optional<Customer> existingCustomer = customerRepository.findById(customerId);
            
            if (existingCustomer.isPresent()) {
                // Add customer context before deletion
                span.setAllAttributes(businessContextTracer.createContext()
                    .customerId(existingCustomer.get().getCustomerId())
                    .toOtelAttributes());
                
                customerRepository.deleteById(customerId);
                
                long duration = System.currentTimeMillis() - startTime;
                span.setAllAttributes(businessContextTracer.createContext()
                    .processingTime(duration)
                    .toOtelAttributes());
                
                logger.info("Service: Deleted customer {} in {}ms", customerId, duration);
            } else {
                // Add not found context
                span.setAllAttributes(businessContextTracer.createContext()
                    .errorCode("CUSTOMER_NOT_FOUND")
                    .errorCategory("BUSINESS_LOGIC_ERROR")
                    .toOtelAttributes());
                
                logger.warn("Service: Cannot delete - Customer with ID {} not found", customerId);
                throw new RuntimeException("Customer not found with ID: " + customerId);
            }
            
        } catch (Exception e) {
            span.recordException(e);
            span.setAllAttributes(businessContextTracer.createContext()
                .errorCode("CUSTOMER_SERVICE_DELETE_FAILED")
                .errorCategory("SERVICE_LAYER_ERROR")
                .toOtelAttributes());
            
            logger.error("Service: Failed to delete customer with ID: {}", customerId, e);
            throw e;
        } finally {
            span.end();
        }
    }
}
