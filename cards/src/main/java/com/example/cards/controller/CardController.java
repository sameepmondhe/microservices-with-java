package com.example.cards.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.example.cards.entity.Card;
import com.example.cards.service.CardService;
import com.example.cards.tracing.BusinessContextTracer;
import io.opentelemetry.api.trace.Span;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class CardController {

    private static final Logger logger = LoggerFactory.getLogger(CardController.class);

    @Autowired
    private CardService cardService;

    @Autowired
    private BusinessContextTracer businessContextTracer;

    // Helper method to start business span with request context
    private Span startRequestSpan(HttpServletRequest request, String operation) {
        String correlationId = UUID.randomUUID().toString();
        
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("cards-service", operation)
                .transactionId(correlationId);
        
        Span span = businessContextTracer.startBusinessSpan("cards." + operation, context);
        
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

    // Create
    @PostMapping("/create")
    public Card createCard(@RequestBody Card card, HttpServletRequest request) {
        Span span = startRequestSpan(request, "createCard");
        long startTime = System.currentTimeMillis();

        try {
            logger.info("Request received to create a new card for customer: {} and account: {}", 
                       card.getCustomerId(), card.getAccountId());
            
            // Add business context before creating card
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext()
                    .customerId(card.getCustomerId())
                    .cardType(card.getCardType())
                    .serviceCall("cards-service", "createCard")
            );
            
            // Auto-generate card ID if not provided
            if (card.getCardId() == null || card.getCardId().isEmpty()) {
                card.setCardId("CARD-" + UUID.randomUUID().toString().substring(0, 8));
            }
            
            // Auto-generate card number for demo
            if (card.getCardNumber() == null || card.getCardNumber().isEmpty()) {
                card.setCardNumber("****-****-****-" + String.format("%04d", (int)(Math.random() * 10000)));
            }
            
            Card createdCard = cardService.createCard(card);

            // Add business context after successful creation
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext()
                    .cardId(createdCard.getCardId())
                    .cardType(createdCard.getCardType())
            );

            long duration = System.currentTimeMillis() - startTime;
            
            // Add performance context
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext().processingTime(duration)
            );
            
            logger.info("Card created successfully with ID: {} for customer: {} in {}ms", 
                       createdCard.getCardId(), card.getCustomerId(), duration);
            return createdCard;
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    // Read all
    @GetMapping("/getAll")
    public List<Card> getAllCards(HttpServletRequest request) {
        Span span = startRequestSpan(request, "getAllCards");
        long startTime = System.currentTimeMillis();

        try {
            logger.info("Request received to fetch all cards");
            List<Card> cards = cardService.getAllCards();

            // Add business context
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext()
                    .batchSize(cards.size())
                    .serviceCall("cards-service", "getAllCards")
            );

            long duration = System.currentTimeMillis() - startTime;
            
            // Add performance context
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext().processingTime(duration)
            );
            
            logger.info("getAllCards completed in {}ms - Returning {} cards", duration, cards.size());
            return cards;
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    // Read by ID
    @GetMapping("/{id}")
    public Optional<Card> getCardById(@PathVariable("id") String cardId, HttpServletRequest request) {
        Span span = startRequestSpan(request, "getCardById");
        long startTime = System.currentTimeMillis();

        try {
            logger.info("Request received to fetch card with ID: {}", cardId);
            
            // Add business context
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext()
                    .cardId(cardId)
                    .serviceCall("cards-service", "getCardById")
            );
            
            Optional<Card> card = cardService.getCardById(cardId);

            long duration = System.currentTimeMillis() - startTime;
            
            // Add performance context
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext().processingTime(duration)
            );
            
            if (card.isPresent()) {
                // Add additional business context for found card
                businessContextTracer.addBusinessAttributes(
                    businessContextTracer.createContext()
                        .customerId(card.get().getCustomerId())
                        .cardType(card.get().getCardType())
                        .cardStatus(card.get().getCardStatus())
                );
                logger.info("Card found with ID: {} in {}ms", cardId, duration);
            } else {
                logger.warn("Card not found with ID: {} after {}ms", cardId, duration);
                span.addEvent("card.not.found");
            }

            return card;
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    // Update
    @PutMapping("/{id}")
    public Card updateCard(@PathVariable("id") String cardId, @RequestBody Card card, HttpServletRequest request) {
        Span span = startRequestSpan(request, "updateCard");
        long startTime = System.currentTimeMillis();

        try {
            logger.info("Request received to update card with ID: {}", cardId);
            
            // Add business context
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext()
                    .cardId(cardId)
                    .customerId(card.getCustomerId())
                    .cardType(card.getCardType())
                    .serviceCall("cards-service", "updateCard")
            );
            
            Card updatedCard = cardService.updateCard(cardId, card);

            // Add business context after successful update
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext()
                    .cardStatus(updatedCard.getCardStatus())
            );

            long duration = System.currentTimeMillis() - startTime;
            
            // Add performance context
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext().processingTime(duration)
            );
            
            logger.info("Card updated successfully with ID: {} in {}ms", cardId, duration);
            return updatedCard;
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    // Delete
    @DeleteMapping("/{id}")
    public void deleteCard(@PathVariable("id") String cardId, HttpServletRequest request) {
        Span span = startRequestSpan(request, "deleteCard");
        long startTime = System.currentTimeMillis();

        try {
            logger.info("Request received to delete card with ID: {}", cardId);
            
            // Add business context
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext()
                    .cardId(cardId)
                    .serviceCall("cards-service", "deleteCard")
            );
            
            cardService.deleteCard(cardId);

            long duration = System.currentTimeMillis() - startTime;
            
            // Add performance context
            businessContextTracer.addBusinessAttributes(
                businessContextTracer.createContext().processingTime(duration)
            );
            
            logger.info("Card deleted successfully with ID: {} in {}ms", cardId, duration);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
}
