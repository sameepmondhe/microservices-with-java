package com.example.cards.repository;

import com.example.cards.entity.Card;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;


public interface CardRepository extends MongoRepository<Card, String> {
}
