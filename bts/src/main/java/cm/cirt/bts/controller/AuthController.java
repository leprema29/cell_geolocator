package cm.cirt.bts.controller;

import cm.cirt.bts.model.AuthResponse;
import cm.cirt.bts.model.LoginRequest;
import cm.cirt.bts.model.RefreshRequest;
import cm.cirt.bts.model.SignupRequest;
import cm.cirt.bts.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Signup, login and token refresh")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a new user")
    @PostMapping("/signup")
    public ResponseEntity<Map<String, String>> signup(@Valid @RequestBody SignupRequest req) {
        authService.signup(req.getUsername(), req.getEmail(), req.getPassword());
        return ResponseEntity.ok(Map.of("message", "User registered successfully"));
    }

    @Operation(summary = "Authenticate user and issue tokens")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req.getEmail(), req.getPassword()));
    }

    @Operation(summary = "Refresh an access token")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ResponseEntity.ok(authService.refresh(req.getRefreshToken()));
    }
}
