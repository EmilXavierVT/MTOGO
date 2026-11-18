package app.controller;

import app.dao.OwnerMenuDao;
import app.dao.OwnerMenuDao.ProductDetails;
import app.entities.restaurant.Menu;
import app.entities.restaurant.Product;
import app.exceptions.ApiException;
import app.security.SecurityController.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.javalin.http.Context;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class OwnerMenuController {
    private static final ObjectReader PRODUCT_JSON = new ObjectMapper().reader().with(
            DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final BigDecimal PRICE_LIMIT = BigDecimal.TEN.pow(Product.PRICE_PRECISION - Product.PRICE_SCALE);

    public record MenuResponse(UUID id, String name, List<ProductResponse> products) {
        private static MenuResponse from(Menu menu) {
            return new MenuResponse(menu.getId(), menu.getName(),
                    menu.getProducts().stream().map(ProductResponse::from).toList());
        }
    }

    public record ProductResponse(UUID id, String nameDa, String descriptionDa, String nameEn,
                                  String descriptionEn, BigDecimal priceDkkInclVat, boolean available) {
        private static ProductResponse from(Product product) {
            return new ProductResponse(product.getId(), product.getName(), product.getDescriptionDa(),
                    product.getNameEn(), product.getDescriptionEn(), product.getPrice(), product.isAvailable());
        }
    }

    private final OwnerMenuDao menus;

    public OwnerMenuController(OwnerMenuDao menus) {
        this.menus = menus;
    }

    public void listMenus(Context ctx) {
        CurrentUser owner = ctx.attribute("user");
        ctx.json(menus.findMenus(owner.id()).stream().map(MenuResponse::from).toList());
    }

    public void createProduct(Context ctx) {
        CurrentUser owner = ctx.attribute("user");
        Product product = menus.createProduct(owner, pathId(ctx, "menuId"), readDetails(ctx));
        ctx.status(201).json(ProductResponse.from(product));
    }

    public void updateProduct(Context ctx) {
        CurrentUser owner = ctx.attribute("user");
        UUID menuId = pathId(ctx, "menuId");
        UUID productId = pathId(ctx, "productId");
        Product product = menus.updateProduct(owner, menuId, productId, readDetails(ctx));
        ctx.json(ProductResponse.from(product));
    }

    private static UUID pathId(Context ctx, String field) {
        String value = ctx.pathParam(field);
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException();
            }
            return id;
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, field + " must be a UUID");
        }
    }

    private static ProductDetails readDetails(Context ctx) {
        JsonNode body = readProductJson(ctx);
        String nameDa = requiredText(body, "nameDa");
        String nameEn = optionalText(body, "nameEn");
        checkNameLength(nameDa, "nameDa");
        checkNameLength(nameEn, "nameEn");
        return new ProductDetails(nameDa, requiredText(body, "descriptionDa"), nameEn,
                optionalText(body, "descriptionEn"), price(body.path("priceDkkInclVat")),
                availability(body.path("available")));
    }

    private static JsonNode readProductJson(Context ctx) {
        JsonNode body;
        try {
            body = PRODUCT_JSON.readTree(ctx.body());
        } catch (JsonProcessingException e) {
            throw new ApiException(400, "Invalid product JSON");
        }
        if (body == null || !body.isObject()) {
            throw new ApiException(400, "Product body must be a JSON object");
        }
        return body;
    }

    private static String requiredText(JsonNode body, String field) {
        String text = optionalText(body, field);
        if (text == null) {
            throw new ApiException(400, field + " is required");
        }
        return text;
    }

    private static String optionalText(JsonNode body, String field) {
        JsonNode node = body.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isTextual() || node.textValue().indexOf('\0') >= 0) {
            throw new ApiException(400, field + " must be valid text");
        }
        String text = node.textValue().strip();
        return text.isEmpty() ? null : text;
    }

    private static void checkNameLength(String name, String field) {
        if (name != null && name.length() > Product.NAME_MAX_LENGTH) {
            throw new ApiException(400, field + " must be at most " + Product.NAME_MAX_LENGTH + " characters");
        }
    }

    private static BigDecimal price(JsonNode node) {
        BigDecimal amount = node.decimalValue();
        if (!node.isNumber() || amount.signum() <= 0 || amount.compareTo(PRICE_LIMIT) >= 0
                || amount.stripTrailingZeros().scale() > Product.PRICE_SCALE) {
            throw new ApiException(400, "priceDkkInclVat must be a positive number in whole øre (max 9999999999.99 DKK)");
        }
        return amount.setScale(Product.PRICE_SCALE);
    }

    private static boolean availability(JsonNode node) {
        if (!node.isBoolean()) {
            throw new ApiException(400, "available must be a boolean");
        }
        return node.booleanValue();
    }
}
