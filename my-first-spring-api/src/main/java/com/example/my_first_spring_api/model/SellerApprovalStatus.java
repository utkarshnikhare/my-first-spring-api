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
    SUSPENDED
}
