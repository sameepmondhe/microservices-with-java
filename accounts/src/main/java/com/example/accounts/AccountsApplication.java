package com.example.accounts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class AccountsApplication {

    private static final Logger logger = LoggerFactory.getLogger(AccountsApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AccountsApplication.class, args);
    }

    @EventListener(ApplicationStartedEvent.class)
    public void logApplicationStarted() {
        logger.info("Accounts Service started successfully");
        logger.debug("Accounts Service debug logging enabled");
    }
}
