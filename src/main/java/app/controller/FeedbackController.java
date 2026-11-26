package app.controller;

import app.exceptions.ApiException;
import app.service.FeedbackService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.javalin.http.Context;
import java.util.UUID;

public final class FeedbackController {
    private static final ObjectReader RATINGS_JSON = new ObjectMapper().reader()
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final FeedbackService feedback;

    public FeedbackController(FeedbackService feedback) {
        this.feedback = feedback;
    }

    public void show(Context ctx) {
        ctx.header("Cache-Control", "no-store");
        ctx.json(feedback.find(ctx.attribute("user"), orderId(ctx)));
    }

    public void submit(Context ctx) {
        ctx.header("Cache-Control", "no-store");
        ctx.status(201).json(feedback.submit(ctx.attribute("user"), orderId(ctx), ratings(ctx)));
    }

    private static FeedbackService.Ratings ratings(Context ctx) {
        JsonNode body;
        try {
            body = RATINGS_JSON.readTree(ctx.body());
        } catch (JsonProcessingException e) {
            throw new ApiException(400, "All three ratings must be integers from 1 to 5");
        }
        if (body == null || !body.isObject()) {
            throw new ApiException(400, "All three ratings must be integers from 1 to 5");
        }
        return new FeedbackService.Ratings(rating(body.get("foodRating")),
                rating(body.get("overallExperienceRating")), rating(body.get("courierRating")));
    }

    private static int rating(JsonNode value) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new ApiException(400, "All three ratings must be integers from 1 to 5");
        }
        return value.intValue();
    }

    private static UUID orderId(Context ctx) {
        String value = ctx.pathParam("orderId");
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "orderId must be a UUID");
        }
    }
}
