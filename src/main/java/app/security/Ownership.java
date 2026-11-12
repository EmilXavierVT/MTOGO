package app.security;

import app.entities.enums.Role;
import app.exceptions.ApiException;
import java.util.UUID;

/** Call after loading the resource; always use the owner stored in the database. */
public final class Ownership {
    private Ownership() {}
    public static void requireCustomer(SecurityController.CurrentUser actor, UUID customerId) {
        require(actor, Role.CUSTOMER, customerId);
    }
    public static void requireRestaurantOwner(SecurityController.CurrentUser actor, UUID restaurantOwnerId) {
        require(actor, Role.RESTAURANT_OWNER, restaurantOwnerId);
    }
    private static void require(SecurityController.CurrentUser actor, Role role, UUID ownerId) {
        if (actor == null || actor.role() != role || !actor.id().equals(ownerId))
            throw new ApiException(403, "Access denied");
    }
}
