package com.mymail.integration.google.repository;

import com.mymail.integration.google.entities.GoogleAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GoogleAccountRepository extends JpaRepository<GoogleAccount, UUID> {

    Optional<GoogleAccount> findByGoogleUserId(String googleUserId);

    Optional<GoogleAccount> findByUserId(UUID userId);

    boolean existsByGoogleUserId(String googleUserId);
}