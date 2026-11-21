package com.justfast.identity.security;

import com.justfast.identity.domain.User;
import com.justfast.identity.infrastructure.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return users.findByEmail(User.normalizeEmail(email))
                .map(AppUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
