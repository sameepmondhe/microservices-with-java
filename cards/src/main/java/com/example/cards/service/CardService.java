package com.example.cards.service;

import com.example.cards.entity.Card;
import com.example.cards.repository.CardRepository;
import com.example.cards.tracing.BusinessContextTracer;
import io.opentelemetry.api.trace.Span;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@Service
public class CardService {

    private static final Logger logger = LoggerFactory.getLogger(CardService.class);

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private BusinessContextTracer businessContextTracer;

    // Create
    public Card createCard(Card card) {
        // Create child span for service layer operation
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("card-service", "createCard")
                .cardType(card.getCardType())
                .customerId(card.getCustomerId())
                .transactionType("CARD_CREATION");
        
        Span span = businessContextTracer.startChildSpan("cards.service.createCard", context);
        
        try (var scope = span.makeCurrent()) {
            long startTime = System.currentTimeMillis();
            logger.info("Service: Creating card for customer: {} with type: {}", 
                       card.getCustomerId(), card.getCardType());
            
            // Perform card creation with repository call
            Card createdCard = cardRepository.save(card);
            
            // Add success business context with results
            span.setAllAttributes(businessContextTracer.createContext()
                .cardId(createdCard.getCardId())
                .cardType(createdCard.getCardType())
                .toOtelAttributes());
            
            long duration = System.currentTimeMillis() - startTime;
            span.setAllAttributes(businessContextTracer.createContext()
                .processingTime(duration)
                .toOtelAttributes());
            
            logger.info("Service: Created card {} in {}ms", createdCard.getCardId(), duration);
            return createdCard;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setAllAttributes(businessContextTracer.createContext()
                .errorCode("CARD_SERVICE_CREATE_FAILED")
                .errorCategory("SERVICE_LAYER_ERROR")
                .toOtelAttributes());
            
            logger.error("Service: Failed to create card for customer: {}", card.getCustomerId(), e);
            throw e;
        } finally {
            span.end();
        }
    }

    // Read all
    public List<Card> getAllCards() {
        // Create child span for service layer operation
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("card-service", "getAllCards");
        
        Span span = businessContextTracer.startChildSpan("cards.service.getAllCards", context);
        
        try (var scope = span.makeCurrent()) {
            long startTime = System.currentTimeMillis();
            logger.info("Service: Fetching all cards");
            
            List<Card> cards = cardRepository.findAll();
            
            // Add result context
            span.setAllAttributes(businessContextTracer.createContext()
                .batchSize(cards.size())
                .toOtelAttributes());
            
            long duration = System.currentTimeMillis() - startTime;
            span.setAllAttributes(businessContextTracer.createContext()
                .processingTime(duration)
                .toOtelAttributes());
            
            logger.info("Service: Retrieved {} cards in {}ms", cards.size(), duration);
            return cards;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setAllAttributes(businessContextTracer.createContext()
                .errorCode("CARD_SERVICE_FETCH_ALL_FAILED")
                .errorCategory("SERVICE_LAYER_ERROR")
                .toOtelAttributes());
            
            logger.error("Service: Failed to fetch all cards", e);
            throw e;
        } finally {
            span.end();
        }
    }

    // Read by ID
    public Optional<Card> getCardById(String cardId) {
        // Create child span for service layer operation
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("card-service", "getCardById")
                .cardId(cardId);
        
        Span span = businessContextTracer.startChildSpan("cards.service.getCardById", context);
        
        try (var scope = span.makeCurrent()) {
            long startTime = System.currentTimeMillis();
            logger.info("Service: Fetching card with ID: {}", cardId);
            
            Optional<Card> card = cardRepository.findById(cardId);
            
            if (card.isPresent()) {
                // Add success context
                span.setAllAttributes(businessContextTracer.createContext()
                    .cardType(card.get().getCardType())
                    .customerId(card.get().getCustomerId())
                    .toOtelAttributes());
                
                long duration = System.currentTimeMillis() - startTime;
                span.setAllAttributes(businessContextTracer.createContext()
                    .processingTime(duration)
                    .toOtelAttributes());
                
                logger.info("Service: Found card {} in {}ms", cardId, duration);
            } else {
                // Add not found context
                span.setAllAttributes(businessContextTracer.createContext()
                    .errorCode("CARD_NOT_FOUND")
                    .errorCategory("BUSINESS_LOGIC_ERROR")
                    .toOtelAttributes());
                
                logger.warn("Service: Card with ID {} not found", cardId);
            }
            
            return card;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setAllAttributes(businessContextTracer.createContext()
                .errorCode("CARD_SERVICE_FETCH_BY_ID_FAILED")
                .errorCategory("SERVICE_LAYER_ERROR")
                .toOtelAttributes());
            
            logger.error("Service: Failed to fetch card with ID: {}", cardId, e);
            throw e;
        } finally {
            span.end();
        }
    }

