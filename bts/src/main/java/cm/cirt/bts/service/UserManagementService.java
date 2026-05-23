package cm.cirt.bts.service;

import cm.cirt.bts.entity.User;
import cm.cirt.bts.exception.ApiException;
import cm.cirt.bts.model.CreateUserRequest;
import cm.cirt.bts.model.UpdateUserRequest;
import cm.cirt.bts.model.UserResponse;
import cm.cirt.bts.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserManagementService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponse> list() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    public UserResponse get(Long id) {
        return UserResponse.from(findOrThrow(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new ApiException(HttpStatus.CONFLICT, "Username already exists");
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already exists");
        }
        User u = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .role(req.getRole() == null ? "ROLE_USER" : req.getRole())
                .enabled(true)
                .build();
        return UserResponse.from(userRepository.save(u));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest req, String currentUsername) {
        User u = findOrThrow(id);

        if (req.getEmail() != null && !req.getEmail().equals(u.getEmail())) {
            if (userRepository.existsByEmail(req.getEmail())) {
                throw new ApiException(HttpStatus.CONFLICT, "Email already exists");
            }
            u.setEmail(req.getEmail());
        }
        if (req.getRole() != null) {
            if (u.getUsername().equals(currentUsername) && !"ROLE_ADMIN".equals(req.getRole())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot demote yourself");
            }
            u.setRole(req.getRole());
        }
        if (req.getEnabled() != null) {
            if (u.getUsername().equals(currentUsername) && !req.getEnabled()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot disable yourself");
            }
            u.setEnabled(req.getEnabled());
        }
        return UserResponse.from(userRepository.save(u));
    }

    @Transactional
    public void delete(Long id, String currentUsername) {
        User u = findOrThrow(id);
        if (u.getUsername().equals(currentUsername)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot delete yourself");
        }
        if ("ROLE_ADMIN".equals(u.getRole())) {
            long admins = userRepository.findAll().stream()
                    .filter(x -> "ROLE_ADMIN".equals(x.getRole())).count();
            if (admins <= 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot delete the last admin");
            }
        }
        userRepository.delete(u);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        User u = findOrThrow(id);
        u.setPassword(passwordEncoder.encode(newPassword));
        u.setRefreshTokenHash(null);
        userRepository.save(u);
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
