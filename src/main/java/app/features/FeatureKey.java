package app.features;

/** A named application flag; declare shared constants when enrolling a product feature. */
public record FeatureKey(String environmentVariable) {
    public FeatureKey {
        if (environmentVariable == null || !environmentVariable.matches("MTOGO_FEATURE_[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException("Feature environment names must start with MTOGO_FEATURE_");
        }
    }
}
