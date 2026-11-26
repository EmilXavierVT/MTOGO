package app.features;

import app.exceptions.ApiException;

@FunctionalInterface
public interface FeatureFlags {
    boolean enabled(FeatureKey key);

    /** Call after independent authentication/ownership checks, before gated side effects. */
    default void requireEnabled(FeatureKey key) {
        if (!enabled(key)) {
            throw new ApiException(404, "Feature unavailable");
        }
    }
}
