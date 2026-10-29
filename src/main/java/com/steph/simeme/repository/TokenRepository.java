package com.steph.simeme.repository;

import com.steph.simeme.entity.Token;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TokenRepository extends JpaRepository<Token, String> {
    
    // Spring JPA otomatis menerjemahkan nama method ini menjadi query:
    // SELECT * FROM tokens WHERE is_featured = true;
    List<Token> findByIsFeaturedTrue();
}