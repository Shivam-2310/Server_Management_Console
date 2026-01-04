package com.management.console.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration for Spring MVC.
 * Note: ObjectMapper is configured in JacksonConfig to avoid bean conflicts.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    // ObjectMapper is already defined in JacksonConfig with @Primary annotation
    // No need to duplicate it here to avoid bean definition conflicts
}

