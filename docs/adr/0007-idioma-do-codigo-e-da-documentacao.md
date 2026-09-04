# ADR-0007 — Inglês para o que a máquina lê, português para o que a pessoa lê

- **Estado:** Aceito
- **Data:** 2026-09-03
- **Etapa:** 04b — correção de legibilidade e manutenibilidade
- **Achado atendido:** L7 (idioma misturado sem convenção declarada), que refina o **E11**

## Contexto

O diagnóstico da etapa 03 (achado **E11**) registrou idioma misturado sem regra escrita, e mediu
**6 de 118 classes** de produção com Javadoc em português. A etapa 04 mediu de novo: **31 de 122** —
um quarto do código de produção.

As 31 são exatamente as classes tocadas pelas etapas 01b, 02b, 03b, 03c, 03d e 03e desta varredura.
**A escalada é consequência direta deste trabalho.** Cada relatório foi escrito em português a
pedido, e o português foi junto para o Javadoc sem que ninguém decidisse que deveria ir.

O resultado é um arquivo típico de hoje:

```java
/**
 * Repara um cronograma que deixou de fora uma revisão obrigatória.   <- português
 */
public class SpacedRepetitionRepairer {                              <- inglês
    throw new DomainException("Total minimum study days required…");  <- inglês
}
```

E o teste correspondente com `@DisplayName` em português e método em português.

**O problema nunca foi a mistura em si.** Ela é, na prática, coerente: identificadores em inglês,
explicação em português. O problema é que **isso não está escrito em lugar nenhum**, então quem
chega não tem como saber se o português é a regra nova ou uma anomalia das últimas semanas — e a
dúvida se resolve por imitação do arquivo mais próximo, que é como convenções se desfazem.

## Decisão

**Adotar como convenção o que o código já pratica, e escrevê-la.**

| Elemento | Idioma | Por quê |
|---|---|---|
| Nome de classe, método, variável, constante, pacote | **inglês** | É o idioma do ecossistema: `List`, `Optional`, `@Override`. Misturar `calcularFadiga` com `getExpectedEnergyLevel` na mesma cadeia de chamadas é pior do que qualquer um dos dois puro |
| Mensagem de exceção que chega ao cliente | **inglês** | É contrato público. `detail` de um RFC 7807 sai pela API e vai para consumidores que não se sabe quem são |
| Chave de log e nome de métrica | **inglês** | São consumidos por agregador e painel, e frequentemente por quem opera, não por quem escreve |
| **Javadoc e comentário** | **português** | É explicação para a equipe. Explicar "por quê" no idioma em que se pensa produz texto melhor — e o valor de um comentário está inteiro na clareza dele |
| `@DisplayName` e nome de método de teste | **português** | O relatório de teste é lido por pessoas, e uma falha precisa ser compreendida rápido |
| Documentação em `docs/` | **português** | Já é, sem exceção |

Regra prática, em uma frase: **o que a máquina lê ou o que sai pela API é em inglês; o que só uma
pessoa lê é em português.**

### O que isso implica para o código existente

**Nada é renomeado em massa.** As 91 classes com Javadoc em inglês continuam como estão. Traduzi-las
seria um diff enorme, sem ganho de leitura — quem lê inglês técnico lê as duas versões — e apagaria
o `git blame` de todo o repositório, exatamente a razão pela qual esta mesma etapa recusou um
formatador que reescreve tudo (ver §1 de `docs/qualidade/04b-correcao-escrita.md`).

A convenção vale para **código novo e código tocado**. A conversão acontece por atrito natural: um
arquivo modificado com propósito ganha Javadoc em português quando o Javadoc já estava sendo mexido,
e não porque alguém saiu traduzindo.

## Consequências

**A favor:**

- A dúvida "em que idioma escrevo isto?" tem resposta escrita, em vez de depender do arquivo que a
  pessoa abriu primeiro.
- A mistura deixa de ser acidente e vira estrutura: dois idiomas com fronteira declarada são
  legíveis; dois idiomas sem fronteira, não.
- A explicação — que é onde este repositório mais investe, com classes que têm quase tanto Javadoc
  quanto código — fica no idioma que produz o melhor texto.

**Contra, e aceito:**

- **O repositório fica bilíngue por tempo indeterminado.** Durante a transição haverá arquivos em
  inglês ao lado de arquivos em português, e um leitor pode achar que é desleixo. O ADR existe
  também para responder isso: é transição declarada, não desleixo.
- **Fecha a porta para colaboradores que não leem português.** É uma decisão real com custo real.
  Aceita porque o projeto é acadêmico, de autoria única, e toda a documentação de engenharia já está
  em português — o custo já havia sido pago; só não estava registrado.
- **Nenhuma ferramenta verifica isto.** O Checkstyle não tem regra de idioma. A convenção depende de
  revisão humana, e o `config/checkstyle/checkstyle.xml` apenas aponta para este ADR.

## Alternativas consideradas

**Tudo em inglês, traduzindo os 31 arquivos.** Descartada: é o maior diff das duas opções, sem ganho
de leitura para quem trabalha no projeto, e desfaria a explicação mais cuidadosa escrita nas últimas
etapas — que é justamente o material mais valioso destas mudanças.

**Tudo em português, inclusive identificadores.** Descartada com mais convicção: obriga a misturar
idiomas dentro da mesma expressão (`plano.getDaysPerSubject()` viraria
`plano.getDiasPorDisciplina()`, mas `Map`, `Optional` e `@Override` continuam em inglês), e quebra a
convenção de todo o ecossistema Java.

**Deixar como está, sem regra.** Descartada porque é o estado atual, e o estado atual é o achado.
Uma mistura sem fronteira declarada tende a piorar: cada arquivo novo imita o vizinho, e o vizinho
depende de quem passou por ali por último.
