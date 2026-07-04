package com.flatts.productivefrogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.advancement.FrogProducedTrigger;
import com.flatts.productivefrogs.data.Category;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Integrity gate for the hand-authored advancement set (#183). These pin the
 * structure that a GameTest can't (advancements are client-display, and the
 * in-world grant needs a live player), and catch the regressions a JSON set is
 * prone to: a mistyped Category, a tier dropped or duplicated, or a criterion
 * pointing at a trigger id that doesn't exist (which fails silently at datapack
 * load). Pure JSON + enum checks - no Minecraft bootstrap, matching the
 * {@code LangCompletenessTest} style.
 */
class AdvancementSetTest {

    /** Trigger ids the set is allowed to use: our custom one + the vanilla item gate. */
    private static final Set<String> ALLOWED_TRIGGERS = Set.of(
        "productivefrogs:frog_produced",
        "productivefrogs:predation_milestone",
        "minecraft:inventory_changed"
    );

    private static final Path ADVANCEMENT_DIR = advancementDir();

    /** Locate the shipped advancement dir off the test classpath (working-dir independent). */
    private static Path advancementDir() {
        try {
            return Paths.get(AdvancementSetTest.class
                .getResource("/data/productivefrogs/advancement/root.json").toURI()).getParent();
        } catch (URISyntaxException | NullPointerException e) {
            throw new IllegalStateException("could not locate the advancement dir on the test classpath", e);
        }
    }

    private static List<Path> advancementFiles() {
        try (Stream<Path> files = Files.list(ADVANCEMENT_DIR)) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("could not list " + ADVANCEMENT_DIR, e);
        }
    }

    private static JsonObject parse(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }

    @Test
    void everyAdvancementHasNonEmptyCriteriaAndRequirements() {
        List<String> problems = new ArrayList<>();
        for (Path file : advancementFiles()) {
            JsonObject advancement = parse(file);
            String name = file.getFileName().toString();
            if (!advancement.has("criteria") || advancement.getAsJsonObject("criteria").size() == 0) {
                problems.add(name + ": no criteria");
            }
            if (!advancement.has("requirements") || advancement.getAsJsonArray("requirements").isEmpty()) {
                problems.add(name + ": no requirements");
            }
        }
        assertTrue(problems.isEmpty(), () -> "Malformed advancement(s):\n  " + String.join("\n  ", problems));
    }

    @Test
    void everyCriterionUsesAKnownTrigger() {
        List<String> offenders = new ArrayList<>();
        for (Path file : advancementFiles()) {
            JsonObject criteria = parse(file).getAsJsonObject("criteria");
            for (String criterionName : criteria.keySet()) {
                String trigger = criteria.getAsJsonObject(criterionName).get("trigger").getAsString();
                if (!ALLOWED_TRIGGERS.contains(trigger)) {
                    offenders.add(file.getFileName() + " -> " + criterionName + ": " + trigger);
                }
            }
        }
        assertTrue(offenders.isEmpty(),
            () -> "Advancement criterion uses an unknown trigger id (typo -> silent load failure):\n  "
                + String.join("\n  ", offenders));
    }

    @Test
    void perTierAdvancementsCoverEveryCategoryExactlyOnce() {
        List<String> categoryValues = new ArrayList<>();
        for (Path file : advancementFiles()) {
            JsonObject criteria = parse(file).getAsJsonObject("criteria");
            for (String criterionName : criteria.keySet()) {
                JsonObject criterion = criteria.getAsJsonObject(criterionName);
                if (!"productivefrogs:frog_produced".equals(criterion.get("trigger").getAsString())) {
                    continue;
                }
                categoryValues.add(criterion.getAsJsonObject("conditions").get("category").getAsString());
            }
        }

        // Every category string must resolve to a real Category (catches a typo).
        EnumSet<Category> covered = EnumSet.noneOf(Category.class);
        List<String> invalid = new ArrayList<>();
        for (String id : categoryValues) {
            Category category = byId(id);
            if (category == null) {
                invalid.add(id);
            } else {
                assertTrue(covered.add(category), "category covered by more than one per-tier advancement: " + id);
            }
        }
        assertTrue(invalid.isEmpty(), () -> "frog_produced advancement names an unknown category: " + invalid);

        // The set must have exactly one per-tier node per species - a new Category
        // added to the enum should fail this until its tier advancement is authored.
        assertEquals(EnumSet.allOf(Category.class), covered,
            "per-tier advancements must cover every Category exactly once");
    }

    @Test
    void frogProducedTriggerMatchesOnlyItsOwnCategory() {
        for (Category own : Category.values()) {
            FrogProducedTrigger.TriggerInstance instance =
                new FrogProducedTrigger.TriggerInstance(Optional.empty(), own);
            for (Category other : Category.values()) {
                assertEquals(own == other, instance.matches(other),
                    "instance for " + own + " matching " + other);
            }
        }
    }

    private static Category byId(String id) {
        for (Category category : Category.values()) {
            if (category.id().equals(id)) {
                return category;
            }
        }
        return null;
    }
}
