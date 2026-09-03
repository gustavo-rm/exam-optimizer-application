package com.ia.project.dynamicstudyplanner.service.calculation.fatigue;

import com.ia.project.dynamicstudyplanner.domain.Chronotype;
import com.ia.project.dynamicstudyplanner.domain.StudentState;
import com.ia.project.dynamicstudyplanner.domain.fatigue.FatigueAlgorithm;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Estima o risco de esgotamento de um cronograma, a partir da energia disponível ao longo do dia e
 * da fadiga que se acumula entre os dias.
 *
 * <h2>O formato real da penalidade</h2>
 *
 * O resultado vai de <b>1,0</b> (perfeitamente sustentável) a <b>0,1</b> (insustentável), e
 * <b>não é uma curva contínua</b>. São três regimes, e a diferença importa para quem for calibrar
 * pesos de fitness:
 *
 * <ol>
 *   <li><b>Esgotamento agudo</b> — a fadiga de um único dia passa de metade do limiar: devolve
 *       {@value #ACUTE_BURNOUT_PENALTY} imediatamente, sem olhar o resto do plano.</li>
 *   <li><b>Esgotamento crônico</b> — a fadiga acumulada passa do limiar: devolve
 *       {@value #CHRONIC_BURNOUT_PENALTY}.</li>
 *   <li><b>Rampa gradual</b> — queda <b>linear</b> com a fadiga acumulada, limitada por baixo em
 *       {@value #GRADUAL_PENALTY_FLOOR}.</li>
 * </ol>
 *
 * <p>Como a rampa para em {@value #GRADUAL_PENALTY_FLOOR} e o degrau agudo devolve
 * {@value #ACUTE_BURNOUT_PENALTY}, <b>nenhuma entrada produz valor entre os dois</b>: há um salto.
 * Até a etapa 04b o Javadoc desta classe afirmava que a queda era "exponencial", o que nunca foi
 * verdade em nenhum dos três regimes (achado C1 de
 * {@code docs/qualidade/04-diagnostico-escrita.md}).
 *
 * <h2>Duas limitações medidas, ainda não decididas</h2>
 *
 * <ul>
 *   <li><b>A metodologia de estudo não altera o resultado.</b> O multiplicador de fadiga escolhe
 *       entre {@value #OVERLOADED_FATIGUE_MULTIPLIER} e {@value #COMFORTABLE_FATIGUE_MULTIPLIER}
 *       comparando a carga do bloco com a energia esperada — mas a energia é limitada a 1,5 e o
 *       bloco mais leve possível já custa 2,048. A comparação é sempre verdadeira, então
 *       {@link #getExpectedEnergyLevel} é calculada e descartada. Pendência <b>P12</b>.</li>
 *   <li><b>O regime crônico é inalcançável pela API</b>, porque exige {@code fatigueLevel >= 8} e
 *       {@code StudentStateDto} valida o campo com {@code @DecimalMax("5.0")}. Pendência
 *       <b>P11</b>.</li>
 * </ul>
 *
 * <p>As duas foram <b>preservadas exatamente</b> na refatoração da etapa 04b, e estão travadas por
 * {@code FatigueAndEnergyModelTest}. Mudá-las é decisão de domínio, não de legibilidade — as
 * pendências estão em {@code docs/qualidade/04b-correcao-escrita.md}.
 */
@Service
public class FatigueAndEnergyModel implements FatigueAlgorithm {

    /** Fadiga acumulada a partir da qual se considera esgotamento crônico. */
    private static final double BURNOUT_THRESHOLD = 50.0;

    /** Fração do limiar que, num único dia, já caracteriza esgotamento agudo. */
    private static final double ACUTE_BURNOUT_FRACTION = 0.5;

    /** Quanto da fadiga do dia atravessa a noite e entra no dia seguinte. */
    private static final double FATIGUE_CARRYOVER_RATE = 0.2;

    /** Converte a fadiga declarada pelo aluno (escala 1–5) para a escala interna do modelo. */
    private static final double REPORTED_FATIGUE_SCALE = 5.0;

    /** Multiplicador quando a carga do bloco excede a energia esperada para o horário. */
    private static final double OVERLOADED_FATIGUE_MULTIPLIER = 1.5;

    /** Multiplicador quando a carga cabe na energia esperada. Ver P12: hoje inalcançável. */
    private static final double COMFORTABLE_FATIGUE_MULTIPLIER = 0.8;

    private static final double NO_PENALTY = 1.0;
    private static final double ACUTE_BURNOUT_PENALTY = 0.1;
    private static final double CHRONIC_BURNOUT_PENALTY = 0.2;

    /** Piso da rampa gradual: por pior que fique, o regime gradual não desce disto. */
    private static final double GRADUAL_PENALTY_FLOOR = 0.5;

    /** Fadiga que zeraria a rampa gradual, se não houvesse piso. */
    private static final double GRADUAL_PENALTY_SPAN = BURNOUT_THRESHOLD * 1.5;

    private static final double MINUTES_PER_HOUR = 60.0;

    // --- Curva bifásica de energia -------------------------------------------------------------

    private static final double BASE_ENERGY = 1.0;
    private static final double MIN_ENERGY = 0.5;
    private static final double MAX_ENERGY = 1.5;

    private static final double PRIMARY_PEAK_GAIN = 0.5;
    private static final double PRIMARY_PEAK_RADIUS_HOURS = 4.0;
    private static final double SECONDARY_PEAK_GAIN = 0.3;
    private static final double SECONDARY_PEAK_RADIUS_HOURS = 3.0;

    private static final double LATE_NIGHT_PENALTY = 0.4;
    private static final double NIGHT_STARTS_AFTER_HOUR = 23.0;
    private static final double NIGHT_ENDS_BEFORE_HOUR = 6.0;

    private static final double LARK_PRIMARY_PEAK = 9.0;
    private static final double LARK_SECONDARY_PEAK = 15.0;
    private static final double OWL_PRIMARY_PEAK = 14.0;
    private static final double OWL_SECONDARY_PEAK = 22.0;
    private static final double INTERMEDIATE_PRIMARY_PEAK = 10.0;
    private static final double INTERMEDIATE_SECONDARY_PEAK = 16.0;

    /**
     * Penalidade de risco de esgotamento para o plano inteiro. Ver o Javadoc da classe para os três
     * regimes e para o salto entre eles.
     */
    @Override
    public double calculateBurnoutRisk(TacticalStudyPlan plan, StudentState state) {
        if (state == null || plan.getSchedule().isEmpty()) {
            return NO_PENALTY;
        }

        double cumulativeFatigue = state.fatigueLevel() * REPORTED_FATIGUE_SCALE;
        double dailyFatigue = 0.0;
        int currentDay = -1;

        for (Map.Entry<TimeSlot, TacticalStudyBlock> entry : chronologically(plan)) {
            int blockDay = entry.getKey().startTime().getDayOfYear();

            if (currentDay == -1) {
                currentDay = blockDay;
            } else if (blockDay != currentDay) {
                // Virada de dia: parte da fadiga atravessa a noite, o resto se dissipa.
                cumulativeFatigue = (cumulativeFatigue + dailyFatigue) * FATIGUE_CARRYOVER_RATE;
                dailyFatigue = 0.0;
                currentDay = blockDay;
            }

            dailyFatigue += fatigueOf(entry.getKey(), entry.getValue(), state);

            if (dailyFatigue > BURNOUT_THRESHOLD * ACUTE_BURNOUT_FRACTION) {
                // Um único dia insustentável condena o plano: não adianta olhar os demais.
                return ACUTE_BURNOUT_PENALTY;
            }
        }

        cumulativeFatigue += dailyFatigue;

        return cumulativeFatigue > BURNOUT_THRESHOLD
                ? CHRONIC_BURNOUT_PENALTY
                : gradualPenalty(cumulativeFatigue);
    }

    /**
     * Fadiga gerada por um bloco, em "horas-equivalentes".
     *
     * <p>Estudar acima da energia disponível para o horário cansa mais depressa do que estudar
     * dentro dela. <b>Na configuração atual o segundo caso nunca ocorre</b> — ver P12 no Javadoc da
     * classe. O ramo é mantido porque removê-lo seria decidir a questão de domínio por conta
     * própria.
     */
    private double fatigueOf(TimeSlot slot, TacticalStudyBlock block, StudentState state) {
        double expectedEnergy = getExpectedEnergyLevel(slot.startTime().toLocalTime(), state.chronotype());
        double blockLoad = block.calculateEmotionalLoad() + block.calculateRequiredEnergy();

        double multiplier = blockLoad > expectedEnergy
                ? OVERLOADED_FATIGUE_MULTIPLIER
                : COMFORTABLE_FATIGUE_MULTIPLIER;

        return (block.durationMinutes() / MINUTES_PER_HOUR) * multiplier;
    }

    /** Queda linear com a fadiga acumulada, limitada por baixo. */
    private double gradualPenalty(double cumulativeFatigue) {
        return Math.max(GRADUAL_PENALTY_FLOOR, NO_PENALTY - (cumulativeFatigue / GRADUAL_PENALTY_SPAN));
    }

    /**
     * Blocos em ordem cronológica.
     *
     * <p>A ordenação é o que permite detectar viradas de dia comparando apenas com o bloco anterior.
     * O código anterior fazia a mesma ordenação com um comentário dizendo que ela era suposta —
     * ela não é suposta, é feita aqui.
     */
    private List<Map.Entry<TimeSlot, TacticalStudyBlock>> chronologically(TacticalStudyPlan plan) {
        return plan.getSchedule().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().startTime()))
                .toList();
    }

    /**
     * Energia esperada do aluno num horário, entre {@value #MIN_ENERGY} e {@value #MAX_ENERGY}.
     *
     * <p>Curva bifásica: um pico forte e um pico secundário mais fraco, deslocados conforme o
     * cronotipo, com penalidade na madrugada.
     */
    @Override
    public double getExpectedEnergyLevel(LocalTime time, Chronotype chronotype) {
        double hour = time.getHour() + (time.getMinute() / MINUTES_PER_HOUR);

        return switch (chronotype) {
            case MORNING_LARK -> biphasicCurve(hour, LARK_PRIMARY_PEAK, LARK_SECONDARY_PEAK);
            case NIGHT_OWL -> biphasicCurve(hour, OWL_PRIMARY_PEAK, OWL_SECONDARY_PEAK);
            case INTERMEDIATE -> biphasicCurve(hour, INTERMEDIATE_PRIMARY_PEAK, INTERMEDIATE_SECONDARY_PEAK);
            default -> BASE_ENERGY;
        };
    }

    /** Soma dois picos em torno da energia de base e aplica a penalidade da madrugada. */
    private double biphasicCurve(double hour, double primaryPeakHour, double secondaryPeakHour) {
        double energy = BASE_ENERGY
                + peakContribution(hour, primaryPeakHour, PRIMARY_PEAK_RADIUS_HOURS, PRIMARY_PEAK_GAIN)
                + peakContribution(hour, secondaryPeakHour, SECONDARY_PEAK_RADIUS_HOURS, SECONDARY_PEAK_GAIN);

        if (hour < NIGHT_ENDS_BEFORE_HOUR || hour > NIGHT_STARTS_AFTER_HOUR) {
            energy -= LATE_NIGHT_PENALTY;
        }

        return Math.max(MIN_ENERGY, Math.min(MAX_ENERGY, energy));
    }

    /**
     * Contribuição de um pico: máxima exatamente na hora do pico, caindo em cosseno até zero na
     * borda do raio, e nula fora dele.
     */
    private double peakContribution(double hour, double peakHour, double radiusHours, double gain) {
        double distance = Math.abs(hour - peakHour);
        if (distance >= radiusHours) {
            return 0.0;
        }
        return gain * Math.cos((distance / radiusHours) * (Math.PI / 2));
    }
}
