package com.example.aistore.controller.api;

import com.example.aistore.dto.ApiResponse;
import com.example.aistore.dto.LoginRequest;
import com.example.aistore.dto.RegisterRequest;
import com.example.aistore.dto.UserDto;
import com.example.aistore.entity.User;
import com.example.aistore.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    public AuthApiController(UserService userService, AuthenticationManager authenticationManager) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/register")
    public ApiResponse<UserDto> register(@Valid @RequestBody RegisterRequest request,
                                         HttpServletRequest httpRequest) {
        User user = userService.register(request);
        authenticateAndBindSession(httpRequest, request.email(), request.password());
        return ApiResponse.ok("Registration successful", toUserDto(user));
    }

    @PostMapping("/login")
    public ApiResponse<UserDto> login(@Valid @RequestBody LoginRequest request,
                                      HttpServletRequest httpRequest) {
        authenticateAndBindSession(httpRequest, request.email(), request.password());
        return ApiResponse.ok("Login successful", currentUserDto(request.email()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ApiResponse.ok("Logged out", null);
    }

    @GetMapping("/me")
    public ApiResponse<UserDto> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ApiResponse.ok(null);
        }
        return ApiResponse.ok(currentUserDto(authentication.getName()));
    }

    private void authenticateAndBindSession(HttpServletRequest request, String email, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email.trim().toLowerCase(), password));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    private UserDto currentUserDto(String email) {
        User user = userService.findByEmail(email);
        return new UserDto(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getAvatarUrl(), user.isActive(),
                user.getRoles().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()));
    }

    private UserDto toUserDto(User user) {
        return currentUserDto(user.getEmail());
    }
}