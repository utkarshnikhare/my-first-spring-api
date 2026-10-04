package com.example.my_first_spring_api.model;

/**
 * Lifecycle status of a seller account on the platform.
 * A new seller starts PENDING and must be approved by an Admin
 * before their kitchen is publicly visible / active.
 */
public enum SellerApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /**
     * Admin handover section 7.1 requires a third decision besides Approve and
     * Reject: "Request Changes". The seller stays in the approval queue and is
     * not yet serving, which is what distinguishes this from REJECTED.
     */
    CHANGES_REQUESTED,
    SUSPENDED
}
