package com.example.securemcp;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class PrincipalContextFilter {

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> principalFilter(McpProperties properties) {
        var registration = new FilterRegistrationBean<OncePerRequestFilter>();
        registration.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                try {
                    Principal principal = principalFromTrustedHeaders(request, properties);
                    if (principal != null) {
                        PrincipalContextHolder.set(principal);
                    }
                    filterChain.doFilter(request, response);
                } finally {
                    PrincipalContextHolder.clear();
                }
            }
        });
        registration.addUrlPatterns("/mcp");
        registration.setName("validatedPrincipalContext");
        registration.setOrder(-100);
        return registration;
    }

    private static Principal principalFromTrustedHeaders(HttpServletRequest request, McpProperties properties) {
        String source = request.getHeader("X-Mcp-Principal-Source");
        boolean trustedLambdaHeader = "validated-jwt".equalsIgnoreCase(source);
        boolean localDebugHeader = properties.isAllowDebugPrincipal() && "debug-local".equalsIgnoreCase(source);
        if (!trustedLambdaHeader && !localDebugHeader) {
            return null;
        }
        String id = request.getHeader("X-Mcp-Principal-Id");
        String typeValue = request.getHeader("X-Mcp-Principal-Type");
        if (id == null || id.isBlank() || typeValue == null || typeValue.isBlank()) {
            return null;
        }
        try {
            return new Principal(id, Principal.Type.valueOf(typeValue.toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
