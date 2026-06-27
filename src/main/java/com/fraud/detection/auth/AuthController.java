package com.fraud.detection.auth;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fraud.detection.auth.dto.LoginRequest;
import com.fraud.detection.auth.dto.LoginResponse;
import com.fraud.detection.auth.dto.RegisterRequest;
import com.fraud.detection.auth.dto.RegisterResponse;
import com.fraud.detection.entity.User;
import com.fraud.detection.entity.UserProfile;
import com.fraud.detection.entity.enums.UserRole;
import com.fraud.detection.repository.UserProfileRepository;
import com.fraud.detection.repository.UserRepository;
import com.fraud.detection.security.JwtService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository; // NEW
    private final PasswordEncoder passwordEncoder; // NEW

    public AuthController(AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        Optional<User> userOpt = userRepository.findByEmail(auth.getName());
        User user = userOpt.get();
        String token = jwtService.generateToken(user);
        return new LoginResponse(token);
    }

    @PostMapping("/register")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        if (request.phone() != null && userRepository.existsByPhone(request.phone())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone number already registered");
        }

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);
        user = userRepository.save(user);

        // Every user gets an (empty) profile so the scoring layer always has one.
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        userProfileRepository.save(profile);

        // No token here — the user logs in separately. Cleaner, more demoable flow.
        return new RegisterResponse(
                user.getId(),
                user.getEmail(),
                "Account created successfully. Please log in.");
    }
}