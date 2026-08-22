package org.corpsman.users.mapper;

import javax.annotation.processing.Generated;
import org.corpsman.auth.model.User;
import org.corpsman.users.dto.MemberDto;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-22T23:06:54+0300",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21 (GraalVM Community)"
)
@Component
public class MemberMapperImpl implements MemberMapper {

    @Override
    public MemberDto toDto(User user) {
        if ( user == null ) {
            return null;
        }

        MemberDto memberDto = new MemberDto();

        memberDto.setId( user.getId() );
        memberDto.setUsername( user.getUsername() );
        memberDto.setRole( user.getRole() );
        memberDto.setLastLoginAt( user.getLastLoginAt() );

        return memberDto;
    }
}
