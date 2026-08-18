package com.example.aistore.service;

import com.example.aistore.dto.AddressRequest;
import com.example.aistore.dto.AddressDto;
import com.example.aistore.dto.ProfileResponse;
import com.example.aistore.dto.RegisterRequest;
import com.example.aistore.dto.UpdatePreferencesRequest;
import com.example.aistore.dto.UserDto;
import com.example.aistore.entity.Address;
import com.example.aistore.entity.User;
import com.example.aistore.entity.UserPreference;
import com.example.aistore.entity.UserRole;
import com.example.aistore.exception.BadRequestException;
import com.example.aistore.exception.ResourceNotFoundException;
import com.example.aistore.repository.AddressRepository;
import com.example.aistore.repository.UserPreferenceRepository;
import com.example.aistore.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserPreferenceService preferenceService;

    public UserService(UserRepository userRepository, AddressRepository addressRepository,
                       UserPreferenceRepository preferenceRepository, PasswordEncoder passwordEncoder,
                       UserPreferenceService preferenceService) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.preferenceRepository = preferenceRepository;
        this.passwordEncoder = passwordEncoder;
        this.preferenceService = preferenceService;
    }

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email().trim().toLowerCase())) {
            throw new BadRequestException("An account with this email already exists");
        }
        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        user.setActive(true);
        Set<UserRole> roles = new LinkedHashSet<>();
        roles.add(UserRole.ROLE_USER);
        user.setRoles(roles);
        user = userRepository.save(user);

        UserPreference preference = new UserPreference();
        preference.setUser(user);
        preference.setPreferredCategoriesJson("{}");
        preference.setPreferredBrandsJson("{}");
        preference.setRecommendationsEnabled(true);
        preference.setBehaviorTrackingEnabled(true);
        preferenceRepository.save(preference);
        return user;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        UserPreference preference = preferenceRepository.findByUserId(userId).orElse(null);
        List<AddressDto> addresses = addressRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toAddressDto).toList();
        return new ProfileResponse(
                toUserDto(user),
                addresses,
                preferenceService.parseCategories(preference),
                preferenceService.parseBrands(preference),
                preference != null ? preference.getMinBudget() : null,
                preference != null ? preference.getMaxBudget() : null,
                preference == null || preference.isRecommendationsEnabled(),
                preference == null || preference.isBehaviorTrackingEnabled());
    }

    @Transactional
    public AddressDto addAddress(Long userId, AddressRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Address address = new Address();
        address.setUser(user);
        address.setFullName(request.fullName());
        address.setStreetAddress(request.streetAddress());
        address.setApartment(request.apartment());
        address.setCity(request.city());
        address.setState(request.state());
        address.setPostalCode(request.postalCode());
        address.setCountry(request.country());
        address.setPhone(request.phone());
        address.setAddressType(request.addressType() == null ? "HOME" : request.addressType());
        address.setDefault(request.isDefault());
        if (request.isDefault()) {
            clearDefaultFlags(userId);
        }
        return toAddressDto(addressRepository.save(address));
    }

    private void clearDefaultFlags(Long userId) {
        for (Address a : addressRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            if (a.isDefault()) {
                a.setDefault(false);
                addressRepository.save(a);
            }
        }
    }

    @Transactional(readOnly = true)
    public UserDto updateProfile(Long userId, String fullName, String phone) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName);
        }
        if (phone != null) {
            user.setPhone(phone);
        }
        return toUserDto(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    @Transactional(readOnly = true)
    public Long findUserIdByEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    private UserDto toUserDto(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getAvatarUrl(), user.isActive(),
                user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()));
    }

    private AddressDto toAddressDto(Address a) {
        return new AddressDto(a.getId(), a.getFullName(), a.getStreetAddress(), a.getApartment(),
                a.getCity(), a.getState(), a.getPostalCode(), a.getCountry(), a.getPhone(),
                a.getAddressType(), a.isDefault(), a.getCreatedAt());
    }
}