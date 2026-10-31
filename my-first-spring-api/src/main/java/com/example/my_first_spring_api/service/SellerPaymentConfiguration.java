package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.GpayIdentifierType;
import com.example.my_first_spring_api.model.SellerPaymentMode;

import java.util.Locale;
import java.util.regex.Pattern;

final class SellerPaymentConfiguration {
    private static final Pattern UPI_ID = Pattern.compile("^[A-Za-z0-9._-]{2,100}@[A-Za-z0-9.-]{2,100}$");
    private static final Pattern INDIAN_PHONE = Pattern.compile("^(?:\\+91)?[6-9]\\d{9}$");

    private SellerPaymentConfiguration() {}

    static SellerPaymentMode parseMode(String value) {
        return parseEnum(value, SellerPaymentMode.class, "Choose a supported seller payment option.");
    }

    static GpayIdentifierType parseGpayIdentifierType(String value) {
        return parseEnum(value, GpayIdentifierType.class, "Choose whether the Google Pay identifier is a phone number or UPI ID.");
    }

    private static <T extends Enum<T>> T parseEnum(String value, Class<T> enumType, String error) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(error);
        }
    }

    static void validate(SellerPaymentMode mode, String upiId,
                         GpayIdentifierType gpayIdentifierType, String gpayIdentifier) {
        String normalizedUpiId = clean(upiId);
        String normalizedGpayIdentifier = clean(gpayIdentifier);
        if (mode == null || mode == SellerPaymentMode.CASH_ONLY) return;

        if (mode == SellerPaymentMode.UPI || mode == SellerPaymentMode.GPAY_AND_UPI) {
            if (normalizedUpiId == null || !UPI_ID.matcher(normalizedUpiId).matches()) {
                throw new IllegalArgumentException("Enter a valid UPI ID for the selected payment option.");
            }
        }
        if (mode == SellerPaymentMode.GPAY || mode == SellerPaymentMode.GPAY_AND_UPI) {
            if (gpayIdentifierType == null || normalizedGpayIdentifier == null) {
                throw new IllegalArgumentException("Enter the Google Pay identifier and select its type.");
            }
            boolean valid = switch (gpayIdentifierType) {
                case PHONE_NUMBER -> INDIAN_PHONE.matcher(normalizedGpayIdentifier.replaceAll("[\\s()-]", "")).matches();
                case UPI_ID -> UPI_ID.matcher(normalizedGpayIdentifier).matches();
            };
            if (!valid) {
                throw new IllegalArgumentException(gpayIdentifierType == GpayIdentifierType.PHONE_NUMBER
                        ? "Google Pay phone number must be a valid Indian mobile number."
                        : "Enter a valid Google Pay UPI ID.");
            }
        }
    }

    static boolean allows(SellerPaymentMode mode, String paymentMethod) {
        if (mode == null) return true;
        if (paymentMethod == null || paymentMethod.isBlank()) return false;
        return switch (mode) {
            case GPAY -> "gpay".equalsIgnoreCase(paymentMethod);
            case UPI -> "upi".equalsIgnoreCase(paymentMethod);
            case GPAY_AND_UPI -> "gpay".equalsIgnoreCase(paymentMethod) || "upi".equalsIgnoreCase(paymentMethod);
            case CASH_ONLY -> "cod".equalsIgnoreCase(paymentMethod);
        };
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
