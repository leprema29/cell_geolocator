package cm.cirt.bts.config;

import cm.cirt.bts.entity.User;
import cm.cirt.bts.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DefaultAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdminInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.email:admin@bts.local}")
    private String adminEmail;

    @Value("${app.admin.password:Admin@123}")
    private String adminPassword;

    public DefaultAdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean hasAdmin = userRepository.findAll().stream()
                .anyMatch(u -> "ROLE_ADMIN".equals(u.getRole()));

        if (hasAdmin) {
            log.info("Admin user already present — skipping default admin creation");
            return;
        }

        if (userRepository.existsByUsername(adminUsername) || userRepository.existsByEmail(adminEmail)) {
            log.warn("Cannot create default admin: username '{}' or email '{}' already used by a non-admin user",
                    adminUsername, adminEmail);
            return;
        }

        User admin = User.builder()
                .username(adminUsername)
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .role("ROLE_ADMIN")
                .enabled(true)
                .build();
        userRepository.save(admin);

        log.warn("================================================================");
        log.warn(" Default ADMIN user created:");
        log.warn("   username : {}", adminUsername);
        log.warn("   email    : {}", adminEmail);
        log.warn("   password : {}", adminPassword);
        log.warn(" CHANGE THE PASSWORD IMMEDIATELY via the admin console.");
        log.warn(" Override defaults with app.admin.username / .email / .password");
        log.warn("================================================================");
    }
}
