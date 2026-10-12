package com.stotram.security;

import com.stotram.model.AdminUser;
import com.stotram.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the admin account on startup from {@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD}.
 *
 * <p>Why not a Flyway migration: the repo is public, and a migration would publish the
 * account — a bcrypt hash of a short password is crackable offline. The environment keeps
 * both out of git while still landing a normal row in {@code admin_user}.
 *
 * <p>Idempotent: an existing row is left alone unless the password changed, in which case
 * the hash is replaced — so rotating the password is "change the env var, restart".
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final int RECOMMENDED_PASSWORD_LENGTH = 12;

    private final AdminUserRepository admins;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public AdminBootstrap(AdminUserRepository admins,
                          PasswordEncoder encoder,
                          @Value("${app.admin.email:}") String email,
                          @Value("${app.admin.password:}") String password) {
        this.admins = admins;
        this.encoder = encoder;
        this.email = AdminUserDetailsService.normalize(email);
        this.password = password == null ? "" : password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isEmpty() || password.isEmpty()) {
            log.warn("ADMIN_EMAIL / ADMIN_PASSWORD not set: no admin account, every /admin endpoint will answer 401");
            return;
        }
        if (!email.contains("@")) {
            throw new IllegalStateException("ADMIN_EMAIL must be an email address, got: " + email);
        }
        if (password.length() < RECOMMENDED_PASSWORD_LENGTH) {
            log.warn("ADMIN_PASSWORD is shorter than {} characters: fine for local dev, change it before deploying",
                    RECOMMENDED_PASSWORD_LENGTH);
        }

        AdminUser admin = admins.findByEmailIgnoreCase(email).orElse(null);
        if (admin == null) {
            admin = new AdminUser();
            admin.setEmail(email);
            admin.setPasswordHash(encoder.encode(password));
            admins.save(admin);
            log.info("Created admin account {}", email);
        } else if (!encoder.matches(password, admin.getPasswordHash())) {
            admin.setPasswordHash(encoder.encode(password));
            log.info("Updated password for admin account {}", email);
        } else {
            log.info("Admin account {} ready", email);
        }
    }
}
