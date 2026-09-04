package com.ia.project.dynamicstudyplanner.domain;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;

import java.util.Collections;
import java.util.Map;

/**
 * O cromossomo: uma alocação completa de dias de estudo por disciplina.
 *
 * <p>Objeto de valor imutável. Depois de construído, um plano não muda — é isso que garante a
 * integridade das soluções que o algoritmo genético carrega de uma geração para a outra.
 *
 * <h2>A representação: vetor indexado, não mapa (pendência P18)</h2>
 *
 * Internamente o plano é um {@code int[]} alinhado a um {@link SubjectIndex} compartilhado:
 * {@code dias[i]} são os dias da disciplina {@code index.subject(i)}. Ler um gene é ler uma posição
 * de vetor.
 *
 * <p>Era um {@code Map<Subject, Integer>}. A troca é o fecho da pendência <b>P18</b>, e o motivo
 * está medido: com mapa, cada gene custava um cálculo de hash, um desembrulho de {@code Integer} e,
 * na escrita, a alocação de um nó — ~72 ns por gene, sobre 474 mil recombinações no pior caso que a
 * API aceita. Como o conjunto de disciplinas é fixo durante toda a otimização, esse custo era
 * pagamento por uma flexibilidade que a evolução não usa. Ver {@link SubjectIndex} para o que a
 * mudança fez com a ordem dos genes — e por que ela deixou o resultado <i>menos</i> dependente de
 * detalhes fora do nosso controle, não mais.
 *
 * <h2>O mapa continua existindo, na fronteira</h2>
 *
 * {@link #getDaysPerSubject()} reconstrói o mapa sob demanda, para o mapeador da API, o gerador de
 * cronograma e os testes. <b>Não o use dentro da evolução</b>: lá ele aloca um mapa por chamada,
 * que é exatamente o custo que esta representação existe para não pagar. Dentro do laço, use
 * {@link #daysAt(int)} sobre {@link #getIndex()}.
 */
public class StudyPlan {

    private final SubjectIndex index;

    /** Dias por posição, alinhado a {@link #index}. Nunca sai desta classe. */
    private final int[] days;

    /** Soma dos dias alocados, calculada na construção. Ver a nota no construtor canônico. */
    private final int totalDays;

    /**
     * Construtor canônico. <b>Recebe a posse do vetor</b>: quem constrói um plano entrega o vetor e
     * não o altera mais.
     *
     * <h2>Por que a posse, e não uma cópia</h2>
     *
     * A versão segura por construção copiaria o vetor aqui. A cópia defensiva do <i>mapa</i>
     * equivalente foi implementada e medida na etapa 05b, e custava mais do que economizava —
     * planos são construídos uma vez por descendente, meio milhão de vezes no pior caso:
     *
     * <pre>
     * pior caso (24 disc., 1000 ger., 500 pop.), mediana de 9 execuções:
     *   sem memoização, sem cópia .................. 1385 ms   (linha de base)
     *   com memoização E cópia defensiva ........... 1688 ms   PIOR em 22 %
     *   com memoização, sem cópia .................. 1318 ms   melhor em 4,8 %
     * </pre>
     *
     * <p>Um {@code int[]} é mais barato de copiar que um {@code LinkedHashMap}, então o número
     * exato mudaria; a decisão, não. A escolha foi o contrato explícito mais o teste de invariante
     * ({@code domain/StudyPlanInvarianteTest}, que verifica sobre planos produzidos pelos operadores
     * genéticos reais que {@link #getTotalDays()} bate com a soma dos genes), e não a proteção paga
     * em todo descendente. Os pontos de construção em {@code src/main} passam um vetor recém-criado
     * que sai de escopo em seguida — nenhum retém a referência.
     *
     * <h2>Estratégia de invalidação do total</h2>
     *
     * <b>Não existe, e isso é uma afirmação, não um esquecimento.</b> Não há método nesta classe que
     * altere a alocação: os campos são {@code final}, o vetor nunca é exposto, e o total é derivado
     * uma vez aqui. Não há evento capaz de tornar o valor obsoleto, logo não há o que invalidar.
     *
     * <p>O dia em que alguém acrescentar um método que mude a alocação, este cálculo precisa mudar
     * junto — e é por isso que ele mora no construtor, onde é impossível não ver.
     *
     * @param index ordem canônica dos genes, compartilhada por toda a população
     * @param days  dias por posição, alinhado ao índice; a posse passa para o plano
     * @throws IllegalArgumentException se o vetor não tiver o tamanho do índice, o que significa que
     *                                  os dois falam de conjuntos diferentes de disciplinas
     */
    public StudyPlan(SubjectIndex index, int[] days) {
        this.index = index == null ? SubjectIndex.of(null) : index;
        this.days = days == null ? new int[0] : days;
        if (this.days.length != this.index.size()) {
            throw new IllegalArgumentException(
                    "Plano incoerente: o indice tem " + this.index.size()
                            + " disciplina(s) e o vetor de dias tem " + this.days.length + " posicao(oes).");
        }

        int soma = 0;
        for (int dia : this.days) {
            soma += dia;
        }
        this.totalDays = soma;
    }

