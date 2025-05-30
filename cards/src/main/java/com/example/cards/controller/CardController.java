package com.example.cards.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.example.cards.entity.Card;
import com.example.cards.service.CardService;
import java.util.List;
import java.util.Optional;

@RestController
public class CardController {
    @Autowired
    private CardService cardService;

    // Create
    @PostMapping("/create")
    public Card createCard(@RequestBody Card card) {
        return cardService.createCard(card);
    }

    // Read all
    @GetMapping("/getAll")
    public List<Card> getAllCards() {
        return cardService.getAllCards();
    }

    // Read by ID
    @GetMapping("/{id}")
    public Optional<Card> getCardById(@PathVariable("id") String cardId) {
        return cardService.getCardById(cardId);
    }

    // Update
    @PutMapping("/{id}")
    public Card updateCard(@PathVariable("id") String cardId, @RequestBody Card card) {
        return cardService.updateCard(cardId, card);
    }

    // Delete
    @DeleteMapping("/{id}")
    public void deleteCard(@PathVariable("id") String cardId) {
        cardService.deleteCard(cardId);
    }
}
