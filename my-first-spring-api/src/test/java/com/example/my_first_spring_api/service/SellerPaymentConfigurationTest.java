package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.GpayIdentifierType;
import com.example.my_first_spring_api.model.SellerPaymentMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SellerPaymentConfigurationTest {
    @Test
    void explicitModesAcceptOnlyTheirConfiguredMethod() {
        assertThat(SellerPaymentConfiguration.allows(SellerPaymentMode.GPAY, "gpay")).isTrue();
        assertThat(SellerPaymentConfiguration.allows(SellerPaymentMode.GPAY, "upi")).isFalse();
        assertThat(SellerPaymentConfiguration.allows(SellerPaymentMode.UPI, "upi")).isTrue();
        assertThat(SellerPaymentConfiguration.allows(SellerPaymentMode.UPI, "cod")).isFalse();
        assertThat(SellerPaymentConfiguration.allows(SellerPaymentMode.GPAY_AND_UPI, "gpay")).isTrue();
        assertThat(SellerPaymentConfiguration.allows(SellerPaymentMode.GPAY_AND_UPI, "upi")).isTrue();
        assertThat(SellerPaymentConfiguration.allows(SellerPaymentMode.CASH_ONLY, "cod")).isTrue();
        assertThat(SellerPaymentConfiguration.allows(SellerPaymentMode.CASH_ONLY, "card")).isFalse();
        assertThat(SellerPaymentConfiguration.allows(null, "card")).isTrue();
    }

    @Test
    void gpayIdentifierTypeMakesPhoneAndUpiRepresentationExplicit() {
        assertThatCodeForGpay(GpayIdentifierType.PHONE_NUMBER, "9876543210");
        assertThatCodeForGpay(GpayIdentifierType.PHONE_NUMBER, "+91 9876543210");
        assertThatCodeForGpay(GpayIdentifierType.UPI_ID, "cook@examplebank");

        assertThatThrownBy(() -> SellerPaymentConfiguration.validate(
                SellerPaymentMode.GPAY, null, null, "9876543210"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("select its type");
        assertThatThrownBy(() -> SellerPaymentConfiguration.validate(
                SellerPaymentMode.GPAY, null, GpayIdentifierType.PHONE_NUMBER, "not-a-phone"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Indian mobile number");
    }

    @Test
    void eachDigitalConfigurationRequiresItsOwnIdentifier() {
        assertThatThrownBy(() -> SellerPaymentConfiguration.validate(
                SellerPaymentMode.UPI, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid UPI ID");
        assertThatThrownBy(() -> SellerPaymentConfiguration.validate(
                SellerPaymentMode.GPAY_AND_UPI, "cook@examplebank", GpayIdentifierType.UPI_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Google Pay identifier");

        SellerPaymentConfiguration.validate(SellerPaymentMode.CASH_ONLY, null, null, null);
    }

    @Test
    void unknownModesAndIdentifierKindsAreRejected() {
        assertThatThrownBy(() -> SellerPaymentConfiguration.parseMode("CARD"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SellerPaymentConfiguration.parseGpayIdentifierType("PHONE_OR_UPI"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertThatCodeForGpay(GpayIdentifierType type, String identifier) {
        SellerPaymentConfiguration.validate(SellerPaymentMode.GPAY, null, type, identifier);
    }
}
