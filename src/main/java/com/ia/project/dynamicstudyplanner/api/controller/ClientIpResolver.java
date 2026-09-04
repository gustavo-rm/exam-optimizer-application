package com.ia.project.dynamicstudyplanner.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Decide qual endereço identifica o cliente, e como esse endereço aparece em log.
 *
 * <h2>O problema que esta classe resolve (achado S12)</h2>
 *
 * Até a etapa 02b o {@code RateLimitingFilter} lia o cabeçalho {@code X-Forwarded-For} e usava o
 * primeiro valor como chave do balde de requisições, caindo para o endereço da conexão apenas quando
 * o cabeçalho estava ausente. <b>Cabeçalho é dado enviado pelo cliente.</b> Com o serviço exposto
 * diretamente — que é o cenário atual, já que não há implantação com proxy —, qualquer chamador
 * mandava um valor diferente a cada requisição e obtinha um balde novo, anulando por completo o
 * limite. Era o caminho mais barato para exaustão de CPU.
 *
 * <h2>A regra adotada</h2>
 *
 * O endereço da conexão ({@code getRemoteAddr()}) é a única fonte que o cliente não escolhe, então é
 * o padrão. {@code X-Forwarded-For} só é considerado quando a conexão vem de um proxy que o operador
 * declarou confiável em {@code api.trusted-proxies}. A lista é <b>vazia por padrão</b>: a
 * configuração segura é a que vale sem ninguém configurar nada.
 *
 * <p>Quando a lista está preenchida e a conexão vem de um proxy confiável, a cadeia do
 * {@code X-Forwarded-For} é percorrida <b>da direita para a esquerda</b>, descartando proxies
 * confiáveis, até achar a primeira entrada que não seja um deles. Esse é o cliente real: os valores
 * à esquerda dele podem ter sido forjados por ele mesmo, e por isso são ignorados.
 *
 * <h2>Mascaramento em log (achado S1)</h2>
 *
 * Endereço IP é dado pessoal. {@link #maskForLogging(String)} devolve a forma reduzida usada em
 * qualquer registro: em IPv4 o último octeto vira {@code x}; em IPv6 só os três primeiros grupos são
 * mantidos. A finalidade — detectar e conter abuso — continua atendida, porque a granularidade de
 * rede basta para reconhecer um padrão de ataque, e o que se perde é justamente a identificação do
 * assinante individual.
 *
 * <p>O balde de requisições continua usando o endereço <b>completo</b>: mascarar a chave juntaria
 * até 256 clientes de IPv4 no mesmo balde e transformaria o limite numa negação de serviço entre
 * vizinhos. Mascara-se o que sai para o log, não o que fica na memória do processo.
 */
@Component
public class ClientIpResolver {

    /** Cabeçalho padrão de encadeamento de proxies. */
    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private final Set<String> trustedProxies;

    public ClientIpResolver(@Value("${api.trusted-proxies:}") String trustedProxiesCsv) {
        this.trustedProxies = parse(trustedProxiesCsv);
    }

    private static Set<String> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(valor -> !valor.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /** Lista de proxies confiáveis configurada, para inspeção e teste. */
    public Set<String> getTrustedProxies() {
        return trustedProxies;
    }

    /**
     * Resolve o endereço que identifica o cliente desta requisição.
     *
     * @return o endereço da conexão, ou o primeiro endereço não confiável da cadeia de
     *         {@code X-Forwarded-For} quando — e somente quando — a conexão vem de um proxy declarado
     *         confiável
     */
    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        // Sem proxy confiavel declarado, o cabecalho nao e considerado em hipotese alguma.
        if (trustedProxies.isEmpty() || remoteAddr == null || !trustedProxies.contains(remoteAddr)) {
            return remoteAddr;
        }

        String forwarded = request.getHeader(FORWARDED_FOR);
        if (forwarded == null || forwarded.isBlank()) {
            return remoteAddr;
        }

        List<String> cadeia = Arrays.stream(forwarded.split(","))
                .map(String::trim)
                .filter(valor -> !valor.isEmpty())
                .toList();

        // Da direita para a esquerda: a primeira entrada que nao e proxy confiavel e o cliente real.
        for (int i = cadeia.size() - 1; i >= 0; i--) {
            if (!trustedProxies.contains(cadeia.get(i))) {
                return cadeia.get(i);
            }
        }

        // Cadeia inteira composta de proxies confiaveis: nao ha cliente identificavel alem deles.
        return remoteAddr;
    }

    /**
     * Reduz um endereço à granularidade de rede, para registro em log.
     *
     * @return {@code "192.168.1.x"} para IPv4, {@code "2001:db8:85a3:x"} para IPv6, {@code "unknown"}
     *         quando não há endereço
     */
    public static String maskForLogging(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        if (ip.contains(":")) {
            String[] grupos = ip.split(":");
            if (grupos.length <= 3) {
                return ip;
            }
            return String.join(":", grupos[0], grupos[1], grupos[2]) + ":x";
        }
        int ultimoPonto = ip.lastIndexOf('.');
        if (ultimoPonto < 0) {
            return "unknown";
        }
        return ip.substring(0, ultimoPonto + 1) + "x";
    }
}
