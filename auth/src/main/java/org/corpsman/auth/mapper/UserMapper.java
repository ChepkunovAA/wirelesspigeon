package org.corpsman.auth.mapper;

import org.corpsman.auth.model.User;
import org.corpsman.common.dto.UserDto;
import org.mapstruct.Mapper;

/**
 * Маппер пользователя в DTO.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * Преобразует сущность пользователя в DTO.
     *
     * @param user сущность пользователя
     * @return DTO пользователя
     */
    UserDto toDto(User user);
}