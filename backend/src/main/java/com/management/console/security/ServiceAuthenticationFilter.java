package com.management.console.security;

import com.management.console.repository.ManagedServiceRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Filter for authenticating services using their authentication tokens.
 * Services can authenticate using the X-Service-Token header.
 * This allows services to securely register themselves and send telemetry.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceAuthenticationFilter extends OncePerRequestFilter {

    private final ManagedServiceRepository serviceRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String serviceToken = request.getHeader("X-Service-Token");
        
        if (serviceToken != null && !serviceToken.isEmpty()) {
            try {
                // Find service by token
                serviceRepository.findByAuthenticationToken(serviceToken)
                        .ifPresent(service -> {
                            // Create authentication for service
                            UsernamePasswordAuthenticationToken authToken = 
                                    new UsernamePasswordAuthenticationToken(
                                            service.getName(),
                                            null,
                                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_SERVICE"))
                                    );
                            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                            
                            log.debug("Service authenticated: {} from {}", service.getName(), 
                                    request.getRemoteAddr());
                        });
            } catch (Exception e) {
                log.warn("Service authentication failed: {}", e.getMessage());
            }
        }
        
        filterChain.doFilter(request, response);
    }
}



