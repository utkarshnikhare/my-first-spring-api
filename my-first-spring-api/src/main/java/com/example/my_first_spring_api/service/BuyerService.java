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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class BuyerService {

    public static final String BUYER_SESSION_KEY = "BUYER_USER";

    private final UserRepository userRepository;
    private final AnalyticsService analyticsService;
    private final SocietyDirectory societyDirectory;

    @Autowired
    public BuyerService(UserRepository userRepository, AnalyticsService analyticsService,
                        SocietyDirectory societyDirectory) {
        this.userRepository = userRepository;
        this.analyticsService = analyticsService;
        this.societyDirectory = societyDirectory;
    }

    /**
     * Demo login: authenticates a buyer/seller by mobile number only (no code step).
     * For client demo so the app opens and operates with a mobile number only.
     */
    @Transactional
    public User demoLoginAndAuthenticate(String mobileNumber, String name, String flatHouseNumber,
                                          HttpSession session) {
        if (mobileNumber == null || !mobileNumber.matches("[0-9]{10}")) {
            throw new IllegalArgumentException("Enter a valid 10-digit mobile number.");
        }
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
        if (!java.util.Objects.equals(session.getAttribute(BUYER_SESSION_KEY), buyer.getId())) {
            session.removeAttribute(OrderService.DRAFT_ORDER_SESSION_KEY);
        }
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
                    "Authentication required. Please log in.");
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

    /**
     * The societies a buyer may choose from on their profile, taken from the
     * authoritative SocietyDirectory (derived from existing users and kitchens).
     * The buyer's CURRENT society is always included even if it is no longer in
     * the directory, so an existing profile is never silently invalidated.
     */
    @Transactional(readOnly = true)
    public List<String> getSelectableSocieties(HttpSession session) {
        User buyer = requireCurrentBuyer(session);
        List<String> societies = new ArrayList<>(societyDirectory.findAllSocieties());
        String current = buyer.getSociety() == null ? "" : buyer.getSociety().trim();
        if (!current.isEmpty() && societies.stream().noneMatch(s -> s.equalsIgnoreCase(current))) {
            societies.add(current);
        }
        societies.sort(String.CASE_INSENSITIVE_ORDER);
        return societies;
    }

    /**
     * Resolves a submitted society to its stored spelling. A value that matches a
     * known society (ignoring case) is canonicalized to that exact spelling. The
     * buyer's own current society is accepted unchanged so an existing profile
     * stays editable; anything else is rejected rather than stored as free text.
     */
    private String canonicalizeBuyerSociety(String submitted, User buyer) {
        String candidate = submitted == null ? "" : submitted.trim();
        if (candidate.isEmpty()) return "";
        for (String known : societyDirectory.findAllSocieties()) {
            if (known.equalsIgnoreCase(candidate)) return known;
        }
        String current = buyer.getSociety() == null ? "" : buyer.getSociety().trim();
        if (!current.isEmpty() && current.equalsIgnoreCase(candidate)) return current;
        throw new IllegalArgumentException("Please choose your community from the list.");
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
            // Validate server-side against the authoritative society records so a
            // buyer cannot store an arbitrary free-text community, which would
            // silently break service-area eligibility at order time. A blank value
            // keeps the existing "no society set" behaviour.
            buyer.setSociety(canonicalizeBuyerSociety(profileDto.getSociety(), buyer));
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
        if (user.getRole() != UserRole.BUYER && user.getRole() != UserRole.SELLER) {
            throw new IllegalArgumentException("Only a buyer account can become a seller.");
        }
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
