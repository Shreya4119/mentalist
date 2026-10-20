package com.hrragassistant.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.hrragassistant.backend.hosting.AuthThrottles;
import com.hrragassistant.backend.hosting.ClientAddress;
import com.hrragassistant.backend.hosting.HostingSettings;
import com.hrragassistant.backend.hosting.SignupRules;

@RestController
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final HostingSettings hosting;
    private final AuthThrottles throttles;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                          HostingSettings hosting, AuthThrottles throttles) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.hosting = hosting;
        this.throttles = throttles;
    }

    @PostMapping("/api/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String address = ClientAddress.of(http, hosting.trustForwardedFor());
        throttles.checkLogin(address);
        // Usernames are case-insensitive for accounts created through sign-up.
        User user = userRepository.findByUsername(request.username())
                .or(() -> userRepository.findByUsername(SignupRules.normalize(request.username())))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElse(null);
        if (user == null) {
            throttles.loginFailed(address);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        throttles.loginSucceeded(address);
        return new LoginResponse(jwtService.issueToken(user.getUsername(), user.getRole()), user.getRole(), user.getUsername());
    }

    /** Creates an account (when sign-up is enabled) and signs the person in straight away. */
    @PostMapping("/api/auth/signup")
    public ResponseEntity<Object> signup(@RequestBody SignupRequest request, HttpServletRequest http) {
        if (!hosting.signupEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sign-up is not enabled on this server");
        }
        if (!request.acceptedTerms()) {
            return error(HttpStatus.BAD_REQUEST, "Please confirm that you've read how Mentalist works and what it isn't.");
        }
        String problem = SignupRules.problem(request.username(), request.password());
        if (problem != null) {
            return error(HttpStatus.BAD_REQUEST, problem);
        }
        String username = SignupRules.normalize(request.username());
        if (userRepository.existsByUsername(username)) {
            return error(HttpStatus.CONFLICT, "That username is taken. Please choose another.");
        }
        if (hosting.maxUsers() > 0 && userRepository.count() >= hosting.maxUsers()) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "Mentalist isn't taking new accounts right now. Please try again later.");
        }
        throttles.acquireSignup(ClientAddress.of(http, hosting.trustForwardedFor()));
        User user = userRepository.save(new User(username, passwordEncoder.encode(request.password()), Role.USER));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new LoginResponse(jwtService.issueToken(user.getUsername(), user.getRole()), user.getRole(), user.getUsername()));
    }

    /** What the sign-in page needs to know before anyone is signed in. */
    @GetMapping("/api/auth/config")
    public PublicConfig config() {
        return new PublicConfig(hosting.publicMode(), hosting.signupEnabled(), SignupRules.MIN_PASSWORD);
    }

    private static ResponseEntity<Object> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(java.util.Map.of("message", message));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record SignupRequest(String username, String password, boolean acceptedTerms) {
    }

    public record LoginResponse(String token, Role role, String username) {
    }

    public record PublicConfig(boolean publicMode, boolean signupEnabled, int minPasswordLength) {
    }
}
