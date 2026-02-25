package com.examplatform.auth.controller;

import com.examplatform.auth.dto.*;
import com.examplatform.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController — REST endpoints for OAuth2.0 grant types.
 *
 * This class is purely the HTTP layer.
 * All logic lives in AuthService — same separation as Gateway's filter → JwtUtil.
 *
 * POST /api/auth/login     — ROPC grant
 * POST /api/auth/refresh   — Refresh token grant
 * POST /api/auth/logout    — Token revocation (RFC 7009)
 * POST /api/auth/register  — Registration + auto-login
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Login — Resource Owner Password Credentials grant.
     *
     * Request:  { "email": "student@exam.com", "password": "Secret123!" }
     * Response: { "accessToken": "eyJ...", "refreshToken": "uuid", "tokenType": "Bearer", "expiresIn": 900 }
     *
     * Errors:
     *   401 — invalid credentials
     *   403 — account locked/inactive
     *   429 — rate limit exceeded
     *   503 — User Service unavailable
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        TokenResponse response = authService.login(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Refresh — exchange refresh token for a new token pair.
     *
     * Request:  { "refreshToken": "uuid" }
     * Response: new access token + new refresh token (old one is now invalid — rotated)
     *
     * Errors:
     *   401 — refresh token invalid, expired, or already used
     *   403 — account locked/inactive since token was issued
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest) {

        TokenResponse response = authService.refresh(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Logout — revoke tokens.
     *
     * Request:  { "refreshToken": "uuid", "allDevices": false }
     * Response: 204 No Content
     *
     * Authorization header (Bearer access token) is optional but recommended.
     * When provided, access token is immediately blacklisted in Redis so
     * Gateway rejects it on the next request — no waiting for expiry.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @Valid @RequestBody LogoutRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpRequest) {

        authService.logout(request, authHeader, httpRequest);
    }

    /**
     * Register — create user and issue initial token pair.
     *
     * Request:  { "email": "...", "password": "...", "firstName": "...", "lastName": "...", "role": "STUDENT" }
     * Response: token pair (user is auto-logged in after registration)
     */
    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {

        TokenResponse response = authService.register(request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
