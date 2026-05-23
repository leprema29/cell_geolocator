package cm.cirt.bts.service;

import cm.cirt.bts.entity.User;
import cm.cirt.bts.exception.ApiException;
import cm.cirt.bts.model.AuthResponse;
import cm.cirt.bts.repository.UserRepository;
import cm.cirt.bts.security.JwtUtils;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    @Transactional
    public void signup(String username, String email, String password) {
        if (userRepository.existsByUsername(username) || userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Invalid email or username already exists");
        }
        User u = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role("ROLE_USER")
                .build();
        userRepository.save(u);
    }

    @Transactional
    public AuthResponse login(String emailOrUsername, String rawPassword) {
        User user = userRepository.findByEmail(emailOrUsername)
                .or(() -> userRepository.findByUsername(emailOrUsername))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String access = jwtUtils.generateAccessToken(user.getUsername());
        String refresh = jwtUtils.generateRefreshToken(user.getUsername());
        user.setRefreshTokenHash(sha256(refresh));
        userRepository.save(user);

        return AuthResponse.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .tokenType("Bearer")
                .build();
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank() || !jwtUtils.isValid(refreshToken)
                || !"refresh".equals(jwtUtils.getType(refreshToken))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
        Claims claims = jwtUtils.parse(refreshToken);
        String username = claims.getSubject();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (user.getRefreshTokenHash() == null
                || !user.getRefreshTokenHash().equals(sha256(refreshToken))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        String newAccess = jwtUtils.generateAccessToken(username);
        return AuthResponse.builder()
                .accessToken(newAccess)
                .tokenType("Bearer")
                .build();
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
