package com.genzenglish.partner.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.genzenglish.partner.auth.CurrentUser;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final AccountService accounts;

    public MeController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return accounts.me(CurrentUser.id(jwt));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal Jwt jwt) {
        accounts.deleteAccount(CurrentUser.id(jwt));
    }
}
