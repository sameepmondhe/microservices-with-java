package com.example.accounts.repository;

import com.example.accounts.entity.Account;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AccountRepository extends MongoRepository<Account, String> {
    // ...you can add custom query methods here if needed...
}
