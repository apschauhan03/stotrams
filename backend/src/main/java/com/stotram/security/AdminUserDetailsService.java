package com.stotram.security;

import com.stotram.repository.AdminUserRepository;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Looks up the admin by email for Spring Security's password check.
 *
 * <p>Only the account named by {@code app.admin.email} can log in — even if older rows are
 * left in {@code admin_user} (say, after the configured email changes), they are ignored.
 */
@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository admins;
    private final String configuredEmail;

    public AdminUserDetailsService(AdminUserRepository admins,
                                   @Value("${app.admin.email:}") String configuredEmail) {
        this.admins = admins;
        this.configuredEmail = normalize(configuredEmail);
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        String wanted = normalize(email);
        if (configuredEmail.isEmpty() || !configuredEmail.equals(wanted)) {
            throw new UsernameNotFoundException("Not the configured admin");
        }
        return admins.findByEmailIgnoreCase(wanted)
                .map(admin -> User.withUsername(admin.getEmail())
                        .password(admin.getPasswordHash())
                        .roles("ADMIN")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("No admin row"));
    }

    static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
