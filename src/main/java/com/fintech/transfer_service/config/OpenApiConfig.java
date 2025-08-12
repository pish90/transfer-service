package com.fintech.transfer_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!prod")
public class OpenApiConfig {

    @Bean
    public OpenAPI transferServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Transfer Service API")
                        .description("Public API for payment transfers")
                        .version("1.0.0"));
    }
}