    /**
     * Construtor de fronteira: monta um plano a partir de um mapa por disciplina.
     *
     * <p>Deriva um {@link SubjectIndex} próprio, na ordem de iteração do mapa recebido. É o caminho
     * do plano tático, dos mapeadores e dos testes — <b>não</b> o da evolução, que compartilha um
     * índice único entre todos os indivíduos. Um plano construído por aqui é correto em tudo, mas
     * seus genes não estão alinhados aos de outro plano, e os operadores genéticos detectam isso
     * (ver {@code RepairingCrossover}).
     *
     * @param daysPerSubject dias por disciplina; {@code null} produz um plano vazio
     */
    public StudyPlan(Map<Subject, Integer> daysPerSubject) {
        this(SubjectIndex.of(daysPerSubject == null ? null : daysPerSubject.keySet()), daysPerSubject);
    }

    /**
     * Ponte entre os dois construtores públicos. Existe para que o índice seja derivado <b>uma
     * vez</b> e o vetor seja projetado contra essa mesma instância — Java não deixa executar nada
     * antes de {@code this(...)}, então sem este passo a ordem seria calculada duas vezes.
     */
    private StudyPlan(SubjectIndex index, Map<Subject, Integer> daysPerSubject) {
        this(index, index.projectInts(daysPerSubject, 0));
    }

    /** @return a ordem canônica dos genes deste plano */
    public SubjectIndex getIndex() {
        return index;
    }

    /**
     * Lê um gene por posição. É o acesso do caminho quente.
     *
     * @param posicao posição do gene, de {@code 0} a {@code getIndex().size() - 1}
     * @return os dias alocados nessa posição
     */
    public int daysAt(int posicao) {
        return days[posicao];
    }

    /**
     * Lê um gene por posição <b>de outra ordem</b>, sem exigir que o plano esteja alinhado a ela.
     *
     * <p>É o acesso da avaliação de fitness, que recebe um plano qualquer e percorre as posições do
     * índice do contexto. Quando o plano já está nessa ordem — o caso de todo indivíduo da evolução
     * — é uma leitura de vetor; quando não está, cai na busca pela disciplina. A verificação é uma
     * comparação de referência, não uma busca com hash.
     *
     * @param ordem a ordem em que a posição é expressa
     * @param posicao posição do gene nessa ordem
     * @return os dias alocados, ou 0 se a disciplina dessa posição não estiver no plano
     */
    public int daysAt(SubjectIndex ordem, int posicao) {
        return ordem == index ? days[posicao] : getDaysForSubject(ordem.subject(posicao));
    }

    /**
     * Copia os genes deste plano para um vetor alinhado à ordem pedida.
     *
     * <p>Quando o plano já está nessa ordem — o caso de todo indivíduo da evolução, que compartilha
     * o índice do contexto — é uma cópia de vetor. Quando não está, cada gene é reposicionado pela
     * disciplina. É o que permite aos operadores genéticos aceitar um plano vindo da fronteira sem
     * abrir mão do caminho rápido no laço.
     *
     * @param ordem a ordem canônica desejada
     * @return vetor de tamanho {@code ordem.size()}; posições cuja disciplina falte neste plano
     *         valem 0
     */
    public int[] genesAlignedTo(SubjectIndex ordem) {
        if (ordem == index) {
            return days.clone();
        }
        int[] alinhado = new int[ordem.size()];
        for (int i = 0; i < alinhado.length; i++) {
            alinhado[i] = getDaysForSubject(ordem.subject(i));
        }
        return alinhado;
    }

    /**
     * Dias alocados a uma disciplina específica.
     *
     * @param subject a disciplina consultada
     * @return os dias alocados, ou 0 se a disciplina não estiver no plano
     */
    public int getDaysForSubject(Subject subject) {
        int posicao = index.positionOf(subject);
        return posicao < 0 ? 0 : days[posicao];
    }

    /**
     * Total de dias do plano inteiro.
     *
     * @return a soma dos dias alocados em todas as disciplinas
     */
    public int getTotalDays() {
        return totalDays;
    }

    /**
     * Reconstrói o mapa de dias por disciplina, na ordem canônica.
     *
     * <p><b>Aloca um mapa a cada chamada.</b> É para a fronteira — resposta da API, geração de
     * cronograma, testes. Dentro da evolução, use {@link #daysAt(int)}.
     *
     * @return mapa somente leitura, na ordem de {@link #getIndex()}
     */
    public Map<Subject, Integer> getDaysPerSubject() {
        return Collections.unmodifiableMap(index.toMap(days));
    }

    /**
     * Verifica se o plano respeita todos os pisos de dias mínimos informados.
     * <p>
     * Traz a validação para dentro do objeto de domínio, em vez de deixá-la no arcabouço do AG.
     *
     * @param minimumDaysPerSubject pisos por disciplina
     * @return {@code true} se nenhum piso for violado
     */
    public boolean meetsMinimumConstraints(Map<Subject, Integer> minimumDaysPerSubject) {
        for (int i = 0; i < days.length; i++) {
            int minimo = minimumDaysPerSubject.getOrDefault(index.subject(i), 1);
            if (days[i] < minimo) {
                return false;
            }
        }
        return true;
    }
}
