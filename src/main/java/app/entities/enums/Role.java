package app.entities.enums;

import io.javalin.security.RouteRole;

public enum Role implements RouteRole {
    CUSTOMER, COURIER, RESTAURANT_OWNER, ADMIN
}
