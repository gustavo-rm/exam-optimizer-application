package com.ia.project.dynamicstudyplanner.domain.exam;

import com.ia.project.dynamicstudyplanner.domain.PlanningItem;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A fronteira entre o edital de concurso e o núcleo do otimizador. <b>Removido em EOA-4b.</b>
 *
 * <h2>Por que esta classe existe</h2>
 *
 * O núcleo passou a planejar em {@link PlanningItem} — o tópico do SINAPSE, identificado por UUID.
 * O caminho de concurso, porém, continua de pé enquanto a linha não é desligada: a API recebe um
 * edital, os calculadores produzem mapas por {@link Subject} e o gerador de cronograma devolve
 * blocos por {@link Subject}. Esta classe é o único ponto em que os dois vocabulários se encontram.
 *
 * <p><b>Ela é temporária por decisão explícita.</b> Não é meia generalização nem o primeiro de dois
 * adaptadores permanentes: este otimizador existe para servir o SINAPSE, e um núcleo parametrizado
 * com duas implementações seria abstração comprada para um futuro que foi descartado. Em
 * <b>EOA-4b</b>, quando o caminho de concurso sair, esta classe sai junto — e com ela o pacote
 * {@code domain.exam} inteiro, que é por isso que ela mora aqui e não em {@code service}.
 *
 * <h2>A tradução</h2>
 *
 * <pre>
 *   id             = subject.name()
 *   name           = subject.name()
 *   difficultyBand = subject.cognitiveLoad()    // identidade, 1..5
 * </pre>
 *
 * <p>A banda é a carga cognitiva <b>sem conversão alguma</b>: as duas já são a mesma escala ordinal
 * de 1 a 5, e a API valida o intervalo na entrada ({@code SubjectDto.cognitiveLoad}). Qualquer
 * reescala aqui mudaria o resultado do algoritmo, que é exatamente o que esta etapa não pode fazer.
 *
 * <p>O identificador é o nome da disciplina. Isso é injetivo no caminho de concurso porque
 * {@code StudentProfileMapper} já recusa um edital com duas disciplinas de mesmo nome — ele indexa
 * as disciplinas por nome com {@code Collectors.toMap}, que estoura na chave repetida, antes de
 * qualquer coisa chegar ao montador do contexto. Um edital construído direto em teste, fora da API,
 * escapa dessa checagem: duas disciplinas homônimas e de mesma carga viram <b>um</b> item, e o
 * cromossomo perde um gene. Nenhuma validação nova foi acrescentada aqui de propósito — a que
 * existe está onde os dados entram, e duplicá-la mudaria o ponto em que a requisição falha.
 */
public final class SubjectPlanningItemMapper {

    private SubjectPlanningItemMapper() {
    }

    /**
     * Traduz uma disciplina do edital no item de planejamento correspondente.
     *
     * @param subject a disciplina
     * @return o item equivalente
     */
    public static PlanningItem toItem(Subject subject) {
        return new PlanningItem(subject.name(), subject.name(), subject.cognitiveLoad());
    }

    /**
     * Traduz as disciplinas <b>preservando a ordem</b> em que chegaram.
     *
     * <p>A ordem não é detalhe: ela é a ordem dos genes do cromossomo
     * ({@code PlanningItemIndex}), e portanto decide onde cai o ponto de corte do cruzamento e a que
     * item corresponde cada sorteio. A lista do edital entra como está.
     *
     * @param subjects as disciplinas, na ordem do edital
     * @return os itens na mesma ordem
     */
    public static List<PlanningItem> toItems(Collection<Subject> subjects) {
        return subjects.stream().map(SubjectPlanningItemMapper::toItem).toList();
    }

    /**
     * Retroca as chaves de um mapa por disciplina para um mapa por item.
     *
     * <h2>Por que {@link LinkedHashMap}, e não {@code Collectors.toMap}</h2>
     *
     * O mapa devolvido preserva a ordem de iteração do mapa recebido, e isso é <b>aritmético</b>,
     * não cosmético: {@code EvolutionContext.normalize} soma {@code raw.values()} para dividir as
     * importâncias pelo total, e soma de ponto flutuante não é associativa. Um {@code HashMap} novo
     * iteraria na ordem dos códigos de espalhamento de {@code PlanningItem}, que não são os de
     * {@code Subject} — a soma mudaria de ordem, o total poderia mudar no último bit, e com ele
     * todo o fitness. Preservar a ordem da origem mantém a soma idêntica à de antes da migração.
     *
     * @param bySubject mapa por disciplina
     * @param <V>       o tipo do valor, que atravessa intacto
     * @return o mesmo mapa, chaveado por item, na mesma ordem de iteração
     */
    public static <V> Map<PlanningItem, V> rekey(Map<Subject, V> bySubject) {
        Map<PlanningItem, V> byItem = new LinkedHashMap<>();
        for (Map.Entry<Subject, V> entry : bySubject.entrySet()) {
            byItem.put(toItem(entry.getKey()), entry.getValue());
        }
        return byItem;
    }

    /**
     * A volta: de que disciplina veio cada item.
     *
     * <p>É o que o gerador de cronograma precisa para reconverter o plano que o algoritmo devolve —
     * a camada de agendamento continua falando em {@link Subject}, e continuará até EOA-4b.
     *
     * @param subjects as disciplinas do edital
     * @return mapa item {@literal ->} disciplina, na ordem do edital
     */
    public static Map<PlanningItem, Subject> subjectsByItem(Collection<Subject> subjects) {
        Map<PlanningItem, Subject> porItem = new LinkedHashMap<>();
        for (Subject subject : subjects) {
            porItem.put(toItem(subject), subject);
        }
        return porItem;
    }
}
