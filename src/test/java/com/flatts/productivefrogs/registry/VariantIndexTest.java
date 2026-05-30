package com.flatts.productivefrogs.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Sync-gate for {@code productivefrogs/variants_index.json}: it must list exactly
 * the shipped {@code slime_variant/*.json} ids. The index is what
 * {@link com.flatts.productivefrogs.setup.VariantFluidDiscovery} reads at mod-init
 * to mint per-variant milk fluids (the variant registry isn't loaded yet then), so
 * a variant added to the registry but missing from the index would silently get no
 * milk. Regenerate with {@code python scripts/generate_variants_index.py}.
 */
class VariantIndexTest {

    @Test
    void indexMatchesShippedVariantSet() {
        Path root = resourcesRoot();
        Set<String> shipped = shippedVariantIds(root);
        Set<String> indexed = indexedVariantIds(root);

        Set<String> missingFromIndex = new TreeSet<>(shipped);
        missingFromIndex.removeAll(indexed);
        Set<String> staleInIndex = new TreeSet<>(indexed);
        staleInIndex.removeAll(shipped);

        assertTrue(missingFromIndex.isEmpty() && staleInIndex.isEmpty(),
            () -> "variants_index.json is out of sync with the slime_variant registry.\n"
                + "  Missing from index (would get NO milk): " + missingFromIndex + "\n"
                + "  Stale in index (no such variant): " + staleInIndex + "\n"
                + "Run: python scripts/generate_variants_index.py");
        assertEquals(shipped, indexed, "index and shipped set must be identical");
    }

    private static Set<String> shippedVariantIds(Path root) {
        Path dir = root.resolve("data/productivefrogs/productivefrogs/slime_variant");
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString())
                .filter(n -> n.endsWith(".json"))
                .map(n -> n.substring(0, n.length() - ".json".length()))
                .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException("could not list slime_variant dir", e);
        }
    }

    private static Set<String> indexedVariantIds(Path root) {
        Path index = root.resolve("productivefrogs/variants_index.json");
        try {
            JsonObject json = JsonParser.parseString(Files.readString(index)).getAsJsonObject();
            Set<String> ids = new TreeSet<>();
            json.getAsJsonArray("variants").forEach(e -> ids.add(e.getAsString()));
            return ids;
        } catch (IOException e) {
            throw new UncheckedIOException("could not read variants_index.json", e);
        }
    }

    private static Path resourcesRoot() {
        try {
            // <root>/productivefrogs/variants_index.json -> two parents up is <root>.
            Path index = Paths.get(VariantIndexTest.class
                .getResource("/productivefrogs/variants_index.json").toURI());
            return index.getParent().getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("could not locate variants_index.json on the test classpath", e);
        }
    }
}
