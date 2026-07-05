package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.ProductiveFrogs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Every registered Productive Frogs item must have a TRANSLATED display name
 * (maintainer bug report 2026-07-03: the seven primed-egg block items rendered
 * as raw {@code item.productivefrogs.<name>} keys - registered without
 * {@code useBlockDescriptionPrefix()}, their description ids pointed at item.*
 * keys while the lang file only carries block.*).
 *
 * <p>The check renders each item's DEFAULT stack hover name exactly as the
 * client would (NeoForge loads every mod's {@code en_us} server-side, so
 * translatable components resolve here) and fails if the result is the raw
 * description id. Component-named items (variant buckets, froglights) resolve
 * their unstamped base key, which this covers too.
 */
final class ItemNameCompletenessTests {

    private ItemNameCompletenessTests() {
    }

    static void register() {
        PFGameTests.test("every_pf_item_has_a_translated_name", 20,
            ItemNameCompletenessTests::everyPfItemHasATranslatedName);
    }

    private static void everyPfItemHasATranslatedName(GameTestHelper helper) {
        List<String> missing = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            var key = BuiltInRegistries.ITEM.getKey(item);
            if (!ProductiveFrogs.MOD_ID.equals(key.getNamespace())) {
                continue;
            }
            String rendered = new ItemStack(item).getHoverName().getString();
            // An untranslated key renders as itself: dotted, starting item./block.
            if (rendered.equals(item.getDescriptionId())
                    || (rendered.contains(".") && rendered.startsWith("item.") || rendered.startsWith("block."))) {
                missing.add(key + " -> \"" + rendered + "\"");
            }
        }
        if (!missing.isEmpty()) {
            helper.fail("items with untranslated names (" + missing.size() + "): "
                + String.join(", ", missing.subList(0, Math.min(10, missing.size()))));
            return;
        }
        helper.succeed();
    }
}
