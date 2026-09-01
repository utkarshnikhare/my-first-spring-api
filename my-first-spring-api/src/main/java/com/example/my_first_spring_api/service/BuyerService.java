package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.BuyerProfileDto;
import com.example.my_first_spring_api.exception.BuyerNotAuthenticatedException;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class BuyerService {

    public static final String BUYER_SESSION_KEY = "BUYER_USER";

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final AnalyticsService analyticsService;

    @Autowired
    public BuyerService(UserRepository userRepository, OtpService otpService,
                        AnalyticsService analyticsService) {
        this.userRepository = userRepository;
        this.otpService = otpService;
        this.analyticsService = analyticsService;
    }

    @Transactional
    public String requestOtp(String mobileNumber) {
        return otpService.generateOtp(mobileNumber);
    }

    @Transactional
    public User verifyOtpAndAuthenticate(String mobileNumber, String otpCode,
                                        String name, String flatHouseNumber,
                                        HttpSession session) {
        otpService.verifyOtp(mobileNumber, otpCode);

        Optional<User> existing = userRepository.findByMobileNumber(mobileNumber);
        User buyer;
        if (existing.isPresent()) {
            buyer = existing.get();
            if (name != null && !name.isBlank()) {
                buyer.setName(name);
            }
            if (flatHouseNumber != null && !flatHouseNumber.isBlank()) {
                buyer.setFlatHouseNumber(flatHouseNumber);
            }
        } else {
            buyer = new User(
                    (name == null || name.isBlank()) ? "Buyer" : name,
                    mobileNumber,
                    flatHouseNumber,
                    UserRole.BUYER
            );
        }
        buyer = userRepository.save(buyer);
        session.setAttribute(BUYER_SESSION_KEY, buyer.getId());
        boolean isNew = existing.isEmpty();
        analyticsService.record(
                isNew ? AnalyticsService.EV_USER_REGISTERED : AnalyticsService.EV_USER_LOGIN,
                buyer.getId(), buyer.getMobileNumber(), null, buyer.getName());
        return buyer;
    }

        @Transactional(readOnly = true)
    public User getCurrentBuyer(HttpSession session) {
        // Check session attribute first (reliable for REST), then SecurityContext
        if (session != null) {
            Object attr = session.getAttribute(BUYER_SESSION_KEY);
            if (attr instanceof Long userId) {
                return userRepository.findById(userId).orElse(null);
            }
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Long userId) {
            return userRepository.findById(userId).orElse(null);
        }
        return null;
    }

    @Transactional(readOnly = true)
    public User requireCurrentBuyer(HttpSession session) {
        User buyer = getCurrentBuyer(session);
        if (buyer == null) {
            throw new BuyerNotAuthenticatedException(
                    "Authentication required. Please verify your mobile number via OTP.");
        }
        return buyer;
    }

    @Transactional(readOnly = true)
    public BuyerProfileDto getProfile(HttpSession session) {
        User buyer = requireCurrentBuyer(session);
        BuyerProfileDto dto = new BuyerProfileDto(
                buyer.getId(),
                buyer.getName(),
                buyer.getMobileNumber(),
                buyer.getFlatHouseNumber()
        );
        dto.setSociety(buyer.getSociety());
        dto.setBuilding(buyer.getBuilding());
        return dto;
    }

    @Transactional
    public BuyerProfileDto updateProfile(BuyerProfileDto profileDto, HttpSession session) {
        User buyer = requireCurrentBuyer(session);
        if (profileDto.getName() != null && !profileDto.getName().isBlank()) {
            buyer.setName(profileDto.getName());
        }
        if (profileDto.getFlatHouseNumber() != null) {
            buyer.setFlatHouseNumber(profileDto.getFlatHouseNumber());
        }
        if (profileDto.getSociety() != null) {
            buyer.setSociety(profileDto.getSociety());
        }
        if (profileDto.getBuilding() != null) {
            buyer.setBuilding(profileDto.getBuilding());
        }
        buyer = userRepository.save(buyer);
        BuyerProfileDto dto = new BuyerProfileDto(
                buyer.getId(),
                buyer.getName(),
                buyer.getMobileNumber(),
                buyer.getFlatHouseNumber()
        );
        dto.setSociety(buyer.getSociety());
        dto.setBuilding(buyer.getBuilding());
        return dto;
    }

    @Transactional
    public User becomeSeller(HttpSession session) {
        User user = requireCurrentBuyer(session);
        if (user.getRole() != UserRole.SELLER) {
            user.setRole(UserRole.SELLER);
            // New sellers always start in the moderation queue: an Admin must
            // approve them before their kitchen becomes publicly active.
            if (user.getSellerApprovalStatus() == null) {
                user.setSellerApprovalStatus(SellerApprovalStatus.PENDING);
            }
            user = userRepository.save(user);
            analyticsService.record(AnalyticsService.EV_SELLER_REGISTERED, user.getId(),
                    user.getMobileNumber(), null, user.getName());
        }
        return user;
    }

    public void logout(HttpSession session) {
        session.removeAttribute(BUYER_SESSION_KEY);
    }
}
