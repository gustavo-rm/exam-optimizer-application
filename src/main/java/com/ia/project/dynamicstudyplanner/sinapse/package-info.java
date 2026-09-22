/**
 * O adaptador entre o contrato do Core e o algoritmo genético: o caminho SINAPSE.
 *
 * <h2>O que este pacote é</h2>
 *
 * A tradução, nos dois sentidos, entre {@code PlanRequest}/{@code PlanResponse} e o núcleo que
 * planeja sobre {@code PlanningItem}. É a segunda condição do experimento; a primeira é
 * {@code baseline}, e as duas respondem o mesmo {@code POST /plans} por trás de
 * {@code plan.PlanEngine}.
 *
 * <h2>A regra que governa o pacote inteiro</h2>
 *
 * <b>Nada aqui alcança um tipo que exija dado que a plataforma não envia.</b> Sem
 * {@code StudentProfile}, sem {@code StudentState}, sem {@code Exam}, e sem as três calculadoras que
 * os exigem. Não é preferência de estilo: {@code CognitiveLoadCalculator} <i>tolera</i> a ausência —
 * a lacuna média cai para 3,0 e os modificadores de estado são descartados em silêncio — e
 * {@code ImportanceCalculator} e {@code BaselineCalculator} levariam essa mesma lacuna inventada
 * para o peso da maestria, o da retenção e o piso de dias mínimos. Uma constante só reponderaria a
 * função inteira, e cada termo continuaria reportando um número.
 *
 * <p>{@code SinapseAdapterIsolationTest} lê os {@code import} deste pacote e reprova se qualquer um
 * desses tipos aparecer. É a forma forte do critério "nenhum campo é preenchido com constante":
 * proíbe o alcance, não um valor.
 *
 * <h2>Onde procurar o quê</h2>
 *
 * <ul>
 *   <li>{@link com.ia.project.dynamicstudyplanner.sinapse.EffortTierBands} — o único lugar em que
 *       {@code effortTier} vira faixa de dificuldade, e o único que recusa uma faixa desconhecida;</li>
 *   <li>{@link com.ia.project.dynamicstudyplanner.sinapse.TopicImportance} — o único ponto de
 *       cálculo de importância, para que EOA-6 o troque sem tocar em mais nada;</li>
 *   <li>{@link com.ia.project.dynamicstudyplanner.sinapse.RetentionHistory} — a janela de noventa
 *       dias e a ambiguidade que ela deixa;</li>
 *   <li>{@link com.ia.project.dynamicstudyplanner.sinapse.SinapseLoadBudget} — por que o orçamento
 *       de carga não pôde ser reaproveitado;</li>
 *   <li>{@link com.ia.project.dynamicstudyplanner.sinapse.GeneticPlanEngine} — a passada inteira,
 *       em ordem.</li>
 * </ul>
 *
 * <p>As suposições de projeto estão em {@code docs/SINAPSE_ADAPTER.md} e travadas por
 * {@code SinapseAssumptionsTest}.
 */
package com.ia.project.dynamicstudyplanner.sinapse;
