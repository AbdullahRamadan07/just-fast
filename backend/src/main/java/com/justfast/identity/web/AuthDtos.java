package com.justfast.identity.web;

import com.justfast.identity.domain.Role;
import com.justfast.identity.security.AppUserPrincipal;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters.") String password,
            @NotBlank @Size(max = 120) String fullName) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record CurrentUserResponse(Long id, String email, String fullName, Role role) {

        public static CurrentUserResponse from(AppUserPrincipal principal) {
            return new CurrentUserResponse(principal.id(), principal.email(), principal.fullName(), principal.role());
        }
    }
}
