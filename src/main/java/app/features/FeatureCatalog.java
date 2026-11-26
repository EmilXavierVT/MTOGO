package app.features;

import java.util.Map;
import java.util.Set;

/** Registration is explicit; process environment values never enroll flags or public capabilities. */
public record FeatureCatalog(Set<FeatureKey> registered, Map<String, FeatureKey> publicCapabilities) {
    // The first product flag is deliberately deferred to its own issue.
    public static final FeatureCatalog APPLICATION = new FeatureCatalog(Set.of(), Map.of());

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
