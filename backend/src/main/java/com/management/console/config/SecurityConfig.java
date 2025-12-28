package com.management.console.config;

import com.management.console.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)) // For H2 console
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                
                // Dashboard - all authenticated users
                .requestMatchers(HttpMethod.GET, "/api/dashboard/**").authenticated()
                
                // Services - read for all, write for operators+
                .requestMatchers(HttpMethod.GET, "/api/services/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/services").hasAnyRole("OPERATOR", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/services/**").hasAnyRole("OPERATOR", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/services/**").hasRole("ADMIN")
                
                // Lifecycle actions - operators and admins only
                .requestMatchers("/api/lifecycle/**").hasAnyRole("OPERATOR", "ADMIN")
                
                // Incidents - read for all, actions for operators+
                .requestMatchers(HttpMethod.GET, "/api/incidents/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/incidents/**").hasAnyRole("OPERATOR", "ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/incidents/**").hasAnyRole("OPERATOR", "ADMIN")
                
                // Audit logs - read for all
                .requestMatchers(HttpMethod.GET, "/api/audit/**").authenticated()
                
                // Logs - read for all, delete for admins only
                .requestMatchers(HttpMethod.GET, "/api/logs/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/logs/**").hasRole("ADMIN")
                
                // Diagnostics - read for all, write operations for operators+
                .requestMatchers(HttpMethod.GET, "/api/diagnostics/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/diagnostics/**").hasAnyRole("OPERATOR", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/diagnostics/**").hasRole("ADMIN")
                
                // Wallboard - read for all
                .requestMatchers(HttpMethod.GET, "/api/wallboard/**").authenticated()
                
                // Topology - read for all, write for operators+
                .requestMatchers(HttpMethod.GET, "/api/topology/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/topology/**").hasAnyRole("OPERATOR", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/topology/**").hasAnyRole("OPERATOR", "ADMIN")
                
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow all localhost ports for development
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