    // Update
    public Card updateCard(String cardId, Card updatedCard) {
        // Create child span for service layer operation
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("card-service", "updateCard")
                .cardId(cardId)
                .cardType(updatedCard.getCardType())
                .customerId(updatedCard.getCustomerId())
                .transactionType("UPDATE");
        
        Span span = businessContextTracer.startChildSpan("cards.service.updateCard", context);
        
        try (var scope = span.makeCurrent()) {
            long startTime = System.currentTimeMillis();
            logger.info("Service: Updating card with ID: {}", cardId);
            
            Card resultCard = cardRepository.findById(cardId)
                    .map(card -> {
                        // Update existing card
                        card.setCardNumber(updatedCard.getCardNumber());
                        card.setCardType(updatedCard.getCardType());
                        card.setCardStatus(updatedCard.getCardStatus());
                        card.setCardHolderName(updatedCard.getCardHolderName());
                        card.setCardExpiry(updatedCard.getCardExpiry());
                        // ...add other fields as needed...
                        return cardRepository.save(card);
                    })
                    .orElseGet(() -> {
                        // Create new card if not found
                        updatedCard.setCardId(cardId);
                        return cardRepository.save(updatedCard);
                    });
            
            // Add success context
            span.setAllAttributes(businessContextTracer.createContext()
                .cardType(resultCard.getCardType())
                .customerId(resultCard.getCustomerId())
                .toOtelAttributes());
            
            long duration = System.currentTimeMillis() - startTime;
            span.setAllAttributes(businessContextTracer.createContext()
                .processingTime(duration)
                .toOtelAttributes());
            
            logger.info("Service: Updated card {} in {}ms", cardId, duration);
            return resultCard;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setAllAttributes(businessContextTracer.createContext()
                .errorCode("CARD_SERVICE_UPDATE_FAILED")
                .errorCategory("SERVICE_LAYER_ERROR")
                .toOtelAttributes());
            
            logger.error("Service: Failed to update card with ID: {}", cardId, e);
            throw e;
        } finally {
            span.end();
        }
    }

    // Delete
    public void deleteCard(String cardId) {
        // Create child span for service layer operation
        BusinessContextTracer.BusinessContext context = businessContextTracer.createContext()
                .serviceCall("card-service", "deleteCard")
                .cardId(cardId)
                .transactionType("DELETE");
        
        Span span = businessContextTracer.startChildSpan("cards.service.deleteCard", context);
        
        try (var scope = span.makeCurrent()) {
            long startTime = System.currentTimeMillis();
            logger.info("Service: Deleting card with ID: {}", cardId);
            
            // Check if card exists first to add business context
            Optional<Card> existingCard = cardRepository.findById(cardId);
            
            if (existingCard.isPresent()) {
                // Add card context before deletion
                span.setAllAttributes(businessContextTracer.createContext()
                    .cardType(existingCard.get().getCardType())
                    .customerId(existingCard.get().getCustomerId())
                    .toOtelAttributes());
                
                cardRepository.deleteById(cardId);
                
                long duration = System.currentTimeMillis() - startTime;
                span.setAllAttributes(businessContextTracer.createContext()
                    .processingTime(duration)
                    .toOtelAttributes());
                
                logger.info("Service: Deleted card {} in {}ms", cardId, duration);
            } else {
                // Add not found context
                span.setAllAttributes(businessContextTracer.createContext()
                    .errorCode("CARD_NOT_FOUND")
                    .errorCategory("BUSINESS_LOGIC_ERROR")
                    .toOtelAttributes());
                
                logger.warn("Service: Cannot delete - Card with ID {} not found", cardId);
                throw new RuntimeException("Card not found with ID: " + cardId);
            }
            
        } catch (Exception e) {
            span.recordException(e);
            span.setAllAttributes(businessContextTracer.createContext()
                .errorCode("CARD_SERVICE_DELETE_FAILED")
                .errorCategory("SERVICE_LAYER_ERROR")
                .toOtelAttributes());
            
            logger.error("Service: Failed to delete card with ID: {}", cardId, e);
            throw e;
        } finally {
            span.end();
        }
    }
}
