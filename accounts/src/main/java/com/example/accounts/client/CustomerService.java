package com.example.accounts.client;

import com.example.accounts.dto.CustomerDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

@Service
public class CustomerService {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomerService.class);
    private static final String CUSTOMER_SERVICE_URL = "http://host.docker.internal:8084";
    
    @Autowired
    private RestTemplate restTemplate;
    
    public CustomerDto getCustomerById(String customerId) {
        try {
            logger.info("Calling customer service to fetch customer: {}", customerId);
            String url = CUSTOMER_SERVICE_URL + "/" + customerId;
            
            CustomerDto customer = restTemplate.getForObject(url, CustomerDto.class);
            
            if (customer != null) {
                logger.info("Successfully retrieved customer: {} from customer service", customerId);
                return customer;
            } else {
                logger.warn("Customer not found: {}", customerId);
                return null;
            }
            
        } catch (RestClientException e) {
            logger.error("Failed to call customer service for customer: {}. Error: {}", customerId, e.getMessage());
            throw new RuntimeException("Customer service unavailable", e);
        }
    }
}