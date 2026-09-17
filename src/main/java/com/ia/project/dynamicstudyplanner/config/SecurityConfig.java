package com.ia.project.dynamicstudyplanner.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuração de segurança HTTP da aplicação.
 *
 * <h2>Acesso público, e o que isso significa hoje</h2>
 *
 * Todo {@code /api/v1/**} e a documentação OpenAPI são {@code permitAll()}: <b>não há autenticação
 * de nenhum tipo</b>, nem neste repositório nem delegada a um componente externo. A verificação está
 * documentada em {@code docs/qualidade/02b-correcao-seguranca.md}, item 0(a). Qualquer chamador
 * direto alcança o endpoint que recebe nome, autoavaliação de desempenho e estado psicológico do
 * estudante.
 *
 * <h2>Exigência de TLS (achado S11)</h2>
 *
 * A terminação de TLS é responsabilidade de infraestrutura e não pode ser resolvida por este código
 * — a aplicação não tem, nem deveria ter, o certificado. O que <b>é</b> responsabilidade daqui é a
 * postura defensiva: quando o operador declara que existe um proxy TLS na frente, a aplicação passa
 * a recusar requisição em claro e a emitir HSTS, em vez de servir HTTP silenciosamente.
 *
 * <p>Isso é controlado por {@code api.security.require-https}, <b>desligado por padrão</b>. O padrão
 * desligado é deliberado e não é o padrão inseguro: ligar sem proxy real quebraria todo acesso local
 * e — pior — a decisão passaria a depender do cabeçalho {@code X-Forwarded-Proto}, que só tem valor
 * quando vem de um proxy confiável. Por isso a chave <b>só deve ser ligada junto com</b>
 * {@code api.trusted-proxies}; as duas descrevem a mesma premissa de implantação. Ver
 * {@code ClientIpResolver}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Liga a exigência de HTTPS e o cabeçalho HSTS. Só deve ser ligada quando existir de fato um
     * proxy TLS na frente, declarado em {@code api.trusted-proxies}.
     */
    @Value("${api.security.require-https:false}")
    private boolean requireHttps;

    /**
     * Validade do HSTS, em segundos. Padrão de um ano, como recomenda a prática corrente. Só tem
     * efeito quando {@code api.security.require-https} está ligada.
     */
    @Value("${api.security.hsts-max-age-seconds:31536000}")
    private long hstsMaxAgeSeconds;

    /**
     * Defines the security filter chain for the application.
     *
     * <h2>Por que existe autenticação básica aqui (achados F9 e S5)</h2>
     *
     * Até a etapa 05b esta configuração terminava em {@code anyRequest().authenticated()} sem
     * declarar <b>nenhum</b> mecanismo de autenticação. Como a classe define um
     * {@code SecurityFilterChain} próprio, o Spring Boot não acrescenta os padrões dele — então não
     * havia forma de apresentar credencial. O efeito prático: todo caminho protegido respondia
     * <b>403 permanente</b>, para qualquer um, com ou sem senha.
     *
     * <p>Duas consequências reais foram registradas antes desta correção:
     *
     * <ul>
     *   <li><b>S5</b> ({@code docs/qualidade/02-diagnostico-seguranca.md}) —
     *       {@code management.endpoint.health.probes.enabled=true} declara sondas de liveness e
     *       readiness para um orquestrador, mas elas respondiam 403. Uma sonda de Kubernetes
     *       concluiria que a aplicação está morta e reiniciaria um processo saudável.</li>
     *   <li><b>F9</b> ({@code docs/qualidade/05-diagnostico-performance.md}) — a métrica
     *       {@code dynamicstudyplanner.optimization.duration} é publicada e {@code prometheus} está
     *       na lista de exposição, mas {@code /actuator/prometheus} respondia 403. O tempo interno
     *       da otimização não podia ser lido de fora, e ~15 % do tempo de requisição ficou sem
     *       atribuição no diagnóstico por causa disso.</li>
     * </ul>
     *
     * <p>A correção é mínima e não afrouxa nada: {@code /actuator/health} passa a ser público
     * porque uma sonda não pode carregar credencial e a resposta não revela nada além de UP/DOWN;
     * {@code /actuator/prometheus} <b>continua exigindo autenticação</b>, e agora existe um
     * mecanismo — HTTP Basic — pelo qual apresentá-la.
     *
     * <p><b>Pendência P19:</b> a credencial ainda é o usuário {@code user} com senha gerada a cada
     * inicialização (achado R14 do inventário), o que serve para desenvolvimento e não para
     * produção. Definir uma credencial real para o coletor de métricas é decisão de implantação, e
     * hoje não há implantação nenhuma (R2).
     *
     * @param http The HttpSecurity object to configure.
     * @return The configured SecurityFilterChain.
     * @throws Exception If an error occurs during configuration.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Disable CSRF (Cross-Site Request Forgery) protection.
                // This is common for stateless REST APIs that are not called from a browser form.
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Configure authorization rules for HTTP requests.
                .authorizeHttpRequests(auth -> auth
                        // Allow OpenAPI and Swagger UI to be accessed without authentication.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Allow all requests to endpoints under "/api/v1/**" to be accessed without authentication.
                        .requestMatchers("/api/v1/**").permitAll()
                        // Sondas de liveness/readiness: um orquestrador as consulta sem credencial.
                        // Elas nao revelam nada — respondem UP ou DOWN. Ver a nota sobre o F9/S5
                        // abaixo; management.endpoint.health.probes.enabled=true so faz sentido se
                        // alguem puder de fato consulta-las.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // You can add other rules here, for example:
                        // .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // By default, deny any other request that is not explicitly matched.
                        // Isso inclui /actuator/prometheus, que continua exigindo credencial.
                        .anyRequest().authenticated()
                )

                // Sem isto, "authenticated()" era inalcancavel: nao havia mecanismo pelo qual
                // apresentar credencial, entao toda requisicao a um caminho protegido terminava em
                // 403 permanente, inclusive para quem tivesse a senha. Ver a nota do F9/S5.
                .httpBasic(Customizer.withDefaults());

        if (requireHttps) {
            // Recusa requisicao em claro. Com server.forward-headers-strategy=framework, a decisao
            // usa o X-Forwarded-Proto — que so e confiavel vindo de um proxy declarado em
            // api.trusted-proxies. Por isso as duas chaves andam juntas.
            http.requiresChannel(canal -> canal.anyRequest().requiresSecure());

            // HSTS: instrui o navegador a nunca mais acessar este dominio por HTTP puro.
            http.headers(cabecalhos -> cabecalhos
                    .httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(hstsMaxAgeSeconds)));
        }

        return http.build();
    }
}
