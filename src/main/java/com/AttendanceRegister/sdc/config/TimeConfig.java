package com.AttendanceRegister.sdc.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    // Injected wherever "now" matters, so tests can control the time
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
