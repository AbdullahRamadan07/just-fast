package com.justfast.identity.application;

import com.justfast.common.error.BusinessRuleException;
import com.justfast.identity.domain.Role;
import com.justfast.identity.domain.User;
import com.justfast.identity.infrastructure.UserRepository;
import java.time.Clock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public RegistrationService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** Registers a customer. Admin accounts are only created by {@link AdminBootstrap}. */
    @Transactional
    public User registerCustomer(String email, String rawPassword, String fullName) {
        String normalized = User.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw emailTaken();
        }
        try {
            return users.saveAndFlush(new User(normalized, passwordEncoder.encode(rawPassword), fullName,
                    Role.CUSTOMER, clock.instant()));
        } catch (DataIntegrityViolationException race) {
            throw emailTaken();
        }
    }

    private static BusinessRuleException emailTaken() {
        return new BusinessRuleException("EMAIL_TAKEN", "An account with this email already exists.");
    }
}
