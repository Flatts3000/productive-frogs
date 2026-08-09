package com.flatts.productivefrogs.compat.ironfurnaces;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/**
 * Coverage for which stacks bypass Iron Furnaces' item-keyed smeltability cache.
 *
 * <p>The bypass decision is the whole safety story. Bypass too little and
 * Froglights stay locked out of furnaces; bypass too much and every plain item
 * loses the cache Iron Furnaces added deliberately on a hot insertion check.
 * The in-world half - that the patch actually reaches the class and unpoisons
 * the cache - is {@code PFGameTests.ironFurnacesAcceptsFroglightsAfterACacheMiss}.
 */
class RecipeCacheFixTest {

    @Test
    void froglightsWithAVariantBypassTheCache() {
        ItemStack stack = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
        stack.set(PFDataComponents.SLIME_VARIANT.get(), ResourceLocation.parse("productivefrogs:iron"));
        assertTrue(RecipeCacheFix.needsComponentAwareCheck(stack),
            "a variant Froglight's smeltability depends on its component, so it cannot use an item-keyed cache");
    }

    /**
     * The stack that poisons the cache upstream. It carries no components, so it
     * takes Iron Furnaces' original path - which is correct: it genuinely has no
     * recipe. The patch's job is to stop that answer applying to other stacks,
     * not to change this one.
     */
    @Test
    void aVariantlessFroglightUsesTheOriginalPath() {
        assertFalse(RecipeCacheFix.needsComponentAwareCheck(new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get())),
            "a componentless stack is exactly what the item-keyed cache can represent");
    }

    @Test
    void plainItemsKeepTheirCache() {
        assertFalse(RecipeCacheFix.needsComponentAwareCheck(new ItemStack(Items.IRON_ORE, 64)),
            "ordinary smeltables must not lose the cache; that is the case it was built for");
    }

    /** Not Froglight-specific: any mod's component item is exposed the same way. */
    @Test
    void anyComponentCarryingStackBypasses() {
        ItemStack named = new ItemStack(Items.IRON_ORE);
        named.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Sample"));
        assertTrue(RecipeCacheFix.needsComponentAwareCheck(named));
    }

    @Test
    void emptyStacksAreNeverIntercepted() {
        assertFalse(RecipeCacheFix.needsComponentAwareCheck(ItemStack.EMPTY));
    }

    @Test
    void aNullLevelAnswersFalseRatherThanThrowing() {
        ItemStack stack = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
        stack.set(PFDataComponents.SLIME_VARIANT.get(), ResourceLocation.parse("productivefrogs:iron"));
        assertFalse(RecipeCacheFix.hasRecipe(null, net.minecraft.world.item.crafting.RecipeType.SMELTING, stack),
            "a furnace with no level cannot know, and must not throw inside someone else's tick");
    }
}
