package app.controller;

import app.features.FeatureCatalog;
import app.features.FeatureFlags;
import io.javalin.http.Context;
import java.util.HashMap;
import java.util.Map;

public final class FeatureController {
    private final FeatureFlags flags;
    private final FeatureCatalog catalog;

    public FeatureController(FeatureFlags flags, FeatureCatalog catalog) {
        this.flags = flags;
        this.catalog = catalog;
    }

    public void show(Context ctx) {
        Map<String, Boolean> capabilities = new HashMap<>();
        catalog.publicCapabilities().forEach((name, key) -> capabilities.put(name, flags.enabled(key)));
        ctx.header("Cache-Control", "no-store").json(capabilities);
    }
}
