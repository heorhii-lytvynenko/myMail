package com.mymail.user.mappers;

import com.mymail.api.model.UserResponse;
import com.mymail.user.entities.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "idpId", source = "idpId")
    @Mapping(target = "email", source = "email")
    User toEntity(String idpId, String email);
}
