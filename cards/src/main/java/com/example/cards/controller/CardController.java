package com.example.cards.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.example.cards.entity.Card;
import com.example.cards.service.CardService;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class CardController {

    private static final Logger logger = LoggerFactory.getLogger(CardController.class);

    @Autowired
    private CardService cardService;

    // Helper method to log request details
    private void logRequestDetails(HttpServletRequest request, String operation) {
        logger.info("Request: {} {} from IP: {} - Operation: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getRemoteAddr(),
                    operation);
    }

    // Create
    @PostMapping("/create")
    public Card createCard(@RequestBody Card card, HttpServletRequest request) {
        logRequestDetails(request, "createCard");
        long startTime = System.currentTimeMillis();

        logger.info("Request received to create a new card for customer: {} and account: {}", 
                   card.getCustomerId(), card.getAccountId());
        
        // Auto-generate card ID if not provided
        if (card.getCardId() == null || card.getCardId().isEmpty()) {
            card.setCardId("CARD-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        }
        
        // Auto-generate card number for demo
        if (card.getCardNumber() == null || card.getCardNumber().isEmpty()) {
            card.setCardNumber("****-****-****-" + String.format("%04d", (int)(Math.random() * 10000)));
        }
        
        Card createdCard = cardService.createCard(card);

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Card created successfully with ID: {} for customer: {} in {}ms", 
                   createdCard.getCardId(), card.getCustomerId(), duration);
        return createdCard;
    }

    // Read all
    @GetMapping("/getAll")
    public List<Card> getAllCards(HttpServletRequest request) {
        logRequestDetails(request, "getAllCards");
        long startTime = System.currentTimeMillis();

        logger.info("Request received to fetch all cards");
        List<Card> cards = cardService.getAllCards();

        long duration = System.currentTimeMillis() - startTime;
        logger.info("getAllCards completed in {}ms - Returning {} cards", duration, cards.size());
        return cards;
    }

    // Read by ID
    @GetMapping("/{id}")
    public Optional<Card> getCardById(@PathVariable("id") String cardId, HttpServletRequest request) {
        logRequestDetails(request, "getCardById");
        long startTime = System.currentTimeMillis();

        logger.info("Request received to fetch card with ID: {}", cardId);
        Optional<Card> card = cardService.getCardById(cardId);

        long duration = System.currentTimeMillis() - startTime;
        if (card.isPresent()) {
            logger.info("Card found with ID: {} in {}ms", cardId, duration);
        } else {
            logger.warn("Card not found with ID: {} after {}ms", cardId, duration);
        }

        return card;
    }

    // Update
    @PutMapping("/{id}")
    public Card updateCard(@PathVariable("id") String cardId, @RequestBody Card card, HttpServletRequest request) {
        logRequestDetails(request, "updateCard");
        long startTime = System.currentTimeMillis();

        logger.info("Request received to update card with ID: {}", cardId);
        Card updatedCard = cardService.updateCard(cardId, card);

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Card updated successfully with ID: {} in {}ms", cardId, duration);
        return updatedCard;
    }

    // Delete
    @DeleteMapping("/{id}")
    public void deleteCard(@PathVariable("id") String cardId, HttpServletRequest request) {
        logRequestDetails(request, "deleteCard");
        long startTime = System.currentTimeMillis();

        logger.info("Request received to delete card with ID: {}", cardId);
        cardService.deleteCard(cardId);

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Card deleted successfully with ID: {} in {}ms", cardId, duration);
    }
}
