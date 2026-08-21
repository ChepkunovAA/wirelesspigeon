package org.corpsman.cinema.mapper;

import org.corpsman.cinema.dto.FilmDto;
import org.corpsman.cinema.model.Film;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Маппер фильма в DTO.
 */
@Mapper(componentModel = "spring")
public interface FilmMapper {

    /**
     * Преобразует сущность фильма в DTO.
     *
     * @param film сущность фильма
     * @return DTO фильма
     */
    @Mapping(source = "userId", target = "proposerId")
    FilmDto toDto(Film film);

    /**
     * Преобразует список сущностей фильмов в список DTO.
     *
     * @param films сущности фильмов
     * @return список DTO
     */
    List<FilmDto> toDtoList(List<Film> films);
}