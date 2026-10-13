package com.example.my_first_spring_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "mobile_number", unique = true)
    private String mobileNumber;

    @Column(name = "flat_house_number")
    private String flatHouseNumber;

    @Column(name = "society")
    private String society;
    /**
     * The buyer's locality. Nullable and additive: existing profiles that only
     * ever stored a society keep working and simply have no area until they pick
     * one. Buyer-side only - it has no effect on seller service areas.
     */
    @Column(name = "area")
    private String area;

    @Column(name = "building")
    private String building;

    /**
     * The buyer's saved location as stable IDs.
     *
     * <p>{@link #society} and {@link #area} above remain the display/denormalised
     * strings that historical orders, admin lists and the UI already read, so no
     * existing consumer changes. These two references are the authoritative
     * identity used for eligibility: the backend resolves the buyer's location by
     * {@code societyRef}, never by comparing free text.</p>
     *
     * <p>Nullable because legacy profiles (and sellers, who have no buyer
     * location) legitimately have no resolved location record yet.</p>
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "society_id")
    private Society societyRef;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "area_id")
    private Area areaRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    /** Approval lifecycle for SELLER accounts (null = legacy seller, treated as APPROVED). */
    @Enumerated(EnumType.STRING)
    @Column(name = "seller_approval_status")
    private SellerApprovalStatus sellerApprovalStatus;

    @Column(name = "seller_status_reason", columnDefinition = "TEXT")
    private String sellerStatusReason;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /**
     * The Admin who performed the last approval decision (handover section 7.1:
     * "Store approval timestamp and Admin who acted").
     *
     * <p>Nullable: legacy rows pre-date the workflow, and system-driven
     * approvals (the demo bootstrap) have no acting Admin.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    /**
     * Buyer account status (handover section 8: "See account status:
     * Active / Blocked").
     *
     * <p>Default {@code false} so every existing row keeps working unchanged;
     * this is the additive pattern already used for {@link #area}.
     */
    @Column(name = "blocked", nullable = false)
    private boolean blocked = false;

    /** Mandatory when blocking: who/what and why. */
    @Column(name = "blocked_reason", columnDefinition = "TEXT")
    private String blockedReason;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    /** Internal support note. Never surfaced to the buyer. */
    @Column(name = "support_note", columnDefinition = "TEXT")
    private String supportNote;

    /**
     * Area an ADMIN is responsible for (handover 15: an Area Admin sees "only
     * assigned Area/cluster").
     *
     * <p>Null means platform-wide, which is how every Admin behaves until a Super
     * Admin deliberately narrows one - so adding the concept cannot silently
     * restrict an existing admin. SUPER_ADMIN ignores this entirely and is always
     * global.
     *
     * <p>Enforced server-side in {@code AdminService}; the UI only reflects it.</p>
     */
    @Column(name = "admin_area_id")
    private Long adminAreaId;

    /**
     * Soft removal marker for a storefront/kitchen (handover section 7.3:
     * "Prefer soft removal; preserve historical/audit references").
     * An existing order keeps its kitchen reference either way; this flag stops
     * the storefront being surfaced as active to buyers.
     */
    @Column(name = "storefront_removed", nullable = false)
    private boolean storefrontRemoved = false;

    @Column(name = "storefront_removed_at")
    private LocalDateTime storefrontRemovedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public User() {}

    public User(String name, String mobileNumber, String flatHouseNumber, UserRole role) {
        this.name = name;
        this.mobileNumber = mobileNumber;
        this.flatHouseNumber = flatHouseNumber;
        this.role = role;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
    public String getFlatHouseNumber() { return flatHouseNumber; }
    public void setFlatHouseNumber(String flatHouseNumber) { this.flatHouseNumber = flatHouseNumber; }
    public String getSociety() { return society; }
    public void setSociety(String society) { this.society = society; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }

    public Society getSocietyRef() { return societyRef; }
    public void setSocietyRef(Society societyRef) { this.societyRef = societyRef; }

    public Area getAreaRef() { return areaRef; }
    public void setAreaRef(Area areaRef) { this.areaRef = areaRef; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public SellerApprovalStatus getSellerApprovalStatus() { return sellerApprovalStatus; }
    public void setSellerApprovalStatus(SellerApprovalStatus sellerApprovalStatus) { this.sellerApprovalStatus = sellerApprovalStatus; }
    public String getSellerStatusReason() { return sellerStatusReason; }
    public void setSellerStatusReason(String sellerStatusReason) { this.sellerStatusReason = sellerStatusReason; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public User getApprovedBy() { return approvedBy; }
    public void setApprovedBy(User approvedBy) { this.approvedBy = approvedBy; }

    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }
    public String getBlockedReason() { return blockedReason; }
    public void setBlockedReason(String blockedReason) { this.blockedReason = blockedReason; }
    public LocalDateTime getBlockedAt() { return blockedAt; }
    public void setBlockedAt(LocalDateTime blockedAt) { this.blockedAt = blockedAt; }
    public String getSupportNote() { return supportNote; }
    public void setSupportNote(String supportNote) { this.supportNote = supportNote; }
    public Long getAdminAreaId() { return adminAreaId; }
    public void setAdminAreaId(Long adminAreaId) { this.adminAreaId = adminAreaId; }
    public boolean isStorefrontRemoved() { return storefrontRemoved; }
    public void setStorefrontRemoved(boolean storefrontRemoved) { this.storefrontRemoved = storefrontRemoved; }
    public LocalDateTime getStorefrontRemovedAt() { return storefrontRemovedAt; }
    public void setStorefrontRemovedAt(LocalDateTime at) { this.storefrontRemovedAt = at; }

    /**
     * Legacy sellers (created before the approval workflow existed) have a null
     * status and are treated as APPROVED so existing behaviour is preserved.
     */
    public boolean isApprovedSeller() {
        return sellerApprovalStatus == null || sellerApprovalStatus == SellerApprovalStatus.APPROVED;
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
