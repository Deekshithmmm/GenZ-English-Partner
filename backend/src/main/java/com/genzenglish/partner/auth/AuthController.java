package com.genzenglish.partner.auth;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.genzenglish.partner.auth.AuthDtos.AuthResponse;
import com.genzenglish.partner.auth.AuthDtos.GoogleSignInRequest;
import com.genzenglish.partner.auth.AuthDtos.LoginRequest;
import com.genzenglish.partner.auth.AuthDtos.ProvidersResponse;
import com.genzenglish.partner.auth.AuthDtos.RefreshRequest;
import com.genzenglish.partner.auth.AuthDtos.RegisterRequest;
import com.genzenglish.partner.config.AppProperties;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final AppProperties props;

    public AuthController(AuthService auth, AppProperties props) {
        this.auth = auth;
        this.props = props;
    }

    @GetMapping("/providers")
    public ProvidersResponse providers() {
        return new ProvidersResponse(true, props.auth().google().provider());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleSignInRequest request) {
        return auth.google(request.idToken());
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return auth.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        auth.logout(request.refreshToken());
    }
}
