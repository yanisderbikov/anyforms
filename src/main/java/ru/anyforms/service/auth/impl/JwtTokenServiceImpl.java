package ru.anyforms.service.auth.impl;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.anyforms.model.Role;
import ru.anyforms.service.auth.JwtTokenService;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Service
class JwtTokenServiceImpl implements JwtTokenService {

    private static final String SCOPE_CLAIM = "scope";
    private static final String MCP_READ_SCOPE = "mcp:read";

    private final SecretKey key;
    private final long expirationMs;
    private final long mcpExpirationMs;
    private final String serviceToken;

    JwtTokenServiceImpl(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration.seconds}") long expirationSeconds,
            @Value("${auth.mcp-token.ttl-days}") long mcpTtlDays,
            @Value("${service.jwt.token}") String serviceToken) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationSeconds * 1000L;
        this.mcpExpirationMs = Duration.ofDays(mcpTtlDays).toMillis();
        this.serviceToken = serviceToken;
    }

    @Override
    public boolean isServiceToken(String token) {
        // Секрет не настроен — значит, межсервисного доступа нет ни у кого
        return serviceToken != null && !serviceToken.isBlank() && serviceToken.equals(token);
    }

    @Override
    public String createToken(String email, Role role, String name, boolean superAdmin) {
        var now = new Date();
        var expiry = new Date(now.getTime() + expirationMs);
        var builder = Jwts.builder()
                .subject(email)
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(expiry);
        if (name != null && !name.isBlank()) {
            builder.claim("name", name.trim());
        }
        if (superAdmin) {
            builder.claim("super", true);
        }
        return builder.signWith(key).compact();
    }

    @Override
    public String createMcpReadToken(String email) {
        var now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim(SCOPE_CLAIM, MCP_READ_SCOPE)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + mcpExpirationMs))
                .signWith(key)
                .compact();
    }

    @Override
    public boolean isMcpReadToken(String token) {
        return MCP_READ_SCOPE.equals(parseClaims(token).get(SCOPE_CLAIM, String.class));
    }

    @Override
    public boolean isValid(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parse(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    @Override
    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    @Override
    public Role getRole(String token) {
        String roleStr = parseClaims(token).get("role", String.class);
        return roleStr != null ? Role.valueOf(roleStr) : null;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
