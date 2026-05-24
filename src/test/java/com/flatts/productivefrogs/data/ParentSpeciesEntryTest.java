package com.flatts.productivefrogs.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Codec round-trip tests for {@link ParentSpeciesEntry}. Pins the JSON
 * shape that ships at {@code data/<ns>/productivefrogs/parent_species/...}
 * so a future codec tweak doesn't silently break datapack overrides.
 */
class ParentSpeciesEntryTest {

    private static final String JSON_VANILLA = """
        {
          "entity_type": "minecraft:slime",
          "category": "bog"
        }
        """;

    private static final String JSON_PF = """
        {
          "entity_type": "productivefrogs:cave_slime",
          "category": "cave"
        }
        """;

    @Test
    void decodesVanillaParentSpeciesEntry() {
        ParentSpeciesEntry entry = decode(JSON_VANILLA);
        assertEquals(ResourceLocation.parse("minecraft:slime"), entry.entityType());
        assertEquals(Category.BOG, entry.category());
    }

    @Test
    void decodesPfParentSpeciesEntry() {
        ParentSpeciesEntry entry = decode(JSON_PF);
        assertEquals(ResourceLocation.parse("productivefrogs:cave_slime"), entry.entityType());
        assertEquals(Category.CAVE, entry.category());
    }

    @Test
    void codecRoundTrip() {
        ParentSpeciesEntry original = new ParentSpeciesEntry(
            ResourceLocation.parse("productivefrogs:void_slime"),
            Category.VOID
        );
        JsonElement encoded = ParentSpeciesEntry.CODEC.encodeStart(JsonOps.INSTANCE, original)
            .result()
            .orElseThrow();
        ParentSpeciesEntry decoded = ParentSpeciesEntry.CODEC.parse(JsonOps.INSTANCE, encoded)
            .result()
            .orElseThrow();
        assertEquals(original, decoded);
    }

    @Test
    void missingEntityTypeFieldFailsCodec() {
        // Defensive: a datapack JSON missing the required entity_type field
        // should produce a codec error rather than silently decode to a
        // bogus entry that would never match anything in the lookup.
        DataResult<ParentSpeciesEntry> result = ParentSpeciesEntry.CODEC.parse(
            JsonOps.INSTANCE,
            JsonParser.parseString("""
                { "category": "bog" }
            """)
        );
        assertNull(result.result().orElse(null), "missing entity_type should not decode");
        assertNotNull(result.error().orElse(null), "missing entity_type should surface a codec error");
    }

    @Test
    void unknownCategoryValueFailsCodec() {
        DataResult<ParentSpeciesEntry> result = ParentSpeciesEntry.CODEC.parse(
            JsonOps.INSTANCE,
            JsonParser.parseString("""
                {
                  "entity_type": "minecraft:slime",
                  "category": "not_a_real_category"
                }
            """)
        );
        assertNull(result.result().orElse(null), "unknown category id should not decode");
        assertNotNull(result.error().orElse(null), "unknown category should surface a codec error");
    }

    private static ParentSpeciesEntry decode(String json) {
        JsonElement parsed = JsonParser.parseString(json);
        var result = ParentSpeciesEntry.CODEC.parse(JsonOps.INSTANCE, parsed);
        return result.result().orElseThrow(() ->
            new AssertionError("decode failed for json:\n" + json
                + "\nerror: " + result.error().map(Object::toString).orElse("<unknown>")));
    }
}
