package com.flatts.productivefrogs.compat.ironfurnaces;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Component-aware replacement for Iron Furnaces' factory auto-split.
 *
 * <p><b>The bug this exists for.</b> Iron Furnaces balances a factory
 * furnace's input slots by pooling every slot that holds "the same item"
 * and averaging the counts across them. Its idea of "the same item" is
 * {@code stackA.getItem() != stackB.getItem()} - an item-id comparison that
 * never looks at data components. Every Froglight variant is one item id
 * ({@code productivefrogs:configurable_froglight}) carrying its variant in
 * the {@code productivefrogs:slime_variant} component, so the furnace pools
 * variants that are not the same thing at all, averages them, and writes the
 * new counts back into slots that each keep their own variant.
 *
 * <p>A furnace holding 64 Cave Froglights in one slot and a single Nether
 * Star Froglight in another comes out holding 32 and 33. The total count is
 * conserved, so it is not a duplication bug in the strict sense; it is a free
 * transmuter, which is worse. Reported to Sky Frogs as issues #220 and #225
 * and upstream as Qelifern/IronFurnaces#229, a regression of the same mod's
 * #147 (fixed for 1.19.2 NBT in 2023, unfixed for 1.21 data components).
 *
 * <p>Nothing here is Froglight-specific. Any single-id-plus-component item
 * from any mod is corrupted by the same path; Froglights are simply the case
 * we ship and the one players hit.
 *
 * <p><b>What changed.</b> Exactly one thing: the slot-grouping test is now
 * {@link ItemStack#isSameItemSameComponents}, which is the comparison Iron
 * Furnaces already uses correctly everywhere else in that class (its
 * auto-input, auto-output and internal insert paths all get it right). The
 * empty-slot seeding and the averaging maths are reproduced faithfully,
 * including which slots absorb the remainder, so a furnace holding one item
 * type behaves exactly as it does on unpatched Iron Furnaces.
 *
 * <p>Kept free of Iron Furnaces types on purpose: it takes a plain
 * {@link Container} and the slot-index array, so it is unit-testable without
 * the mod installed and the mixin stays a two-line delegation.
 */
public final class FactoryAutoSplit {

    private FactoryAutoSplit() {
    }

    /**
     * Rebalances the furnace's factory input slots across stacks that match in
     * both item and components.
     *
     * @param furnace    the furnace inventory
     * @param inputSlots the factory input slot indices ({@code FACTORY_INPUT})
     * @param fullCheck  true on the post-smelt pass, which rebalances even when
     *                   no slot is empty; false on the cheap per-tick pass
     * @param start      first index into {@code inputSlots} this furnace tier uses
     * @param size       one past the last index this furnace tier uses
     */
    public static void split(Container furnace, int[] inputSlots, boolean fullCheck, int start, int size) {
        if (furnace == null || inputSlots == null || start < 0 || size > inputSlots.length || start >= size) {
            return;
        }

        // The cheap per-tick pass only does work when there is an empty slot to
        // seed. Upstream behaviour, kept so we do not add per-tick cost.
        if (!fullCheck && !hasEmptySlot(furnace, inputSlots, start, size)) {
            return;
        }

        // The last occupied slot picks the group to balance, as upstream does.
        ItemStack template = ItemStack.EMPTY;
        for (int i = start; i < size; i++) {
            ItemStack stack = furnace.getItem(inputSlots[i]);
            if (!stack.isEmpty()) {
                template = stack;
            }
        }
        if (template.isEmpty()) {
            return;
        }
        // Copied before seeding, which may shrink the slot it came from. Only
        // the item and components are read from it, never the count.
        template = template.copy();

        seedEmptySlots(furnace, inputSlots, start, size);

        List<Integer> group = new ArrayList<>(size - start);
        int total = 0;
        for (int i = start; i < size; i++) {
            ItemStack stack = furnace.getItem(inputSlots[i]);
            // THE FIX. Upstream compares stack.getItem() != template.getItem(),
            // which pools every Froglight variant into one group.
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, template)) {
                continue;
            }
            group.add(inputSlots[i]);
            total += stack.getCount();
        }
        if (group.isEmpty()) {
            return;
        }

        int even = total / group.size();
        int remainder = total % group.size();

        boolean anyChange = false;
        for (int i = 0; i < group.size(); i++) {
            if (furnace.getItem(group.get(i)).getCount() != even + (i < remainder ? 1 : 0)) {
                anyChange = true;
                break;
            }
        }
        if (!anyChange) {
            return;
        }

        for (int i = 0; i < group.size(); i++) {
            int slot = group.get(i);
            furnace.setItem(slot, furnace.getItem(slot).copyWithCount(even + (i < remainder ? 1 : 0)));
        }
        furnace.setChanged();
    }

    private static boolean hasEmptySlot(Container furnace, int[] inputSlots, int start, int size) {
        for (int i = start; i < size; i++) {
            if (furnace.getItem(inputSlots[i]).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Seeds every empty input slot with one item taken from the first slot that
     * can spare one, so the balancing pass below has something to spread into.
     * Faithful to upstream: one donor only, and the donor always keeps at least
     * one item. Component-safe already on unpatched Iron Furnaces (the seed is a
     * copy), so this is reproduced rather than changed.
     */
    private static void seedEmptySlots(Container furnace, int[] inputSlots, int start, int size) {
        int empties = 0;
        for (int i = start; i < size; i++) {
            if (furnace.getItem(inputSlots[i]).isEmpty()) {
                empties++;
            }
        }
        if (empties == 0) {
            return;
        }

        for (int j = start; j < size; j++) {
            ItemStack donor = furnace.getItem(inputSlots[j]);
            if (donor.isEmpty() || donor.getCount() <= 1) {
                continue;
            }
            int move = Math.min(empties, donor.getCount() - 1);
            ItemStack seed = donor.copy();
            furnace.setItem(inputSlots[j], seed.copyWithCount(seed.getCount() - move));

            for (int i = start; i < size && move > 0; i++) {
                if (!furnace.getItem(inputSlots[i]).isEmpty()) {
                    continue;
                }
                furnace.setItem(inputSlots[i], seed.copyWithCount(1));
                move--;
            }
            furnace.setChanged();
            return;
        }
    }
}
