package com.mymail.user.mapper;

import com.mymail.api.model.UserResponse;
import com.mymail.user.entities.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}