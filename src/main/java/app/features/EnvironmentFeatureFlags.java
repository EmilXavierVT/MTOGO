package app.features;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Immutable configuration: changing the source environment requires a new application instance. */
public final class EnvironmentFeatureFlags implements FeatureFlags {
    private static final Logger LOG = LoggerFactory.getLogger(EnvironmentFeatureFlags.class);
    private final Map<FeatureKey, Boolean> values;

    public static EnvironmentFeatureFlags fromEnvironment(Set<FeatureKey> registered) {
        return new EnvironmentFeatureFlags(registered, System::getenv,
                name -> LOG.warn("Invalid Boolean configuration for {}; defaulting to disabled", name));
    }

    public EnvironmentFeatureFlags(Set<FeatureKey> registered, Function<String, String> environment,
                                   Consumer<String> invalidConfiguration) {
        Map<FeatureKey, Boolean> snapshot = new HashMap<>();
        for (FeatureKey key : registered) {
            String input = environment.apply(key.environmentVariable());
            boolean enabled = false;
            if (input != null) {
                String value = input.trim();
                if (value.equalsIgnoreCase("true")) {
                    enabled = true;
                } else if (!value.equalsIgnoreCase("false")) {
                    invalidConfiguration.accept(key.environmentVariable());
                }
            }
            snapshot.put(key, enabled);
        }
        values = Map.copyOf(snapshot);
    }

    @Override
    public boolean enabled(FeatureKey key) {
        return values.getOrDefault(key, false);
    }
}
