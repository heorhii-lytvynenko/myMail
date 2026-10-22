package com.mymail.user.repository;

import com.mymail.user.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByIdpId(String idpId);
    @org.springframework.data.jpa.repository.Query(value = "select exists(select 1 from users where lower(email) = lower(:email))", nativeQuery = true)
    boolean existsByEmailIgnoreCase(@org.springframework.data.repository.query.Param("email") String email);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    Optional<User> lockById(@org.springframework.data.repository.query.Param("id") UUID id);
}
