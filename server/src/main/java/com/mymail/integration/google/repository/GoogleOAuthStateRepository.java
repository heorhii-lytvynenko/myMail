package com.mymail.integration.google.repository;

import com.mymail.user.entities.GoogleOAuthState;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
import java.time.Instant;

public interface GoogleOAuthStateRepository extends JpaRepository<GoogleOAuthState, UUID> {
    Optional<GoogleOAuthState> findByState(String state);
    @Modifying
    @Query("update GoogleOAuthState s set s.transactionStatus = 'consumed' where s.id = :id and s.transactionStatus = 'pending' and s.expiresAt > :now")
    int claim(@Param("id") UUID id, @Param("now") Instant now);
    @Modifying
    @Query("update GoogleOAuthState s set s.transactionStatus = 'cancelled' where s.user.id = :user and s.transactionStatus = 'pending'")
    int cancel(@Param("user") UUID user);
}
