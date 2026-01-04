package com.management.console.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        // Skip if no Authorization header or doesn't start with Bearer
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        
        try {
            username = jwtService.extractUsername(jwt);

            if (username != null) {
                // Only attempt JWT authentication if no authentication is already set
                // or if the existing authentication is from a service (ROLE_SERVICE)
                var existingAuth = SecurityContextHolder.getContext().getAuthentication();
                boolean shouldAuthenticate = existingAuth == null || 
                    existingAuth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_SERVICE"));
                
                if (shouldAuthenticate) {
                    UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                    
                    if (jwtService.isTokenValid(jwt, userDetails)) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        log.debug("JWT authentication successful for user: {}", username);
                    } else {
                        log.warn("JWT token validation failed for user: {}", username);
                        // Only clear context if there was no previous authentication
                        if (existingAuth == null) {
                            SecurityContextHolder.clearContext();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("JWT token validation failed: {}", e.getMessage());
            // Only clear context if there was no previous authentication
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                SecurityContextHolder.clearContext();
            }
        }
        
        filterChain.doFilter(request, response);
    }
}

