package com.domus.api.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Mesmo padrão de resolução de IP do RateLimitFilter — reusado aqui pra registrar aceite de termos. */
@Component
public class ClienteIpResolver {

    private final boolean trustForwardedFor;

    public ClienteIpResolver(@Value("${app.ratelimit.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    public String resolver(HttpServletRequest request) {
        if (trustForwardedFor) {
            String cf = request.getHeader("CF-Connecting-IP");
            if (cf != null && !cf.isBlank()) {
                return cf.trim();
            }
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] partes = forwarded.split(",");
                return partes[partes.length - 1].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
