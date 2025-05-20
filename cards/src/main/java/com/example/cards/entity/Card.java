package com.example.cards.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document (collation =  "card")
public class Card {

    @Id
    private String cardId;
    private String cardNumber;
    private String cardType;
    private String cardStatus;
    private String cardHolderName;
    private String cardExpiry;
    private String cvv;
}
