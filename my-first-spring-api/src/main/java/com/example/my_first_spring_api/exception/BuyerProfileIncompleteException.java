package com.example.my_first_spring_api.exception;

/**
 * Thrown when a buyer attempts to place an order but their profile is incomplete.
 * The buyer must provide society, building/wing, and flat/house number before ordering.
 */
public class BuyerProfileIncompleteException extends RuntimeException {
    public BuyerProfileIncompleteException(String message) {
        super(message);
    }
}
