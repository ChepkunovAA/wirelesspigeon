package org.corpsman.auth.mapper;

import javax.annotation.processing.Generated;
import org.corpsman.auth.model.User;
import org.corpsman.common.dto.UserDto;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-23T00:25:35+0300",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21 (GraalVM Community)"
)
@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public UserDto toDto(User user) {
        if ( user == null ) {
            return null;
        }

        UserDto userDto = new UserDto();

        userDto.setId( user.getId() );
        userDto.setUsername( user.getUsername() );
        if ( user.getRole() != null ) {
            userDto.setRole( user.getRole().name() );
        }
        userDto.setActive( user.isActive() );

        return userDto;
    }
}
