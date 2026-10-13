package com.example.my_first_spring_api.exception;

/**
 * The kitchen exists, but it does not serve the buyer's Society.
 *
 * <p>Extends {@link KitchenNotFoundException} on purpose. Concealment is a
 * deliberate part of this project: an out-of-coverage kitchen must be
 * indistinguishable from one that does not exist, so the response stays
 * <b>HTTP 404</b> and callers keep catching {@code KitchenNotFoundException}
 * (see {@code Requirements1920IntegrationTest}). Only the body gains a
 * human-readable reason and a machine-readable
 * {@code KITCHEN_NOT_ELIGIBLE} code, so the UI can show "This kitchen doesn't
 * currently serve your society." plus an "Explore kitchens" action for this
 * case <i>without</i> claiming a service-area restriction for a kitchen that
 * genuinely does not exist.</p>
 */
public class KitchenNotEligibleException extends KitchenNotFoundException {

    /** The exact, user-facing wording required by the acceptance criteria (ASCII apostrophe). */
    public static final String MESSAGE = "This kitchen doesn't currently serve your society.";

    /** Machine-readable code; the frontend branches on this, never on message text. */
    public static final String CODE = "KITCHEN_NOT_ELIGIBLE";

    public KitchenNotEligibleException(Long id) {
        super(id);
    }

    @Override
    public String getMessage() {
        return MESSAGE;
    }
}