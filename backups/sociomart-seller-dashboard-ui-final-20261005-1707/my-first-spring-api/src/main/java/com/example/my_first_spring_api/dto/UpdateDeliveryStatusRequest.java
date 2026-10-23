package com.example.my_first_spring_api.dto;

import com.example.my_first_spring_api.model.DeliveryStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Body of the individual delivery toggle.
 *
 * <p>No sellerId is accepted: the acting seller is derived from the authenticated
 * session, never from anything the client can supply, so tampering with an
 * orderId/offeringId cannot reach another seller's order.</p>
 */
public class UpdateDeliveryStatusRequest {

    @NotNull(message = "deliveryStatus is required")
    private DeliveryStatus deliveryStatus;

    public UpdateDeliveryStatusRequest() {
    }

    public UpdateDeliveryStatusRequest(DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public DeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(DeliveryStatus deliveryStatus) { this.deliveryStatus = deliveryStatus; }
}