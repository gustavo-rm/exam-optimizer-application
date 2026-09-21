package com.ia.project.dynamicstudyplanner.domain;

/**
 * A unidade em que o otimizador planeja: uma coisa que o aluno senta para estudar.
 *
 * <h2>Por que este tipo existe</h2>
 *
 * O cromossomo já era agnóstico quanto ao que um gene <i>significa</i> — ele é um {@code int[]}
 * alinhado a uma ordem canônica. O que não era agnóstico eram os nomes: o núcleo falava em
 * {@code Subject}, um conceito de concurso, enquanto o {@code sinapse-platform} planeja em tópicos
 * identificados por UUID. Este record é o que o núcleo fala agora.
 *
 * <h2>Ele não é uma abstração genérica</h2>
 *
 * Não há interface, nem estratégia, nem fábrica por trás dele. Este otimizador existe para servir o
 * SINAPSE, a linha de concurso não continua, e um núcleo parametrizado com dois adaptadores
 * permanentes seria abstração comprada para um futuro que foi descartado de forma explícita. O
 * caminho de concurso sobrevive por um único mapeador de fronteira,
 * {@code domain.exam.SubjectPlanningItemMapper}, <b>removido em EOA-4b</b>.
 *
 * <h2>{@code difficultyBand} é documentado 1..5 e não validado, de propósito</h2>
 *
 * A API já recusa qualquer coisa fora de 1..5 ({@code SubjectDto.cognitiveLoad}), então a checagem
 * existe onde o dado entra. Repeti-la aqui <b>mudaria comportamento</b> em vez de acrescentar
 * segurança: {@code LearningModel} já põe piso 1 na banda por conta própria, e testes e bancadas
 * constroem instâncias fora do intervalo de propósito, para exercitar as bordas. Um record que
 * lançasse exceção reprovaria esses casos sem tornar uma única requisição de produção mais segura.
 *
 * @param id             identificador estável. O UUID do tópico no caminho da plataforma; o nome da
 *                       disciplina no caminho de concurso, onde é injetivo porque a API já recusa
 *                       um edital com duas disciplinas de mesmo nome
 * @param name           rótulo legível, o que chega ao aluno
 * @param difficultyBand dificuldade ordinal, de 1 (mais fácil) a 5 (mais difícil). Ver acima por
 *                       que o intervalo é documentado e não imposto
 */
public record PlanningItem(String id, String name, int difficultyBand) {
}
