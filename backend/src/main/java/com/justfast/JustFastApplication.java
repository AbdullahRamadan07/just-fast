package com.justfast;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JustFastApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(JustFastApplication.class, args);
        // The "migrate" profile only applies Flyway migrations (run as a Kubernetes Job before rollout).
        if (context.getEnvironment().matchesProfiles("migrate")) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
