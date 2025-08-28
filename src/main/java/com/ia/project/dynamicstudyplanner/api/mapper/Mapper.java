package com.ia.project.dynamicstudyplanner.api.mapper;

/**
 * A generic interface for mapping between a Data Transfer Object (DTO) and a Domain entity.
 *
 * @param <D> The type of the Data Transfer Object.
 * @param <E> The type of the Domain Entity.
 */
public interface Mapper<D, E> {

    /**
     * Maps a Domain entity to its corresponding Data Transfer Object.
     *
     * @param entity The domain entity to be mapped.
     * @return The resulting DTO.
     */
    D toDto(E entity);

    /**
     * Maps a Data Transfer Object to its corresponding Domain entity.
     *
     * @param dto The DTO to be mapped.
     * @return The resulting domain entity.
     */
    E toDomain(D dto);
}
