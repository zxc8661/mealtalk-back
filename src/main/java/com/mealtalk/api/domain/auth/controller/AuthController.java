package com.mealtalk.api.domain.auth.controller;

import com.mealtalk.api.domain.auth.dto.AuthTokenResponse;
import com.mealtalk.api.domain.auth.dto.CurrentUserResponse;
import com.mealtalk.api.domain.auth.dto.GoogleLoginRequest;
import com.mealtalk.api.domain.auth.dto.ProfileUpdateRequest;
import com.mealtalk.api.domain.auth.security.AuthenticatedUser;
import com.mealtalk.api.domain.auth.service.AuthService;
import com.mealtalk.api.domain.auth.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService authService;
    private final ProfileService profileService;

    @PostMapping("/auth/google")
    public AuthTokenResponse loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.loginWithGoogle(request.idToken());
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.currentUser(user.userId());
    }

    @PutMapping("/me/profile")
    public CurrentUserResponse updateProfile(
        @AuthenticationPrincipal AuthenticatedUser user,
        @Valid @RequestBody ProfileUpdateRequest request
    ) {
        return profileService.updateProfile(user.userId(), request);
    }
}
