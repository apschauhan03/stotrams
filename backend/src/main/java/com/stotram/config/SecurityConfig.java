package com.stotram.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Who may call what.
 *
 * <ul>
 *   <li>{@code /api/**} (the public feed) and static images: open to everyone.
 *   <li>{@code POST /admin/login}: open — it's how you get in.
 *   <li>Everything else under {@code /admin/**}: the logged-in admin only. That includes
 *       {@code /admin/generate}, which spends real money.
 * </ul>
 *
 * <p>Login is a JSON endpoint ({@code AuthController}), not Spring's HTML form login: the
 * React admin panel posts credentials and gets a session cookie back. Unauthenticated admin
 * calls get a bare 401 (no redirect to a login page) so the panel can show its own form.
 *
 * <p><b>CSRF:</b> token protection is off on purpose. The session cookie is
 * {@code SameSite=Strict} (application.yml), so a request started from any other site
 * arrives without it and is rejected as unauthenticated; and there is no CORS config, so
 * other origins can't read responses either. Cloudflare Access adds a second gate in
 * production.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService admins, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(admins);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityContextRepository contextRepository) throws Exception {
        var paths = PathPatternRequestMatcher.withDefaults();
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(paths.matcher(HttpMethod.POST, "/admin/login")).permitAll()
                        .requestMatchers(paths.matcher("/admin/**")).authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .logout(logout -> logout
                        .logoutRequestMatcher(paths.matcher(HttpMethod.POST, "/admin/logout"))
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .deleteCookies("STOTRAM_SESSION"));
        return http.build();
    }
}
