package com.mymail.user.controller;

import com.mymail.api.UsersApi;
import com.mymail.api.model.UserResponse;
import com.mymail.user.entities.User;
import com.mymail.user.mapper.UserMapper;
import com.mymail.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController implements UsersApi {

    private final UserService userService;
    private final UserMapper userMapper;

    @Override
    public ResponseEntity<UserResponse> getCurrentUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        User user = userService.getOrCreateUser(authentication);

        return ResponseEntity.ok(userMapper.toResponse(user));
    }
}