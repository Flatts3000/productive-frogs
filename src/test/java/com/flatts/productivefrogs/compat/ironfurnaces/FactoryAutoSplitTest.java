package com.flatts.productivefrogs.compat.ironfurnaces;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/**
 * Coverage for the Iron Furnaces auto-split replacement.
 *
 * <p>The furnace layout is reproduced rather than imported: a plain container
 * whose slots 7 to 12 are the factory inputs, which is what
 * {@code BlockIronFurnaceTileBase.FACTORY_INPUT} indexes. Nothing here needs
 * Iron Furnaces installed.
 */
class FactoryAutoSplitTest {

    private static final int[] FACTORY_INPUT = {7, 8, 9, 10, 11, 12};
    private static final int TIER_2_START = 0;
    private static final int TIER_2_SIZE = 6;

    private static SimpleContainer furnace() {
        return new SimpleContainer(13);
    }

    private static ItemStack froglight(String variant, int count) {
        ItemStack stack = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get(), count);
        stack.set(PFDataComponents.SLIME_VARIANT.get(), ResourceLocation.parse(variant));
        return stack;
    }

    private static int countOf(SimpleContainer c, String variant) {
        int total = 0;
        for (int slot : FACTORY_INPUT) {
            ItemStack stack = c.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = stack.get(PFDataComponents.SLIME_VARIANT.get());
            if (id != null && id.toString().equals(variant)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int totalCount(SimpleContainer c) {
        int total = 0;
        for (int slot : FACTORY_INPUT) {
            total += c.getItem(slot).getCount();
        }
        return total;
    }

    private static void split(SimpleContainer c, boolean fullCheck) {
        FactoryAutoSplit.split(c, FACTORY_INPUT, fullCheck, TIER_2_START, TIER_2_SIZE);
    }

    /**
     * The reported bug (Sky Frogs #225): a stack of one variant beside a single
     * item of another. Unpatched Iron Furnaces pools them by item id and averages
     * the counts, turning 1 Nether Star Froglight into 33 of them.
     */
    @Test
    void doesNotConvertOneVariantIntoAnother() {
        SimpleContainer c = furnace();
        c.setItem(FACTORY_INPUT[0], froglight("productivefrogs:diamond", 64));
        c.setItem(FACTORY_INPUT[1], froglight("productivefrogs:nether_star", 1));

        split(c, true);

        assertEquals(1, countOf(c, "productivefrogs:nether_star"),
            "the single Nether Star Froglight must stay a single Nether Star Froglight");
        assertEquals(64, countOf(c, "productivefrogs:diamond"),
            "the diamond variant must not be consumed to feed another variant");
        assertEquals(65, totalCount(c), "no items created or destroyed");
    }

    /** The same shape, but with the rare variant last, which is the slot the grouping template comes from. */
    @Test
    void doesNotConvertWhenTheRareVariantIsTheTemplate() {
        SimpleContainer c = furnace();
        c.setItem(FACTORY_INPUT[0], froglight("productivefrogs:nether_star", 1));
        c.setItem(FACTORY_INPUT[1], froglight("productivefrogs:diamond", 64));

        split(c, true);

        assertEquals(1, countOf(c, "productivefrogs:nether_star"));
        assertEquals(64, countOf(c, "productivefrogs:diamond"));
        assertEquals(65, totalCount(c));
    }

    /** A furnace holding a single variant still balances, which is what auto-split is for. */
    @Test
    void balancesASingleVariantAcrossEverySlot() {
        SimpleContainer c = furnace();
        c.setItem(FACTORY_INPUT[0], froglight("productivefrogs:iron", 64));

        split(c, false);

        assertEquals(64, totalCount(c), "no items created or destroyed");
        for (int slot : FACTORY_INPUT) {
            int count = c.getItem(slot).getCount();
            assertTrue(count == 10 || count == 11, "64 across 6 slots lands on 10 or 11, got " + count);
        }
        assertEquals(64, countOf(c, "productivefrogs:iron"), "every slot holds the variant it started as");
    }

    /** Componentless items are the ordinary case and must behave exactly as before. */
    @Test
    void balancesPlainItems() {
        SimpleContainer c = furnace();
        c.setItem(FACTORY_INPUT[0], new ItemStack(Items.IRON_ORE, 12));

        split(c, false);

        assertEquals(12, totalCount(c));
        for (int slot : FACTORY_INPUT) {
            assertEquals(2, c.getItem(slot).getCount(), "12 across 6 slots is an even 2 each");
        }
    }

    /** Two variants, both in quantity: each is balanced within itself, never across. */
    @Test
    void balancesEachVariantWithinItself() {
        SimpleContainer c = furnace();
        c.setItem(FACTORY_INPUT[0], froglight("productivefrogs:gold", 30));
        c.setItem(FACTORY_INPUT[1], froglight("productivefrogs:gold", 2));
        c.setItem(FACTORY_INPUT[2], froglight("productivefrogs:redstone", 8));

        split(c, true);

        assertEquals(32, countOf(c, "productivefrogs:gold"));
        assertEquals(8, countOf(c, "productivefrogs:redstone"));
        assertEquals(40, totalCount(c));
    }

    /** The cheap per-tick pass does nothing when every slot is occupied. */
    @Test
    void perTickPassSkipsAFullFurnace() {
        SimpleContainer c = furnace();
        for (int i = 0; i < FACTORY_INPUT.length; i++) {
            c.setItem(FACTORY_INPUT[i], froglight("productivefrogs:coal", i + 1));
        }

        split(c, false);

        assertEquals(1, c.getItem(FACTORY_INPUT[0]).getCount(),
            "untouched when nothing is empty and fullCheck is off");
        assertEquals(21, totalCount(c));
    }

    /** The post-smelt pass does rebalance a full furnace. */
    @Test
    void fullCheckPassRebalancesAFullFurnace() {
        SimpleContainer c = furnace();
        for (int i = 0; i < FACTORY_INPUT.length; i++) {
            c.setItem(FACTORY_INPUT[i], froglight("productivefrogs:coal", i + 1));
        }

        split(c, true);

        assertEquals(21, totalCount(c), "no items created or destroyed");
        for (int slot : FACTORY_INPUT) {
            int count = c.getItem(slot).getCount();
            assertTrue(count == 3 || count == 4, "21 across 6 slots lands on 3 or 4, got " + count);
        }
    }

    /** An empty furnace, and a tier that uses fewer than six slots, are no-ops rather than crashes. */
    @Test
    void handlesEmptyAndPartialTiers() {
        SimpleContainer c = furnace();
        split(c, true);
        assertEquals(0, totalCount(c));

        // Tier 0 uses inputSlots[2] through [3] only.
        c.setItem(FACTORY_INPUT[0], froglight("productivefrogs:copper", 9));
        c.setItem(FACTORY_INPUT[2], froglight("productivefrogs:copper", 3));
        FactoryAutoSplit.split(c, FACTORY_INPUT, true, 2, 4);

        assertEquals(9, c.getItem(FACTORY_INPUT[0]).getCount(), "a slot outside the tier range is left alone");
        assertEquals(12, totalCount(c));
    }

    /** Out-of-range arguments are refused rather than thrown. */
    @Test
    void refusesNonsenseBounds() {
        SimpleContainer c = furnace();
        c.setItem(FACTORY_INPUT[0], froglight("productivefrogs:lapis", 4));

        FactoryAutoSplit.split(c, FACTORY_INPUT, true, -1, 6);
        FactoryAutoSplit.split(c, FACTORY_INPUT, true, 0, 99);
        FactoryAutoSplit.split(c, FACTORY_INPUT, true, 4, 4);
        FactoryAutoSplit.split(null, FACTORY_INPUT, true, 0, 6);

        assertEquals(4, totalCount(c), "nothing ran, nothing changed");
    }
}
