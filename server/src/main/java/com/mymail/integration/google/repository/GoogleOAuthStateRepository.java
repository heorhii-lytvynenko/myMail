package com.mymail.integration.google.repository;

import com.mymail.user.entities.GoogleOAuthState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GoogleOAuthStateRepository extends JpaRepository<GoogleOAuthState, UUID> {

    Optional<GoogleOAuthState> findByState(String state);
}