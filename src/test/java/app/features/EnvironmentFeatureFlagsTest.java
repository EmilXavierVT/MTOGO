package app.features;

import app.exceptions.ApiException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentFeatureFlagsTest {
    private static final FeatureKey TEST = new FeatureKey("MTOGO_FEATURE_TEST");

    @Test
    void onlyExplicitTrueEnablesARegisteredFlag() {
        for (String input : List.of("true", "TRUE", "  TrUe \t", "false", "FALSE", "  false  ", "", " ", "1", "yes")) {
            List<String> warnings = new ArrayList<>();
            FeatureFlags flags = new EnvironmentFeatureFlags(Set.of(TEST), name -> input, warnings::add);
            assertEquals(input.trim().equalsIgnoreCase("true"), flags.enabled(TEST), input);
            assertEquals(input.trim().equalsIgnoreCase("true") || input.trim().equalsIgnoreCase("false")
                    ? List.of() : List.of(TEST.environmentVariable()), warnings);
        }
        List<String> warnings = new ArrayList<>();
        FeatureFlags missing = new EnvironmentFeatureFlags(Set.of(TEST), name -> null, warnings::add);
        assertFalse(missing.enabled(TEST));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void snapshotsReadEachRegisteredValueOnceAndIgnoreUnregisteredValues() {
        Map<String, String> source = new HashMap<>(Map.of(TEST.environmentVariable(), "true",
                "MTOGO_FEATURE_UNKNOWN", "true", "SECRET_KEY", "private-value"));
        AtomicInteger reads = new AtomicInteger();
        FeatureFlags flags = new EnvironmentFeatureFlags(Set.of(TEST), name -> {
            reads.incrementAndGet();
            return source.get(name);
        }, name -> fail("Valid value must not warn"));
        source.put(TEST.environmentVariable(), "false");
        assertTrue(flags.enabled(TEST));
        assertTrue(flags.enabled(TEST));
        assertFalse(flags.enabled(new FeatureKey("MTOGO_FEATURE_UNKNOWN")));
        assertEquals(1, reads.get());
        assertFalse(new EnvironmentFeatureFlags(Set.of(TEST), source::get, name -> fail()).enabled(TEST));
    }

    @Test
    void invalidConfigurationWarnsOnceByNameWithoutExposingTheValue() {
        List<String> warnings = new ArrayList<>();
        FeatureFlags flags = new EnvironmentFeatureFlags(Set.of(TEST), name -> "private-invalid-value", warnings::add);
        assertFalse(flags.enabled(TEST));
        assertFalse(flags.enabled(TEST));
        assertEquals(List.of(TEST.environmentVariable()), warnings);
    }

    @Test
    void keysAndPublicCatalogRejectAccidentalConfigurationExposure() {
        assertThrows(IllegalArgumentException.class, () -> new FeatureKey("SECRET_KEY"));
        assertThrows(IllegalArgumentException.class, () -> new FeatureKey("MTOGO_FEATURE_TEST\nSECRET_KEY"));
        assertThrows(IllegalArgumentException.class, () -> new FeatureCatalog(Set.of(), Map.of("test", TEST)));
        assertThrows(IllegalArgumentException.class, () -> new FeatureCatalog(Set.of(TEST), Map.of("SECRET_KEY", TEST)));
        Map<String, FeatureKey> source = new HashMap<>(Map.of("test", TEST));
        FeatureCatalog catalog = new FeatureCatalog(Set.of(TEST), source);
        source.clear();
        assertEquals(Map.of("test", TEST), catalog.publicCapabilities());
        assertEquals(Set.of(FeatureCatalog.CUSTOMER_FEEDBACK), FeatureCatalog.APPLICATION.registered());
        assertEquals(Map.of("customerFeedback", FeatureCatalog.CUSTOMER_FEEDBACK),
                FeatureCatalog.APPLICATION.publicCapabilities());
    }

    @Test
    void guardReturnsTheExisting404ErrorBeforeAnOperation() {
        FeatureFlags disabled = key -> false;
        ApiException unavailable = assertThrows(ApiException.class, () -> disabled.requireEnabled(TEST));
        assertEquals(404, unavailable.getCode());
        assertEquals("Feature unavailable", unavailable.getMessage());
        FeatureFlags enabled = key -> true;
        assertDoesNotThrow(() -> enabled.requireEnabled(TEST));
    }
}
