package com.fraud.detection.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fraud.detection.security.CustomUserDetailService;
import com.fraud.detection.security.JwtAuthEntryPoint;
import com.fraud.detection.security.JwtAuthenticationFilter;
import com.fraud.detection.security.JwtService;

@Configuration
public class SecurityConfig {

    private final JwtService jwtService;
    private final CustomUserDetailService userDetailsService;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;

    public SecurityConfig(JwtService jwtService,
            CustomUserDetailService userDetailsService,
            JwtAuthEntryPoint jwtAuthEntryPoint) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.jwtAuthEntryPoint = jwtAuthEntryPoint;
    }

    // the moment this bean exists, Spring stops using its default chain and uses
    // yours instead
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Build the filter by hand (see the note in the filter class).
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService, userDetailsService);

        http
                .csrf(csrf -> csrf.disable())
                // No server-side session: every request re-proves identity via the token.
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated())
                // Unauthenticated -> clean JSON 401 instead of a 403 or login page.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthEntryPoint))
                // Run our JWT filter before Spring's username/password filter.
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        // NOTE: .httpBasic(...) is GONE. We authenticate by token now, not Basic.

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {

        // Hands you the AuthenticationManager Spring already built from your
        // UserDetailsService + PasswordEncoder, so you can invoke it directly.
        return config.getAuthenticationManager();

    }
}
