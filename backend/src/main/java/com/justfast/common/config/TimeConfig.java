package com.justfast.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Background jobs (payment reconciliation) are not needed when only running migrations. */
    @Configuration
    @EnableScheduling
    @Profile("!migrate & !test")
    static class SchedulingConfig {
    }
}
