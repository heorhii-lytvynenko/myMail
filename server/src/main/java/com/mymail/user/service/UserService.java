package com.mymail.user.service;

import com.mymail.user.entities.User;
import com.mymail.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User getOrCreateUser(Authentication authentication) {
        String idpId = authentication.getName();

        return userRepository.findByIdpId(idpId)
                .orElseGet(() -> createUser(authentication));
    }

    private User createUser(Authentication authentication) {
        String idpId = authentication.getName();

        String email = "fdsjdl@gmail.com"; // extract email from jwt

        User user = new User();
        user.setIdpId(idpId);
        user.setEmail(email);

        return userRepository.save(user);
    }
}