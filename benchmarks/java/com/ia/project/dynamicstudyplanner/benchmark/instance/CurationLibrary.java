package com.ia.project.dynamicstudyplanner.benchmark.instance;

import com.ia.project.dynamicstudyplanner.coreapi.contract.EdgeProvenance;
import com.ia.project.dynamicstudyplanner.coreapi.contract.EdgeStrength;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * As instâncias da medição de curadoria (EOA-1): disciplinas com a forma do catálogo de exemplo,
 * geradas deterministicamente, enviadas de vários jeitos.
 *
 * <h2>Por que um gerador novo, e não mais um eixo em {@link InstanceLibrary}</h2>
 *
 * {@code InstanceLibrary} fixa números publicados nos relatórios 09 a 12; um eixo a mais nela mudaria
 * o fluxo pseudoaleatório de cada instância e invalidaria todos. E ela foi desenhada para outra
 * pergunta: os minutos são sorteados entre 30 e 180 <b>independentes da faixa</b>, metade das arestas
 * {@code SOFT} aponta para trás de propósito, e as faixas giram {@code índice % 4}. Nada disso tem a
 * forma do que a plataforma manda. Este gerador copia a forma do catálogo de exemplo
 * ({@code sinapse-platform:catalog/MED-ANAT}, lido em {@code other-repo@46b5b3c}):
 *
 * <ul>
 *   <li><b>{@value #TOPICS_PER_SUBJECT} tópicos por disciplina</b>, posições 1..35;</li>
 *   <li><b>faixas na proporção do catálogo</b> — 4 {@code SHORT}, 21 {@code STANDARD}, 8 {@code LONG},
 *       2 {@code EXTENDED} por disciplina, embaralhadas sobre as posições;</li>
 *   <li><b>minutos derivados da faixa</b> pela tabela do nível de esforço, como a plataforma faz
 *       ({@code EffortTierConfiguration}), em vez de sorteados;</li>
 *   <li><b>uma aresta por tópico</b> (35 em 35), a maioria entre posições próximas, e
 *       {@value #BACKWARD_EDGES_PER_SUBJECT} em 35 apontando contra a posição, como no catálogo;</li>
 *   <li><b>todas as arestas {@code CURATED}</b>, como no catálogo. A proveniência não é eixo aqui.</li>
 * </ul>
 *
 * <p>Duas coisas não vêm do catálogo, porque ele tem uma disciplina só, e são suposições declaradas:
 * a fração {@value #CROSS_SUBJECT_SHARE} de arestas entre disciplinas quando há mais de uma (a ADR 0014
 * as permite e nenhum catálogo real as mostra ainda), e o calendário, que é o de
 * {@code InstanceLibrary} — janelas diárias de {@value #WINDOW_MINUTES} minutos em dias seguidos,
 * tantos quantos a folga pede.
 *
 * <h2>O calendário do aluno não muda com a anotação</h2>
 *
 * A folga ({@code tightness}) é medida contra a demanda <b>nominal</b> — os minutos das faixas latentes
 * sob a tabela de hoje da plataforma ({@link #NOMINAL_EFFORT}) —, e não contra a demanda do nível de
 * esforço que está sendo enviado. É o contrafactual certo: o que o curador anota muda o que se pede ao
 * aluno, não quantas horas o aluno tem. Se a folga fosse recalculada por nível, {@code uniform} (50 min
 * por tópico) ganharia um calendário ~20% menor que {@code tiers-platform} (~62 min por tópico em
 * média), e a diferença de cobertura entre os dois mediria o calendário, não a anotação. Por isso a
 * coluna {@code tightness} é o nível nominal, e {@code demanded_minutes} / {@code available_minutes}
 * dão a folga efetiva de cada linha.
 *
 * <h2>Duas propriedades garantidas</h2>
 *
 * <b>O conjunto inteiro de arestas é acíclico</b>, não só a parte {@code HARD}: uma aresta candidata que
 * fecharia um ciclo com as já aceitas é recusada e sorteada de novo. Então qualquer reclassificação —
 * inclusive {@link EdgeStrengthMix#ALL_HARD} — é planejável, e o eixo de força nunca mede o caminho de
 * recusa por ciclo.
 *
 * <p><b>A estrutura depende só de {@code subjects} e {@code replicate}.</b> Mesmos tópicos, mesmas
 * faixas latentes, mesmas arestas em todo nível dos outros eixos — ver {@link CurationPoint}.
 */
public final class CurationLibrary {

    /** Fixa a biblioteca inteira. Mudá-lo invalida toda medição gravada com ele. */
    public static final long GENERATOR_SEED = 20261111L;

    /** Tópicos por disciplina: o tamanho do catálogo de exemplo. */
    public static final int TOPICS_PER_SUBJECT = 35;

    /** Minutos de cada janela diária. Maior que o tópico mais longo de qualquer nível de esforço. */
    public static final int WINDOW_MINUTES = 240;

    /** Quantos tópicos de cada faixa uma disciplina tem, na ordem de {@link EffortMapping#TIERS}. */
    static final int[] TIER_COUNTS = {4, 21, 8, 2};

    /** Arestas por tópico: 35 em 35 no catálogo de exemplo. */
    public static final double EDGES_PER_TOPIC = 1.0;

    /** Arestas, em cada 35, que apontam contra a posição curricular: 2 no catálogo. */
    public static final int BACKWARD_EDGES_PER_SUBJECT = 2;

    /** Arestas, em cada 35, que o curador marcou {@code HARD}: 24 no catálogo. */
    public static final int HARD_EDGES_PER_SUBJECT = 24;

    /** Fração de arestas entre disciplinas, quando há mais de uma. Suposição, não dado. */
    public static final double CROSS_SUBJECT_SHARE = 0.2;

    /** A tabela que define a demanda nominal: a da plataforma hoje. */
    public static final EffortMapping NOMINAL_EFFORT = EffortMapping.PLATFORM;

    /** Primeiro dia de todo horizonte. */
    private static final LocalDate START = LocalDate.parse("2026-11-16");

    /** Fração das arestas locais (posições até 8 de distância): 30 de 35 no catálogo. */
    private static final double LOCAL_SHARE = 0.85;

    /** Maior distância de uma aresta local. */
    private static final int LOCAL_SPAN = 8;

    private CurationLibrary() {
    }

    /**
     * Constrói uma instância.
     *
     * @param point as coordenadas
     * @return o pedido, com as coordenadas e a estrutura latente
     */
    public static CurationInstance build(CurationPoint point) {
        Structure structure = structure(point.subjects(), point.replicate());
        List<PlanRequest.Topic> topics = topics(structure, point.effort());
        long demanded = topics.stream().mapToLong(PlanRequest.Topic::estimatedMinutes).sum();

        // O calendario sai da demanda NOMINAL, nao da deste nivel: ver "O calendario do aluno nao
        // muda com a anotacao" no comentario da classe.
        int days = Math.max(1, (int) Math.ceil(
                Math.round(point.tightness() * nominalDemand(structure)) / (double) WINDOW_MINUTES));
        LocalDate end = START.plusDays(days - 1L);
        List<PlanRequest.PrerequisiteEdge> edges = edges(structure, point.strength());

        PlanRequest request = new PlanRequest(PlanRequest.VERSION,
                new PlanRequest.Horizon(START, end), availability(days),
                goals(point.subjects(), point.importance(), end), topics, edges,
                List.of(), Map.of(), 0L);

        BenchmarkInstance benchmark = new BenchmarkInstance(point.id(), point.topics(),
                edges.size() / (double) point.topics(), point.tightness(), days, request, demanded,
                (long) days * WINDOW_MINUTES);
        int hard = (int) edges.stream().filter(edge -> edge.strength() == EdgeStrength.HARD).count();
        int backward = (int) structure.edges().stream().filter(Draft::backward).count();
        int cross = (int) structure.edges().stream().filter(Draft::cross).count();
        return new CurationInstance(point, benchmark, structure.latentTiers(), edges.size(), hard,
                backward, cross);
    }

    /**
     * Minutos demandados sob a tabela da plataforma, sobre as faixas latentes.
     *
     * @param structure o sorteio
     * @return o denominador da folga, o mesmo em todo nível de esforço
     */
    private static long nominalDemand(Structure structure) {
        return structure.latentTiers().values().stream()
                .mapToLong(tier -> NOMINAL_EFFORT.minutesOf(tier)).sum();
    }

    /** A estrutura de um sorteio: tópicos, faixas latentes e arestas, sem força. */
    private record Structure(List<UUID> ids, List<UUID> subjectOf, List<Integer> positionOf,
            Map<UUID, Integer> latentTiers, List<Draft> edges) {
    }

    /**
     * Uma aresta antes de ganhar força.
     *
     * @param prerequisite índice do tópico pré-requisito
     * @param dependent    índice do tópico dependente
     * @param backward     se aponta contra a posição curricular
     * @param cross        se liga duas disciplinas
     * @param rank         sorteio que decide a ordem em que arestas a favor da posição viram {@code HARD}
     */
    private record Draft(int prerequisite, int dependent, boolean backward, boolean cross,
            double rank) {
    }

    private static Structure structure(int subjects, int replicate) {
        Random random = new Random(GENERATOR_SEED + 1000L * subjects + replicate);
        List<UUID> ids = new ArrayList<>();
        List<UUID> subjectOf = new ArrayList<>();
        List<Integer> positionOf = new ArrayList<>();
        Map<UUID, Integer> latent = new LinkedHashMap<>();
        for (int subject = 0; subject < subjects; subject++) {
            List<Integer> tiers = tierDeck();
            Collections.shuffle(tiers, random);
            for (int position = 1; position <= TOPICS_PER_SUBJECT; position++) {
                UUID id = topicId(subject, position);
                ids.add(id);
                subjectOf.add(subjectId(subject));
                positionOf.add(position);
                latent.put(id, tiers.get(position - 1));
            }
        }
        List<Draft> edges = drafts(subjects, random);
        return new Structure(List.copyOf(ids), List.copyOf(subjectOf), List.copyOf(positionOf),
                Collections.unmodifiableMap(latent), edges);
    }

    /** As faixas de uma disciplina, na proporção do catálogo, ainda não embaralhadas. */
    private static List<Integer> tierDeck() {
        List<Integer> deck = new ArrayList<>(TOPICS_PER_SUBJECT);
        for (int tier = 0; tier < TIER_COUNTS.length; tier++) {
            for (int copy = 0; copy < TIER_COUNTS[tier]; copy++) {
                deck.add(tier);
            }
        }
        return deck;
    }

    /**
     * As arestas, sorteadas até o total pedido.
     *
     * <p>As {@value #BACKWARD_EDGES_PER_SUBJECT} arestas para trás de cada 35 são as primeiras
     * sorteadas e não uma probabilidade, para que toda instância tenha exatamente a proporção do
     * catálogo — com 35 arestas, uma probabilidade de 2/35 daria zero em boa parte dos sorteios.
     */
    private static List<Draft> drafts(int subjects, Random random) {
        int topics = subjects * TOPICS_PER_SUBJECT;
        int wanted = (int) Math.round(EDGES_PER_TOPIC * topics);
        int backwardWanted = BACKWARD_EDGES_PER_SUBJECT * subjects;
        List<Draft> accepted = new ArrayList<>(wanted);
        List<Set<Integer>> dependents = new ArrayList<>(topics);
        for (int index = 0; index < topics; index++) {
            dependents.add(new HashSet<>());
        }
        int attempts = 0;
        while (accepted.size() < wanted) {
            if (++attempts > 100 * wanted) {
                throw new IllegalStateException("could not draw " + wanted + " acyclic edges");
            }
            Draft candidate = candidate(subjects, random, accepted.size() < backwardWanted);
            if (acceptable(candidate, dependents)) {
                accepted.add(candidate);
                dependents.get(candidate.prerequisite()).add(candidate.dependent());
            }
        }
        return List.copyOf(accepted);
    }

    /** Um par candidato, orientado a favor da posição salvo quando {@code backward}. */
    private static Draft candidate(int subjects, Random random, boolean backward) {
        boolean cross = subjects > 1 && random.nextDouble() < CROSS_SUBJECT_SHARE;
        int first;
        int second;
        if (cross) {
            int from = random.nextInt(subjects);
            int to = (from + 1 + random.nextInt(subjects - 1)) % subjects;
            first = index(from, random.nextInt(TOPICS_PER_SUBJECT));
            second = index(to, random.nextInt(TOPICS_PER_SUBJECT));
        } else {
            int subject = random.nextInt(subjects);
            int low = random.nextInt(TOPICS_PER_SUBJECT - 1);
            int distance = random.nextDouble() < LOCAL_SHARE
                    ? 1 + random.nextInt(LOCAL_SPAN)
                    : 1 + random.nextInt(TOPICS_PER_SUBJECT - 1);
            first = index(subject, low);
            second = index(subject, Math.min(TOPICS_PER_SUBJECT - 1, low + distance));
        }
        // "A favor da posição": posição menor primeiro; empate entre disciplinas, a de índice menor.
        boolean firstEarlier = positionKey(first) < positionKey(second);
        int early = firstEarlier ? first : second;
        int late = firstEarlier ? second : first;
        double rank = random.nextDouble();
        return backward
                ? new Draft(late, early, true, cross, rank)
                : new Draft(early, late, false, cross, rank);
    }

    /** Recusa laço, par repetido em qualquer sentido, e aresta que fecharia um ciclo. */
    private static boolean acceptable(Draft candidate, List<Set<Integer>> dependents) {
        int from = candidate.prerequisite();
        int to = candidate.dependent();
        if (from == to || dependents.get(from).contains(to) || dependents.get(to).contains(from)) {
            return false;
        }
        return !reaches(dependents, to, from);
    }

    /** Se há caminho de {@code from} a {@code target} pelas arestas já aceitas. */
    private static boolean reaches(List<Set<Integer>> dependents, int from, int target) {
        Deque<Integer> pending = new ArrayDeque<>(List.of(from));
        Set<Integer> seen = new HashSet<>();
        while (!pending.isEmpty()) {
            int current = pending.pop();
            if (current == target) {
                return true;
            }
            if (seen.add(current)) {
                pending.addAll(dependents.get(current));
            }
        }
        return false;
    }

    /**
     * As arestas com a força deste nível.
     *
     * <p>No nível {@link EdgeStrengthMix#CURATED}, {@value #HARD_EDGES_PER_SUBJECT} em cada 35 viram
     * {@code HARD}, escolhidas entre as que apontam <b>a favor</b> da posição por ordem de
     * {@code rank}: no catálogo, nenhuma aresta contra a posição é {@code HARD}.
     */
    private static List<PlanRequest.PrerequisiteEdge> edges(Structure structure,
            EdgeStrengthMix strength) {

        Set<Draft> hard = new HashSet<>();
        if (strength == EdgeStrengthMix.ALL_HARD) {
            hard.addAll(structure.edges());
        } else if (strength == EdgeStrengthMix.CURATED) {
            int subjects = structure.ids().size() / TOPICS_PER_SUBJECT;
            structure.edges().stream()
                    .filter(edge -> !edge.backward())
                    .sorted(Comparator.comparingDouble(Draft::rank))
                    .limit((long) HARD_EDGES_PER_SUBJECT * subjects)
                    .forEach(hard::add);
        }
        List<PlanRequest.PrerequisiteEdge> edges = new ArrayList<>(structure.edges().size());
        for (Draft draft : structure.edges()) {
            edges.add(new PlanRequest.PrerequisiteEdge(structure.ids().get(draft.prerequisite()),
                    structure.ids().get(draft.dependent()),
                    hard.contains(draft) ? EdgeStrength.HARD : EdgeStrength.SOFT,
                    EdgeProvenance.CURATED));
        }
        return List.copyOf(edges);
    }

    private static List<PlanRequest.Topic> topics(Structure structure, EffortMapping effort) {
        List<PlanRequest.Topic> topics = new ArrayList<>(structure.ids().size());
        for (int index = 0; index < structure.ids().size(); index++) {
            UUID id = structure.ids().get(index);
            int tier = effort.tierSent(structure.latentTiers().get(id));
            topics.add(new PlanRequest.Topic(id, structure.subjectOf().get(index),
                    structure.positionOf().get(index), EffortMapping.TIERS[tier],
                    effort.minutesOf(tier)));
        }
        return List.copyOf(topics);
    }

    /**
     * Uma meta por disciplina, todas vencendo no último dia.
     *
     * <p>Datas iguais pelo mesmo motivo de {@code InstanceLibrary}: o guloso lê a data como desempate
     * dentro de uma prioridade, e datas diferentes variariam um segundo critério junto com o primeiro.
     */
    private static List<PlanRequest.Goal> goals(int subjects, ImportanceSpread importance,
            LocalDate end) {

        int[] priorities = importance.priorities(subjects);
        List<PlanRequest.Goal> goals = new ArrayList<>(subjects);
        for (int subject = 0; subject < subjects; subject++) {
            goals.add(new PlanRequest.Goal(subjectId(subject), end, priorities[subject]));
        }
        return goals;
    }

    private static List<PlanRequest.AvailabilitySlot> availability(int days) {
        List<PlanRequest.AvailabilitySlot> slots = new ArrayList<>(days);
        for (int day = 0; day < days; day++) {
            Instant from = START.plusDays(day).atTime(LocalTime.of(8, 0)).toInstant(ZoneOffset.UTC);
            slots.add(new PlanRequest.AvailabilitySlot(from, from.plusSeconds(WINDOW_MINUTES * 60L)));
        }
        return slots;
    }

    private static int index(int subject, int positionIndex) {
        return subject * TOPICS_PER_SUBJECT + positionIndex;
    }

    /** Posição primeiro, disciplina depois: a ordem "a favor da posição" entre disciplinas. */
    private static int positionKey(int topicIndex) {
        int subject = topicIndex / TOPICS_PER_SUBJECT;
        int position = topicIndex % TOPICS_PER_SUBJECT;
        return position * 16 + subject;
    }

    /** Identificadores fixos e legíveis; nada aqui vem de {@code UUID.randomUUID()}. */
    private static UUID topicId(int subject, int position) {
        return new UUID(0xc000000000004000L | subject, 0x8000000000000000L | position);
    }

    private static UUID subjectId(int subject) {
        return new UUID(0x6000000000004000L, 0x8000000000000000L | subject);
    }
}
