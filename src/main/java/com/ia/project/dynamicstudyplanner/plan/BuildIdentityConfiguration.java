package com.ia.project.dynamicstudyplanner.plan;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Monta a {@link BuildIdentity} a partir do {@link GitProperties} que o Spring Boot cria quando
 * {@code git.properties} está no classpath, e de nada quando não está.
 */
@Configuration(proxyBeanMethods = false)
@Profile(PlanProtocol.PROFILE)
public class BuildIdentityConfiguration {

    /**
     * @param git presente só quando o build gerou {@code git.properties}
     * @return a identidade deste build
     */
    @Bean
    public BuildIdentity buildIdentity(ObjectProvider<GitProperties> git) {
        return BuildIdentity.from(git.getIfAvailable());
    }
}
