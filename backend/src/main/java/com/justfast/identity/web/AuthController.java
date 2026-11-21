package com.justfast.identity.web;

import com.justfast.identity.application.RegistrationService;
import com.justfast.identity.security.AppUserPrincipal;
import com.justfast.identity.web.AuthDtos.CurrentUserResponse;
import com.justfast.identity.web.AuthDtos.LoginRequest;
import com.justfast.identity.web.AuthDtos.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(RegistrationService registrationService, AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository) {
        this.registrationService = registrationService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    /** Touching this endpoint makes sure the browser holds an XSRF-TOKEN cookie before its first write. */
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf() {
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public CurrentUserResponse register(@Valid @RequestBody RegisterRequest request,
                                        HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        registrationService.registerCustomer(request.email(), request.password(), request.fullName());
        return signIn(request.email(), request.password(), httpRequest, httpResponse);
    }

    @PostMapping("/login")
    public CurrentUserResponse login(@Valid @RequestBody LoginRequest request,
                                     HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return signIn(request.email(), request.password(), httpRequest, httpResponse);
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(CurrentUserResponse.from(principal));
    }

    private CurrentUserResponse signIn(String email, String password,
                                       HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, password));

        // Prevent session fixation: rotate the session id on privilege change.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return CurrentUserResponse.from((AppUserPrincipal) authentication.getPrincipal());
    }
}
