package com.management.console.filter;

import com.management.console.service.HttpTraceService;
import com.management.console.service.HttpTraceService.HttpTrace;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.*;

/**
 * Filter to capture HTTP request/response traces for monitoring.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
@Slf4j
public class HttpTraceFilter implements Filter {

    private final HttpTraceService httpTraceService;

    // Paths to exclude from tracing
    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/actuator", "/h2-console", "/api/diagnostics/http",
            "/favicon.ico", "/static", "/assets"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Skip excluded paths
        String path = httpRequest.getRequestURI();
        if (shouldExclude(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Wrap request and response
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(httpResponse);

        long startTime = System.currentTimeMillis();

        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            try {
                recordTrace(wrappedRequest, wrappedResponse, duration);
            } catch (Exception e) {
                log.debug("Failed to record HTTP trace: {}", e.getMessage());
            }

            // Copy body to response
            wrappedResponse.copyBodyToResponse();
        }
    }

    private boolean shouldExclude(String path) {
        if (path == null) return true;
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    private void recordTrace(ContentCachingRequestWrapper request, 
                            ContentCachingResponseWrapper response, 
                            long duration) {
        HttpTrace trace = new HttpTrace();
        trace.setTimestamp(System.currentTimeMillis());
        trace.setMethod(request.getMethod());
        trace.setUri(request.getRequestURI());
        trace.setStatus(response.getStatus());
        trace.setTimeTaken(duration);
        trace.setRemoteAddress(request.getRemoteAddr());

        // Capture request headers (limited)
        Map<String, List<String>> requestHeaders = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (shouldCaptureHeader(name)) {
                requestHeaders.put(name, Collections.list(request.getHeaders(name)));
            }
        }
        trace.setRequestHeaders(requestHeaders);

        // Capture response headers (limited)
        Map<String, List<String>> responseHeaders = new HashMap<>();
        for (String name : response.getHeaderNames()) {
            if (shouldCaptureHeader(name)) {
                responseHeaders.put(name, new ArrayList<>(response.getHeaders(name)));
            }
        }
        trace.setResponseHeaders(responseHeaders);

        // Principal if available
        if (request.getUserPrincipal() != null) {
            trace.setPrincipal(request.getUserPrincipal().getName());
        }

        // Session ID if available
        if (request.getSession(false) != null) {
            trace.setSessionId(request.getSession().getId());
        }

        httpTraceService.recordTrace(trace);
    }

    private boolean shouldCaptureHeader(String headerName) {
        String lower = headerName.toLowerCase();
        // Exclude sensitive headers
        return !lower.contains("authorization") && 
               !lower.contains("cookie") && 
               !lower.contains("token") &&
               !lower.contains("secret");
    }
}

