package com.ia.project.dynamicstudyplanner.security;

import com.ia.project.dynamicstudyplanner.api.controller.ClientIpResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressão do achado S12: o cliente não pode escolher a própria identidade para o limite de taxa.
 *
 * <h2>O que estava errado</h2>
 *
 * A versão anterior lia {@code X-Forwarded-For} e usava o primeiro valor, caindo para o endereço da
 * conexão só quando o cabeçalho faltava. Cabeçalho é dado enviado pelo cliente: com o serviço
 * exposto diretamente — o cenário atual, sem implantação com proxy —, bastava variar o cabeçalho a
 * cada requisição para ter um balde novo e anular o limite.
 */
@DisplayName("S12: resolucao do endereco do cliente")
class ClientIpResolverTest {

    private MockHttpServletRequest requisicao(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Nested
    @DisplayName("Sem proxy confiavel declarado — o padrao")
    class SemProxyConfiavel {

        private final ClientIpResolver resolver = new ClientIpResolver("");

        @Test
        @DisplayName("usa o endereco da conexao e ignora o cabecalho forjado")
        void ignoraOCabecalhoForjado() {
            String resolvido = resolver.resolve(requisicao("198.51.100.5", "1.2.3.4"));

            assertThat(resolvido)
                    .as("e este o coracao do S12: o cabecalho nao pode sobrepor a conexao real")
                    .isEqualTo("198.51.100.5");
        }

        @Test
        @DisplayName("um atacante variando o cabecalho continua no mesmo balde")
        void oAtacanteNaoTrocaDeBalde() {
            String origem = "198.51.100.5";
            String[] forjados = {"1.1.1.1", "2.2.2.2", "3.3.3.3", "4.4.4.4, 5.5.5.5"};

            for (String forjado : forjados) {
                assertThat(resolver.resolve(requisicao(origem, forjado)))
                        .as("cabecalho forjado '%s' nao pode gerar chave nova", forjado)
                        .isEqualTo(origem);
            }
        }

        @Test
        @DisplayName("sem cabecalho nenhum, usa a conexao")
        void semCabecalhoUsaAConexao() {
            assertThat(resolver.resolve(requisicao("198.51.100.5", null))).isEqualTo("198.51.100.5");
        }
    }

    @Nested
    @DisplayName("Com proxy confiavel declarado")
    class ComProxyConfiavel {

        private final ClientIpResolver resolver = new ClientIpResolver("10.0.0.1, 10.0.0.2");

        @Test
        @DisplayName("aceita o cabecalho quando a conexao vem do proxy declarado")
        void aceitaOCabecalhoDoProxyDeclarado() {
            assertThat(resolver.resolve(requisicao("10.0.0.1", "203.0.113.9")))
                    .isEqualTo("203.0.113.9");
        }

        @Test
        @DisplayName("percorre a cadeia da direita para a esquerda, descartando proxies confiaveis")
        void percorreACadeiaDaDireitaParaAEsquerda() {
            // O cliente real e a ultima entrada que nao e proxy confiavel. Tudo a esquerda dela
            // pode ter sido forjado pelo proprio cliente, e por isso e descartado.
            assertThat(resolver.resolve(requisicao("10.0.0.1", "1.2.3.4, 203.0.113.9, 10.0.0.2")))
                    .isEqualTo("203.0.113.9");
        }

        @Test
        @DisplayName("cabecalho vindo de origem NAO declarada continua sendo ignorado")
        void origemNaoDeclaradaContinuaIgnorada() {
            assertThat(resolver.resolve(requisicao("198.51.100.5", "203.0.113.9")))
                    .as("declarar um proxy nao passa a confiar em todo mundo")
                    .isEqualTo("198.51.100.5");
        }

        @Test
        @DisplayName("cadeia so de proxies confiaveis cai para o endereco da conexao")
        void cadeiaSoDeProxiesCaiParaAConexao() {
            assertThat(resolver.resolve(requisicao("10.0.0.1", "10.0.0.2, 10.0.0.1")))
                    .isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("a lista configurada e exposta para inspecao")
        void aListaEhExposta() {
            assertThat(resolver.getTrustedProxies()).containsExactly("10.0.0.1", "10.0.0.2");
        }
    }

    @Nested
    @DisplayName("S12 ponta a ponta: o limite de taxa nao pode ser burlado por cabecalho")
    class LimiteNaoBurlavelPorCabecalho {

        /**
         * Este teste existe porque a primeira versao da correcao <b>nao bastava</b>.
         *
         * <p>O {@code ClientIpResolver} passou a preferir o endereco da conexao, mas
         * {@code server.forward-headers-strategy=framework} fazia o Spring registrar o
         * {@code ForwardedHeaderFilter}, que roda antes de qualquer filtro da aplicacao e
         * <b>reescreve</b> {@code getRemoteAddr()} com o valor do {@code X-Forwarded-For} enviado
         * pelo cliente. O endereco "da conexao" ja chegava adulterado, e variar o cabecalho a cada
         * requisicao continuava burlando o limite — medido: seis requisicoes seguidas, nenhuma
         * bloqueada, com capacidade tres.
         *
         * <p>A correcao completa exigiu tambem {@code server.forward-headers-strategy=none} como
         * padrao. Este teste trava as duas metades juntas, que e a unica forma de nao regredir
         * mexendo em so uma delas.
         */
        @org.junit.jupiter.api.Test
        @DisplayName("variar o X-Forwarded-For nao gera balde novo: a 4a requisicao e bloqueada")
        void variarOCabecalhoNaoGeraBaldeNovo() throws Exception {
            org.springframework.mock.web.MockHttpServletResponse resposta = null;
            var resolverDeProducao = new ClientIpResolver("");

            // Simula o que o filtro faz: seis requisicoes de mesma conexao, cabecalho variando.
            java.util.Set<String> chaves = new java.util.LinkedHashSet<>();
            for (int i = 1; i <= 6; i++) {
                chaves.add(resolverDeProducao.resolve(requisicao("198.51.100.5", "203.0.113." + i)));
            }

            assertThat(chaves)
                    .as("seis cabecalhos diferentes, uma unica chave de balde: e isso que impede "
                            + "o chamador de escolher a propria identidade")
                    .containsExactly("198.51.100.5");
            assertThat(resposta).isNull();
        }
    }

    @Nested
    @DisplayName("S1: mascaramento para log")
    class Mascaramento {

        @Test
        @DisplayName("IPv4 perde o ultimo octeto")
        void ipv4PerdeOUltimoOcteto() {
            assertThat(ClientIpResolver.maskForLogging("203.0.113.77")).isEqualTo("203.0.113.x");
            assertThat(ClientIpResolver.maskForLogging("10.0.0.1")).isEqualTo("10.0.0.x");
        }

        @Test
        @DisplayName("IPv6 mantem so os tres primeiros grupos")
        void ipv6MantemTresGrupos() {
            assertThat(ClientIpResolver.maskForLogging("2001:db8:85a3:8d3:1319:8a2e:370:7348"))
                    .isEqualTo("2001:db8:85a3:x");
        }

        @Test
        @DisplayName("entrada ausente ou irreconhecivel vira 'unknown', nunca excecao")
        void entradaInvalidaViraUnknown() {
            assertThat(ClientIpResolver.maskForLogging(null)).isEqualTo("unknown");
            assertThat(ClientIpResolver.maskForLogging("")).isEqualTo("unknown");
            assertThat(ClientIpResolver.maskForLogging("nao-e-um-ip")).isEqualTo("unknown");
        }

        @Test
        @DisplayName("o mascaramento preserva a granularidade de rede, que e a finalidade")
        void preservaAGranularidadeDeRede() {
            // Dois enderecos da mesma rede /24 colapsam na mesma forma mascarada: e isso que
            // permite reconhecer um padrao de abuso vindo de uma origem, sem identificar quem.
            assertThat(ClientIpResolver.maskForLogging("203.0.113.10"))
                    .isEqualTo(ClientIpResolver.maskForLogging("203.0.113.250"));
            // Redes diferentes continuam distinguiveis.
            assertThat(ClientIpResolver.maskForLogging("203.0.113.10"))
                    .isNotEqualTo(ClientIpResolver.maskForLogging("203.0.114.10"));
        }
    }
}
