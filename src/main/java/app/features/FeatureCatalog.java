package app.features;

import java.util.Map;
import java.util.Set;

/** Registration is explicit; process environment values never enroll flags or public capabilities. */
public record FeatureCatalog(Set<FeatureKey> registered, Map<String, FeatureKey> publicCapabilities) {
    public static final FeatureKey CUSTOMER_FEEDBACK = new FeatureKey("MTOGO_FEATURE_CUSTOMER_FEEDBACK");
    public static final FeatureCatalog APPLICATION = new FeatureCatalog(
            Set.of(CUSTOMER_FEEDBACK), Map.of("customerFeedback", CUSTOMER_FEEDBACK));

    public FeatureCatalog {
        registered = Set.copyOf(registered);
        publicCapabilities = Map.copyOf(publicCapabilities);
        if (!registered.containsAll(publicCapabilities.values())) {
            throw new IllegalArgumentException("Public capabilities must refer to registered flags");
        }
        for (String key : publicCapabilities.keySet()) {
            if (!key.matches("[a-z][A-Za-z0-9]*")) {
                throw new IllegalArgumentException("Public capability names must use lower camel case");
            }
        }
    }
}
