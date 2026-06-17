package com.stockaggregator.web;

import com.stockaggregator.error.UnauthorizedException;
import com.stockaggregator.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rejects requests without a valid bearer token. Only wired up when
 * {@code app.require-auth=true} (see {@link WebConfig}).
 */
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService auth;

    public AuthInterceptor(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = bearerToken(request);
        if (token == null) {
            throw new UnauthorizedException("Missing bearer token");
        }
        auth.verify(token);   // throws UnauthorizedException if invalid/expired
        return true;
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring("Bearer ".length()).trim();
        }
        return null;
    }
}
