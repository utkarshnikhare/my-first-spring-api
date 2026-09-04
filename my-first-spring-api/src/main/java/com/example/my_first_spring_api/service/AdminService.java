package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Operational platform management used by ADMIN and SUPER_ADMIN accounts:
 * seller approval workflow, seller status management, admin account
 * management (Super Admin only) and the platform analytics summary.
 * Separated from FeatureService so higher-level platform controls stay
 * independent from day-to-day seller operations.
 */
@Service
public class AdminService {

    /** Bootstrap accounts created on first run (login via mobile demo login). */
    public static final String SUPER_ADMIN_MOBILE = "9000000001";
    public static final String ADMIN_MOBILE = "9000000002";

    private final UserRepository userRepository;
    private final AnalyticsService analyticsService;

    @Autowired
    public AdminService(UserRepository userRepository, AnalyticsService analyticsService) {
        this.userRepository = userRepository;
        this.analyticsService = analyticsService;
    }

    // ---------------- Seller approval workflow ----------------

    @Transactional(readOnly = true)
    public List<User> listSellers(SellerApprovalStatus status) {
        if (status != null) {
            return userRepository.findByRoleAndSellerApprovalStatus(UserRole.SELLER, status);
        }
        return userRepository.findByRole(UserRole.SELLER);
    }

    @Transactional(readOnly = true)
    public List<User> pendingSellers() {
        return userRepository.findByRoleAndSellerApprovalStatus(UserRole.SELLER, SellerApprovalStatus.PENDING);
    }

    @Transactional
    public User approveSeller(Long sellerId, User actingAdmin) {
        User seller = requireSeller(sellerId);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller.setSellerStatusReason(null);
        seller.setApprovedAt(LocalDateTime.now());
        analyticsService.record(AnalyticsService.EV_SELLER_APPROVED, seller.getId(),
                seller.getMobileNumber(), null, "approved by " + actingAdmin.getMobileNumber());
        return userRepository.save(seller);
    }

    @Transactional
    public User rejectSeller(Long sellerId, String reason, User actingAdmin) {
        User seller = requireSeller(sellerId);
        seller.setSellerApprovalStatus(SellerApprovalStatus.REJECTED);
        seller.setSellerStatusReason(reason);
        analyticsService.record(AnalyticsService.EV_SELLER_APPROVED, seller.getId(),
                seller.getMobileNumber(), null, "rejected by " + actingAdmin.getMobileNumber());
        return userRepository.save(seller);
    }

    @Transactional
    public User suspendSeller(Long sellerId, String reason, User actingAdmin) {
        User seller = requireSeller(sellerId);
        seller.setSellerApprovalStatus(SellerApprovalStatus.SUSPENDED);
        seller.setSellerStatusReason(reason);
        analyticsService.record(AnalyticsService.EV_SELLER_APPROVED, seller.getId(),
                seller.getMobileNumber(), null, "suspended by " + actingAdmin.getMobileNumber());
        return userRepository.save(seller);
    }

    private User requireSeller(Long sellerId) {
        User user = userRepository.findById(sellerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + sellerId));
        if (user.getRole() != UserRole.SELLER) {
            throw new IllegalArgumentException("User " + sellerId + " is not a seller.");
        }
        return user;
    }

    // ---------------- Admin account management (Super Admin) ----------------

    @Transactional(readOnly = true)
    public List<User> listAdmins() {
        List<User> admins = userRepository.findByRole(UserRole.ADMIN);
        admins.addAll(userRepository.findByRole(UserRole.SUPER_ADMIN));
        return admins;
    }

    /** Creates an ADMIN, or upgrades an existing BUYER/SELLER account to ADMIN. */
    @Transactional
    public User createAdmin(String name, String mobileNumber) {
        User user = userRepository.findByMobileNumber(mobileNumber).orElse(null);
        if (user == null) {
            user = new User(name == null || name.isBlank() ? "Admin" : name, mobileNumber, null, UserRole.ADMIN);
        } else if (user.getRole() == UserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("This account is already a Super Admin.");
        } else {
            user.setRole(UserRole.ADMIN);
            if (name != null && !name.isBlank()) user.setName(name);
        }
        return userRepository.save(user);
    }

    /** Demotes an ADMIN back to BUYER. Super Admin accounts can never be demoted. */
    @Transactional
    public User demoteAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("Super Admin accounts cannot be demoted.");
        }
        if (user.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("User " + userId + " is not an admin.");
        }
        user.setRole(UserRole.BUYER);
        return userRepository.save(user);
    }

    /** Idempotent bootstrap of the platform's built-in privileged accounts. */
    @Transactional
    public void ensureBootstrapAccounts() {
        if (userRepository.findByMobileNumber(SUPER_ADMIN_MOBILE).isEmpty()) {
            userRepository.save(new User("Super Admin", SUPER_ADMIN_MOBILE, null, UserRole.SUPER_ADMIN));
        }
        if (userRepository.findByMobileNumber(ADMIN_MOBILE).isEmpty()) {
            userRepository.save(new User("Platform Admin", ADMIN_MOBILE, null, UserRole.ADMIN));
        }
    }

    /** Legacy sellers existed before the approval workflow: grandfather them in as APPROVED. */
    @Transactional
    public void approveLegacySellers() {
        for (User seller : userRepository.findByRole(UserRole.SELLER)) {
            if (seller.getSellerApprovalStatus() == null) {
                seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
                seller.setApprovedAt(LocalDateTime.now());
                userRepository.save(seller);
            }
        }
    }

    /** Platform analytics summary (delegates to AnalyticsService). */
    @Transactional(readOnly = true)
    public Map<String, Object> analyticsSummary() {
        return analyticsService.summary();
    }
}

