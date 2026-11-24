package app.controller;

import app.dao.RestaurantDao;
import app.entities.restaurant.Menu;
import app.entities.restaurant.Product;
import app.entities.restaurant.Restaurant;
import app.exceptions.ApiException;
import io.javalin.http.Context;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;


public class RestaurantController{

    RestaurantDao restaurantDao;

    public RestaurantController(RestaurantDao restaurantDao){ this.restaurantDao = restaurantDao; }

    public record RestaurantResponse(UUID id, String address, Double rating){
        private static RestaurantResponse from(Restaurant restaurant){
            return new RestaurantResponse (restaurant.getId(), restaurant.getAddress(), restaurant.getRating());
        }
    }

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

    public void displayRestaurants(Context ctx){
        ctx.json(restaurantDao.getAllRestaurants().stream().map(RestaurantResponse :: from).toList());
    }

    public void displayRestaurantMenus(Context ctx){
        UUID restaurantId = pathId(ctx, "restaurantId");
        ctx.json(restaurantDao.getAllRestaurantMenus(restaurantId).stream().map(MenuResponse :: from).toList());
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
}
