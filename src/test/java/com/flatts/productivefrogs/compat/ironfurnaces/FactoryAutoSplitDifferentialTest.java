package com.flatts.productivefrogs.compat.ironfurnaces;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/**
 * Differential and invariant coverage for {@link FactoryAutoSplit} against
 * {@link UpstreamAutoSplitReference}, the frozen transcription of Iron
 * Furnaces 4.3.2.
 *
 * <p>{@link FactoryAutoSplitTest} pins the behaviour we want. This pins the
 * behaviour we must not change: the patch replaces a whole method in someone
 * else's mod, so "it fixes Froglights" is only half the claim. The other half
 * is that a furnace full of iron ore does exactly what it did before, and the
 * only way to know that is to run both algorithms over the same states and
 * compare.
 */
class FactoryAutoSplitDifferentialTest {

    private static final int[] FACTORY_INPUT = {7, 8, 9, 10, 11, 12};
    private static final int SLOTS = 13;

    /** Deterministic: a fixed seed keeps a failure reproducible from the report alone. */
    private static final long SEED = 0x5F30_6C17L;

    private static final String[] VARIANTS = {
        "productivefrogs:iron",
        "productivefrogs:diamond",
        "productivefrogs:nether_star",
        "productivefrogs:coal",
    };

    private static final Item[] PLAIN_ITEMS = {Items.IRON_ORE, Items.GOLD_ORE, Items.COPPER_ORE};

    // --- fixtures -------------------------------------------------------

