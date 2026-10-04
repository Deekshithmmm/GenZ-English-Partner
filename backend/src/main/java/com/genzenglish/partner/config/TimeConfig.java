package com.genzenglish.partner.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /** Injected everywhere we read "now", so tests can pin time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
