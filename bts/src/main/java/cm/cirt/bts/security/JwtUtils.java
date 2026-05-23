package cm.cirt.bts.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access.token.expiry-ms}")
    private long accessExpiryMs;

    @Value("${jwt.refresh.token.expiry-ms}")
    private long refreshExpiryMs;

    private SecretKey key;

    @PostConstruct
    void init() {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            decoded = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.key = Keys.hmacShaKeyFor(decoded);
    }

    public String generateAccessToken(String subject) {
        return generate(subject, accessExpiryMs, "access");
    }

    public String generateRefreshToken(String subject) {
        return generate(subject, refreshExpiryMs, "refresh");
    }

    private String generate(String subject, long ttlMs, String type) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getSubject(String token) {
        return parse(token).getSubject();
    }

    public String getType(String token) {
        Object t = parse(token).get("type");
        return t == null ? null : t.toString();
    }
}
