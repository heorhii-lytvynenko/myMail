package com.mymail.integration.google.repository;

import com.mymail.integration.google.entities.GoogleAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoogleAccountRepository extends JpaRepository<GoogleAccount, UUID> {

    Optional<GoogleAccount> findByGoogleUserId(String googleUserId);

    Optional<GoogleAccount> findByUserId(UUID userId);

    Optional<GoogleAccount> findByUser_IdpId(String idpId);

    boolean existsByGoogleUserId(String googleUserId);
}
