package com.example.memo;

import com.azure.spring.cloud.autoconfigure.implementation.aad.security.AadWebApplicationHttpSecurityConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig  {

    @Value("/memo")
    private String[] allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .with(
                        AadWebApplicationHttpSecurityConfigurer.aadWebApplication(),
                        Customizer.withDefaults()
                )
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/public/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    @RequestScope
    public ServletUriComponentsBuilder urlBuilder() {
        return ServletUriComponentsBuilder.fromCurrentRequest();
    }
}
