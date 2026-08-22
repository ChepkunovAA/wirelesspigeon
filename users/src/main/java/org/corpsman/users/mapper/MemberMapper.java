package org.corpsman.users.mapper;

import org.corpsman.auth.model.User;
import org.corpsman.users.dto.MemberDto;
import org.mapstruct.Mapper;

/**
 * Маппер пользователя в DTO участника.
 */
@Mapper(componentModel = "spring")
public interface MemberMapper {

    /**
     * Преобразует сущность пользователя в DTO участника.
     *
     * @param user сущность пользователя
     * @return DTO участника
     */
    MemberDto toDto(User user);
}
