package cm.cirt.bts.controller;

import cm.cirt.bts.model.CreateUserRequest;
import cm.cirt.bts.model.ResetPasswordRequest;
import cm.cirt.bts.model.UpdateUserRequest;
import cm.cirt.bts.model.UserResponse;
import cm.cirt.bts.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin - Users", description = "User management (admin only)")
public class AdminUserController {

    private final UserManagementService userManagementService;

    public AdminUserController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @Operation(summary = "List all users")
    @GetMapping
    public ResponseEntity<List<UserResponse>> list() {
        return ResponseEntity.ok(userManagementService.list());
    }

    @Operation(summary = "Get a user by id")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(userManagementService.get(id));
    }

    @Operation(summary = "Create a new user (with role)")
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest req) {
        return ResponseEntity.ok(userManagementService.create(req));
    }

    @Operation(summary = "Update a user (email, role, enabled)")
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody UpdateUserRequest req,
                                               Authentication auth) {
        return ResponseEntity.ok(userManagementService.update(id, req, auth.getName()));
    }

    @Operation(summary = "Delete a user")
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id, Authentication auth) {
        userManagementService.delete(id, auth.getName());
        return ResponseEntity.ok(Map.of("message", "User deleted"));
    }

    @Operation(summary = "Reset a user's password")
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable Long id,
                                                             @Valid @RequestBody ResetPasswordRequest req) {
        userManagementService.resetPassword(id, req.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset"));
    }
}
