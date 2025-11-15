package com.AttendanceRegister.sdc.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

 @Bean
 public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
     http
         .csrf(csrf -> csrf.disable()) // Disable CSRF for testing or APIs
         .authorizeHttpRequests(auth -> auth
             .requestMatchers("/api/users/register").permitAll() // Allow public access
             .anyRequest().permitAll() // Or adjust based on your needs
         )
         .httpBasic(Customizer.withDefaults()); // Optional: enable basic auth

     return http.build();
 }
}
