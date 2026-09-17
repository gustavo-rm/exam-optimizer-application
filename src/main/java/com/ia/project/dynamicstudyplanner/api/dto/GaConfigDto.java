package com.ia.project.dynamicstudyplanner.api.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
/**
 * DTO for the Genetic Algorithm's configuration. Includes validation rules to prevent
 * excessively large values that could cause CPU exhaustion (Denial of Service).
 *
 * @param totalStudyDays Total "ideal" days for the GA. Must be between 1 and 365 (1 year max).
 * @param numGenerations Number of generations to run. Must be between 10 and 1000.
 * @param populationSize Size of the population. Must be between 10 and 500.
 */
@Schema(description = "Configuration settings for the Genetic Algorithm.")
public record GaConfigDto(
        @Schema(description = "The target number of study days to allocate across all subjects.", example = "100")
        @Min(value = 1, message = "Total study days must be at least 1.")
        @Max(value = 365, message = "Total study days cannot exceed 365 days (1 year).")
        int totalStudyDays,
        @Schema(description = "The number of evolutionary cycles the algorithm will run. Higher "
                + "yields better results but takes longer.",
                example = "100")
        @Min(value = MIN_GENERATIONS, message = "Number of generations must be at least 10.")
        @Max(value = MAX_GENERATIONS,
                message = "Number of generations cannot exceed 1000 to prevent CPU exhaustion.")
        int numGenerations,
        @Schema(description = "The number of potential solutions evolving per generation.", example = "50")
        @Min(value = MIN_POPULATION, message = "Population size must be at least 10.")
        @Max(value = MAX_POPULATION,
                message = "Population size cannot exceed 500 to prevent CPU exhaustion.")
        int populationSize
) {

    /**
     * Os limites como constantes, e não como literais nas anotações.
     *
     * <h2>Por que isto deixou de ser detalhe na etapa 06b</h2>
     *
     * Desde o achado E4 o limite de taxa <b>precifica o pedido antes de validá-lo</b>, e para isso
     * precisa saber o que o contrato aceita: um pedido fora da faixa será recusado com 400 sem rodar
     * otimização nenhuma, então não pode ser cobrado como se fosse rodar.
     *
     * <p>Com os números repetidos em dois lugares, mexer no teto aqui e esquecer o filtro faria o
     * preço divergir do contrato em silêncio — um pedido legítimo passando a custar o piso, ou um
     * inválido drenando o balde. Uma constante só elimina a possibilidade.
     */
    public static final int MIN_GENERATIONS = 10;

    /** @see #MIN_GENERATIONS */
    public static final int MAX_GENERATIONS = 1000;

    /** @see #MIN_GENERATIONS */
    public static final int MIN_POPULATION = 10;

    /** @see #MIN_GENERATIONS */
    public static final int MAX_POPULATION = 500;
}
