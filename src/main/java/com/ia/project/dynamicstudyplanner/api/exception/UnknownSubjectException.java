package com.ia.project.dynamicstudyplanner.api.exception;

import java.util.List;

/**
 * O perfil do estudante declarou lacuna de conhecimento para uma disciplina que não existe no edital
 * enviado.
 *
 * <h2>Por que esta exceção existe</h2>
 *
 * O contrato aceita {@code knowledgeGaps} como um mapa de nome de disciplina para nota, sem qualquer
 * referência cruzada com as disciplinas declaradas no edital. Antes da etapa 02b, um nome que não
 * batesse era resolvido para {@code null} pelo {@code StudentProfileMapper} e virava chave nula no
 * mapa de lacunas; duas dessas colidiam e o {@code Collectors.toMap} lançava
 * {@code IllegalStateException} com a mensagem <i>"Duplicate key null (attempted merging values 4.5
 * and 2.0)"</i>.
 *
 * <p>Aqueles números são <b>as notas de autoavaliação do estudante</b>. A exceção subia até o
 * catch-all, que registrava a pilha completa em nível {@code ERROR} num log JSON destinado a um
 * agregador externo. Ou seja: um erro de grafia do próprio aluno publicava as notas dele fora do
 * processo, e ele ainda recebia <b>500</b> como resposta. É o achado S3 de
 * {@code docs/qualidade/02-diagnostico-seguranca.md}, e a pendência P5 de
 * {@code 01b-correcao-testes.md}.
 *
 * <h2>O contrato desta classe</h2>
 *
 * Carrega <b>apenas os nomes das disciplinas não reconhecidas</b>. Nomes de disciplina vêm do edital
 * que o próprio cliente enviou e fazem parte do contrato público — não são autoavaliação, não são
 * identificação, não são estado psicológico. <b>Nunca carregar as notas.</b> Essa é a diferença
 * entre uma mensagem de erro útil e um vazamento.
 */
public class UnknownSubjectException extends RuntimeException {

    private final List<String> unknownSubjects;

    public UnknownSubjectException(List<String> unknownSubjects) {
        super("Knowledge gaps reference subjects that are not present in the exam: " + unknownSubjects);
        this.unknownSubjects = List.copyOf(unknownSubjects);
    }

    public List<String> getUnknownSubjects() {
        return unknownSubjects;
    }
}
