package com.example.aistore.controller;

import com.example.aistore.dto.AddressDto;
import com.example.aistore.dto.AddressRequest;
import com.example.aistore.dto.ApiResponse;
import com.example.aistore.dto.ProfileResponse;
import com.example.aistore.dto.UpdatePreferencesRequest;
import com.example.aistore.dto.UserDto;
import com.example.aistore.service.UserPreferenceService;
import com.example.aistore.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/profile")
public class ProfileController {

    private final UserService userService;
    private final UserPreferenceService preferenceService;

    public ProfileController(UserService userService, UserPreferenceService preferenceService) {
        this.userService = userService;
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ApiResponse<ProfileResponse> profile(@AuthenticationPrincipal(expression = "username") String email) {
        return ApiResponse.ok(userService.getProfile(requireUserId(email)));
    }

    @PutMapping
    public ApiResponse<UserDto> update(@AuthenticationPrincipal(expression = "username") String email,
                                       @RequestParam(required = false) String fullName,
                                       @RequestParam(required = false) String phone) {
        return ApiResponse.ok(userService.updateProfile(requireUserId(email), fullName, phone));
    }

    @PostMapping("/addresses")
    public ApiResponse<AddressDto> addAddress(@AuthenticationPrincipal(expression = "username") String email,
                                              @Valid @RequestBody AddressRequest request) {
        return ApiResponse.ok(userService.addAddress(requireUserId(email), request));
    }

    @PutMapping("/preferences")
    public ApiResponse<ProfileResponse> updatePreferences(@AuthenticationPrincipal(expression = "username") String email,
                                                          @RequestBody UpdatePreferencesRequest request) {
        Long userId = requireUserId(email);
        preferenceService.update(userId, request);
        return ApiResponse.ok(userService.getProfile(userId));
    }

    private Long requireUserId(String email) {
        if (email == null) {
            throw new com.example.aistore.exception.BadRequestException("Please log in to continue");
        }
        return userService.findUserIdByEmail(email);
    }
}