package com.mymail.user.mapper;

import com.mymail.api.model.UserResponse;
import com.mymail.user.entities.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "idpId", source = "idpId")
    @Mapping(target = "email", source = "email")
    UserResponse toResponse(User user);
}