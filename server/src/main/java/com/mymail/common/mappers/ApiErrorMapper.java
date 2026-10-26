package com.mymail.common.mappers;

import com.mymail.api.model.ErrorDto;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = {OffsetDateTime.class, ZoneOffset.class})
public interface ApiErrorMapper {
    @Mapping(target = "timestamp", expression = "java(OffsetDateTime.now(ZoneOffset.UTC))")
    @Mapping(target = "message", constant = "Request could not be completed.")
    ErrorDto toResponse(String path, int status, String error);
}