    private static ItemStack froglight(String variant, int count) {
        ItemStack stack = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get(), count);
        stack.set(PFDataComponents.SLIME_VARIANT.get(), ResourceLocation.parse(variant));
        return stack;
    }

    /** Slot contents as a comparable string, so a mismatch reports what actually differed. */
    private static String snapshot(SimpleContainer c) {
        StringBuilder sb = new StringBuilder();
        for (int slot : FACTORY_INPUT) {
            ItemStack stack = c.getItem(slot);
            sb.append(slot).append('=');
            if (stack.isEmpty()) {
                sb.append("empty");
            } else {
                ResourceLocation variant = stack.get(PFDataComponents.SLIME_VARIANT.get());
                sb.append(stack.getCount())
                    .append('x')
                    .append(stack.getItem())
                    .append(variant == null ? "" : "[" + variant + "]");
            }
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    /** Total count per distinct (item, components) key. The thing that must never change. */
    private static Map<String, Integer> stockByKind(SimpleContainer c) {
        Map<String, Integer> stock = new HashMap<>();
        for (int slot : FACTORY_INPUT) {
            ItemStack stack = c.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation variant = stack.get(PFDataComponents.SLIME_VARIANT.get());
            String key = stack.getItem() + "|" + variant;
            stock.merge(key, stack.getCount(), Integer::sum);
        }
        return stock;
    }

    private static SimpleContainer copyOf(SimpleContainer source) {
        SimpleContainer copy = new SimpleContainer(SLOTS);
        for (int slot : FACTORY_INPUT) {
            copy.setItem(slot, source.getItem(slot).copy());
        }
        return copy;
    }

    /**
     * Random furnace state. {@code variantCount} of 1 means every stack is the
     * same kind, which is the case the two algorithms must agree on.
     */
    private static SimpleContainer randomFurnace(Random rng, int variantCount, boolean useFroglights) {
        SimpleContainer c = new SimpleContainer(SLOTS);
        for (int slot : FACTORY_INPUT) {
            if (rng.nextInt(4) == 0) {
                continue;
            }
            int count = 1 + rng.nextInt(64);
            if (useFroglights) {
                c.setItem(slot, froglight(VARIANTS[rng.nextInt(variantCount)], count));
            } else {
                c.setItem(slot, new ItemStack(PLAIN_ITEMS[rng.nextInt(variantCount)], count));
            }
        }
        return c;
    }

    private static List<int[]> tierRanges() {
        // start/size as BlockIronFurnaceTileBase computes them per tier.
        List<int[]> ranges = new ArrayList<>();
        ranges.add(new int[] {2, 4});
        ranges.add(new int[] {1, 5});
        ranges.add(new int[] {0, 6});
        return ranges;
    }

    // --- the reference reproduces the defect -----------------------------

    /**
     * Guards the transcription. If this ever stops failing the way the bug
     * report describes, the reference has drifted and every differential
     * assertion below is worthless.
     */
    @Test
    void referenceStillReproducesTheReportedBug() {
        SimpleContainer c = new SimpleContainer(SLOTS);
        c.setItem(FACTORY_INPUT[0], froglight("productivefrogs:diamond", 64));
        c.setItem(FACTORY_INPUT[1], froglight("productivefrogs:nether_star", 1));

        UpstreamAutoSplitReference.split(c, FACTORY_INPUT, true, 0, 6);

        Map<String, Integer> stock = stockByKind(c);
        int netherStar = stock.getOrDefault(
            PFItems.CONFIGURABLE_FROGLIGHT.get() + "|productivefrogs:nether_star", 0);
        assertTrue(netherStar > 1,
            "upstream must still turn 1 Nether Star Froglight into many; got " + netherStar);
        assertEquals(65, stock.values().stream().mapToInt(Integer::intValue).sum(),
            "the defect conserves the total count, which is why nobody notices it");
    }

    /** The same state through the patch, side by side, so the diff is the test. */
    @Test
    void patchAndReferenceDisagreeOnlyWhereTheBugIs() {
        SimpleContainer patched = new SimpleContainer(SLOTS);
        patched.setItem(FACTORY_INPUT[0], froglight("productivefrogs:diamond", 64));
        patched.setItem(FACTORY_INPUT[1], froglight("productivefrogs:nether_star", 1));
        SimpleContainer reference = copyOf(patched);

        FactoryAutoSplit.split(patched, FACTORY_INPUT, true, 0, 6);
        UpstreamAutoSplitReference.split(reference, FACTORY_INPUT, true, 0, 6);

        assertNotEquals(snapshot(reference), snapshot(patched),
            "the whole point of the patch is that these differ here");
        assertNotEquals(stockByKind(reference), stockByKind(patched),
            "and that the difference is which variants you own");
    }

    // --- and agree everywhere else ---------------------------------------

    /**
     * The claim that protects every player who is not smelting Froglights:
     * for componentless items the patch is behaviourally identical to Iron
     * Furnaces, across every tier's slot range and 400 random furnace states.
     */
    @Test
    void identicalToUpstreamForPlainItems() {
        Random rng = new Random(SEED);
        for (int[] range : tierRanges()) {
            for (int iteration = 0; iteration < 400; iteration++) {
                SimpleContainer patched = randomFurnace(rng, PLAIN_ITEMS.length, false);
                SimpleContainer reference = copyOf(patched);
                boolean fullCheck = rng.nextBoolean();
                String before = snapshot(patched);

                FactoryAutoSplit.split(patched, FACTORY_INPUT, fullCheck, range[0], range[1]);
                UpstreamAutoSplitReference.split(reference, FACTORY_INPUT, fullCheck, range[0], range[1]);

                assertEquals(snapshot(reference), snapshot(patched),
                    "diverged on tier range [" + range[0] + "," + range[1] + ") fullCheck=" + fullCheck
                        + " from " + before);
            }
        }
    }

    /**
     * And identical for component items too, as long as only one variant is in
     * the furnace. That is the ordinary Sky Frogs case: one frog species per
     * furnace. Auto-split must keep working exactly as players expect there.
     */
    @Test
    void identicalToUpstreamForASingleVariant() {
        Random rng = new Random(SEED + 1);
        for (int[] range : tierRanges()) {
            for (int iteration = 0; iteration < 400; iteration++) {
                SimpleContainer patched = randomFurnace(rng, 1, true);
                SimpleContainer reference = copyOf(patched);
                boolean fullCheck = rng.nextBoolean();
                String before = snapshot(patched);

                FactoryAutoSplit.split(patched, FACTORY_INPUT, fullCheck, range[0], range[1]);
                UpstreamAutoSplitReference.split(reference, FACTORY_INPUT, fullCheck, range[0], range[1]);

                assertEquals(snapshot(reference), snapshot(patched),
                    "diverged on a single-variant furnace, range [" + range[0] + "," + range[1]
                        + ") fullCheck=" + fullCheck + " from " + before);
            }
        }
    }

    // --- invariants the patch must hold on its own ------------------------

    /**
     * Over mixed-variant states, where there is no upstream behaviour worth
     * matching, the patch still has to obey the rules that make it safe:
     * nothing created, nothing destroyed, and nothing transmuted.
     */
    @Test
    void neverCreatesDestroysOrTransmutesAnything() {
        Random rng = new Random(SEED + 2);
        for (int[] range : tierRanges()) {
            for (int iteration = 0; iteration < 500; iteration++) {
                SimpleContainer c = randomFurnace(rng, VARIANTS.length, true);
                Map<String, Integer> before = stockByKind(c);
                String state = snapshot(c);
                boolean fullCheck = rng.nextBoolean();

                FactoryAutoSplit.split(c, FACTORY_INPUT, fullCheck, range[0], range[1]);

                assertEquals(before, stockByKind(c),
                    "every variant's total must survive untouched; was " + state);
            }
        }
    }

    /** No slot may come out over its stack limit, which would be a different corruption. */
    @Test
    void neverExceedsTheStackLimit() {
        Random rng = new Random(SEED + 3);
        for (int iteration = 0; iteration < 500; iteration++) {
            SimpleContainer c = randomFurnace(rng, VARIANTS.length, true);
            FactoryAutoSplit.split(c, FACTORY_INPUT, true, 0, 6);
            for (int slot : FACTORY_INPUT) {
                ItemStack stack = c.getItem(slot);
                assertTrue(stack.getCount() <= stack.getMaxStackSize(),
                    "slot " + slot + " holds " + stack.getCount() + " of max " + stack.getMaxStackSize());
            }
        }
    }

    /**
     * A pass balances one group: the one the template slot belongs to, where
     * the template is the last occupied slot <em>before</em> empty slots are
     * seeded. So a furnace holding several kinds can come out of a single pass
     * with one group still uneven, and settles over the next tick or two.
     *
     * <p>That is upstream's ordering, reproduced deliberately, and it is why
     * {@link #identicalToUpstreamForPlainItems()} holds. What matters is that
     * repeated passes converge and then hold still rather than oscillating
     * forever, since the block entity calls this every tick while any input
     * slot is empty and again after every smelt.
     */
    @Test
    void convergesToAFixedPointAndThenHoldsStill() {
        Random rng = new Random(SEED + 4);
        for (int iteration = 0; iteration < 300; iteration++) {
            SimpleContainer c = randomFurnace(rng, VARIANTS.length, true);
            Map<String, Integer> stock = stockByKind(c);

            String previous = null;
            int passes = 0;
            while (passes < 8) {
                String before = snapshot(c);
                FactoryAutoSplit.split(c, FACTORY_INPUT, true, 0, 6);
                passes++;
                assertEquals(stock, stockByKind(c), "conservation must hold on every pass");
                if (before.equals(snapshot(c))) {
                    previous = before;
                    break;
                }
            }

            assertNotEquals(null, previous,
                "did not reach a fixed point within 8 passes, which means it oscillates");

            FactoryAutoSplit.split(c, FACTORY_INPUT, true, 0, 6);
            assertEquals(previous, snapshot(c), "a settled furnace must stay settled");
        }
    }

    /**
     * Components other than ours must be respected too. Two damaged tools, or
     * two stacks separated by any component, are different items to the fix.
     */
    @Test
    void respectsComponentsOtherThanSlimeVariant() {
        SimpleContainer c = new SimpleContainer(SLOTS);
        ItemStack named = new ItemStack(Items.IRON_ORE, 32);
        named.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Sample"));
        c.setItem(FACTORY_INPUT[0], named);
        c.setItem(FACTORY_INPUT[1], new ItemStack(Items.IRON_ORE, 2));

        FactoryAutoSplit.split(c, FACTORY_INPUT, true, 0, 6);

        Map<String, Integer> stock = new HashMap<>();
        for (int slot : FACTORY_INPUT) {
            ItemStack stack = c.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            stock.merge(stack.has(DataComponents.CUSTOM_NAME) ? "named" : "plain", stack.getCount(), Integer::sum);
        }
        assertEquals(32, stock.get("named"), "the named stack must not absorb or feed the plain one");
        assertEquals(2, stock.get("plain"));
    }
}
