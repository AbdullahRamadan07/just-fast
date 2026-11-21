package com.justfast.identity.application;

import com.justfast.common.config.StoreProperties;
import com.justfast.identity.domain.Role;
import com.justfast.identity.domain.User;
import com.justfast.identity.infrastructure.UserRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates (or promotes) the store administrator from ADMIN_EMAIL / ADMIN_PASSWORD on startup,
 * so no credentials live in migrations or source code.
 */
@Component
@Profile("!migrate")
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final StoreProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AdminBootstrap(StoreProperties properties, UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        StoreProperties.Admin admin = properties.admin();
        if (admin == null || isBlank(admin.email()) || isBlank(admin.password())) {
            log.info("ADMIN_EMAIL/ADMIN_PASSWORD not set; skipping admin bootstrap.");
            return;
        }
        String email = User.normalizeEmail(admin.email());
        users.findByEmail(email).ifPresentOrElse(existing -> {
            if (existing.getRole() != Role.ADMIN) {
                existing.promoteTo(Role.ADMIN);
                log.info("Promoted {} to ADMIN.", email);
            }
        }, () -> {
            users.save(new User(email, passwordEncoder.encode(admin.password()), "Store Admin", Role.ADMIN, clock.instant()));
            log.info("Created admin account {}.", email);
        });
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
