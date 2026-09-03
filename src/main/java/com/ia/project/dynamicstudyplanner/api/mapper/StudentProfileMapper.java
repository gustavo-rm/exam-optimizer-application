package com.ia.project.dynamicstudyplanner.api.mapper;

import com.ia.project.dynamicstudyplanner.api.dto.StudentProfileDto;
import com.ia.project.dynamicstudyplanner.api.exception.UnknownSubjectException;
import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.ia.project.dynamicstudyplanner.domain.StudentState;
import com.ia.project.dynamicstudyplanner.domain.Chronotype;

@Component
public class StudentProfileMapper {

    /**
     * Traduz o perfil recebido pela API para o modelo de domínio, ligando cada lacuna declarada à
     * disciplina correspondente do edital.
     *
     * <h2>Validação acrescentada na etapa 02b</h2>
     *
     * Toda lacuna precisa citar uma disciplina que exista no edital enviado. O contrato não tem como
     * garantir isso — {@code knowledgeGaps} é um mapa de {@code String} livre, sem referência cruzada
     * —, então a checagem é feita aqui, antes de qualquer coleta.
     *
     * <p>Antes desta validação, um nome não reconhecido virava chave {@code null} e dois deles
     * colidiam num {@code IllegalStateException} cuja mensagem <b>continha as notas de autoavaliação
     * do estudante</b>, que acabavam registradas em log de ERRO (achado S3 de
     * {@code docs/qualidade/02-diagnostico-seguranca.md}). Falhar cedo, com uma exceção que carrega
     * só os nomes não reconhecidos, fecha o vazamento e devolve ao cliente um <b>400</b> acionável
     * em lugar de um <b>500</b> mudo.
     *
     * @throws UnknownSubjectException se alguma lacuna citar disciplina ausente do edital
     */
    public StudentProfile toDomain(StudentProfileDto dto, List<Subject> allSubjects) {
        Map<String, Subject> subjectMap = allSubjects.stream()
                .collect(Collectors.toMap(Subject::name, Function.identity()));

        List<String> unknown = new ArrayList<>();
        for (String declaredName : dto.knowledgeGaps().keySet()) {
            if (!subjectMap.containsKey(declaredName)) {
                unknown.add(declaredName);
            }
        }
        if (!unknown.isEmpty()) {
            throw new UnknownSubjectException(unknown);
        }

        // HashMap explicito em vez de Collectors.toMap: a colisao de chaves que o toMap rejeitava
        // com uma mensagem contendo as notas do aluno nao pode mais ocorrer, ja que toda chave foi
        // validada acima, mas a construcao explicita torna essa garantia visivel.
        Map<Subject, Double> knowledgeGaps = new HashMap<>(dto.knowledgeGaps().size());
        dto.knowledgeGaps().forEach((name, gap) -> knowledgeGaps.put(subjectMap.get(name), gap));

        StudentState state = null;
        if (dto.state() != null) {
            Chronotype chronotype = dto.state().chronotype() != null
                    ? dto.state().chronotype()
                    : Chronotype.INTERMEDIATE;

            state = new StudentState(
                    dto.state().stressLevel(),
                    dto.state().fatigueLevel(),
                    dto.state().motivationLevel(),
                    chronotype
            );
        }

        return new StudentProfile(dto.name(), knowledgeGaps, dto.weeklyAvailability(), state);
    }
}
