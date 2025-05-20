package com.example.cards.service;

import com.example.cards.entity.Card;
import com.example.cards.repository.CardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CardService {

    @Autowired
    private CardRepository cardRepository;

    // Create
    public Card createCard(Card card) {
        return cardRepository.save(card);
    }

    // Read all
    public List<Card> getAllCards() {
        return cardRepository.findAll();
    }

    // Read by ID
    public Optional<Card> getCardById(String cardId) {
        return cardRepository.findById(cardId);
    }

    // Update
    public Card updateCard(String cardId, Card updatedCard) {
        return cardRepository.findById(cardId)
                .map(card -> {
                    card.setCardNumber(updatedCard.getCardNumber());
                    card.setCardType(updatedCard.getCardType());
                    card.setCardStatus(updatedCard.getCardStatus());
                    card.setCardHolderName(updatedCard.getCardHolderName());
                    card.setCardExpiry(updatedCard.getCardExpiry());
                    // ...add other fields as needed...
                    return cardRepository.save(card);
                })
                .orElseGet(() -> {
                    updatedCard.setCardId(cardId);
                    return cardRepository.save(updatedCard);
                });
    }

    // Delete
    public void deleteCard(String cardId) {
        cardRepository.deleteById(cardId);
    }
}
