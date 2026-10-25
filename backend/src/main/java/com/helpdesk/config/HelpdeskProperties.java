package com.helpdesk.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "helpdesk")
@Getter
@Setter
public class HelpdeskProperties {

    private Cors cors = new Cors();

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins;
    }
}