package com.fraud.detection.profile;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fraud.detection.entity.User;
import com.fraud.detection.entity.UserProfile;
import com.fraud.detection.profile.dto.ProfileResponse;
import com.fraud.detection.profile.dto.UpdateProfileRequest;
import com.fraud.detection.repository.UserProfileRepository;
import com.fraud.detection.repository.UserRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    public ProfileController(UserRepository userRepository,
            UserProfileRepository userProfileRepository) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
    }

    // GET /api/profile/me -> the logged-in user's profile + completeness flag.
    @GetMapping("/me")
    public ProfileResponse getMyProfile(Authentication authentication) {
        UserProfile profile = loadProfile(authentication.getName());
        return toResponse(profile);
    }

    // PUT /api/profile/me -> the logged-in user updates their own profile.
    @PutMapping("/me")
    public ProfileResponse updateMyProfile(@Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        UserProfile profile = loadProfile(authentication.getName());

        profile.setHomeLat(request.homeLat());
        profile.setHomeLon(request.homeLon());
        profile.setDob(request.dob());
        profile.setGender(request.gender());

        // city_pop is a low-importance model feature inherited from the training
        // data; we use a representative default rather than ask the user for it.
        if (profile.getCityPop() == null) {
            profile.setCityPop(50000); // median-ish city population
        }

        if (request.deviceId() != null) {
            profile.setLastKnownDevice(request.deviceId());
        }

        UserProfile saved = userProfileRepository.save(profile);
        return toResponse(saved);
    }

    // --- helpers ---

    private UserProfile loadProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        // Create an empty profile on first access if one doesn't exist yet.
        return userProfileRepository.findByUser_Id(user.getId())
                .orElseGet(() -> {
                    UserProfile p = new UserProfile();
                    p.setUser(user);
                    return userProfileRepository.save(p);
                });
    }

    private ProfileResponse toResponse(UserProfile p) {
        // "Complete" = all the fields the scoring pipeline needs are present.
        boolean complete = p.getHomeLat() != null
                && p.getHomeLon() != null
                && p.getDob() != null
                && p.getGender() != null
                && p.getCityPop() != null;

        return new ProfileResponse(
                p.getHomeLat(), p.getHomeLon(), p.getDob(),
                p.getGender(), p.getCityPop(), complete);
    }
}