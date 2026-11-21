package com.justfast.identity.security;

import com.justfast.identity.domain.Role;
import com.justfast.identity.domain.User;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated user stored in the (JDBC-backed) HTTP session. Must stay serializable and small.
 */
public final class AppUserPrincipal implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String email;
    private final String fullName;
    private final Role role;
    private final transient String passwordHash;

    private AppUserPrincipal(Long id, String email, String fullName, Role role, String passwordHash) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.passwordHash = passwordHash;
    }

    public static AppUserPrincipal from(User user) {
        return new AppUserPrincipal(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getPasswordHash());
    }

    public Long id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String fullName() {
        return fullName;
    }

    public Role role() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
