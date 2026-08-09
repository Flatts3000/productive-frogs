package com.flatts.productivefrogs.compat.ironfurnaces;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Iron Furnaces' factory auto-split, transcribed verbatim from
 * {@code BlockIronFurnaceTileBase} in {@code ironfurnaces-neoforge-1.21.1-4.3.2.jar}.
 * Test scaffolding only, never shipped.
 *
 * <p><b>This code is deliberately wrong and must stay wrong.</b> It exists so
 * the test suite can do two things it otherwise could not:
 *
 * <ol>
 *   <li>Demonstrate the defect executably, rather than asserting it from a
 *       changelog entry. If Iron Furnaces ever fixes this upstream, the test
 *       that pins the broken behaviour here still passes, because this is a
 *       frozen copy of 4.3.2 and not a live call into the mod.</li>
 *   <li>Prove the claim that matters for everyone who is not smelting
 *       Froglights: that {@link FactoryAutoSplit} is byte-for-byte
 *       behaviourally identical to this for any furnace whose contents do not
 *       differ by component. That claim is only worth anything if it is
 *       checked against the real algorithm, so it is.</li>
 * </ol>
 *
 * <p>Two faithful-transcription notes:
 *
 * <ul>
 *   <li>Upstream collects the slot group into a {@link java.util.HashMap} and
 *       iterates it, so the order in which slots absorb the division remainder
 *       is formally unspecified. In practice the keys are the small positive
 *       ints 7 to 12, which hash to themselves and land in ascending bucket
 *       order, so the observed order is ascending. {@link LinkedHashMap} with
 *       ascending insertion pins that rather than depending on it.</li>
 *   <li>Upstream mutates the live {@link ItemStack} in place via
 *       {@code shrink}; this uses {@code setItem} with a copy. Identical result
 *       for any container that stores the stack it was handed, and it keeps the
 *       reference honest for containers that do not.</li>
 * </ul>
 */
final class UpstreamAutoSplitReference {

    private UpstreamAutoSplitReference() {
    }

    static void split(Container furnace, int[] factoryInput, boolean fullCheck, int start, int size) {
        ItemStack itemToCheck = ItemStack.EMPTY;
        int fullCheckCount = 0;

        if (!fullCheck) {
            for (int i = start; i < size; i++) {
                if (furnace.getItem(factoryInput[i]).isEmpty()) {
                    fullCheckCount++;
                }
            }
            if (fullCheckCount == 0) {
                return;
            }
        }

        for (int i = start; i < size; i++) {
            if (!furnace.getItem(factoryInput[i]).isEmpty()) {
                itemToCheck = furnace.getItem(factoryInput[i]).copy();
            }
        }
        if (itemToCheck.isEmpty()) {
            return;
        }

        fillEmptySlots(furnace, factoryInput, start, size);

        Map<Integer, Integer> items = new LinkedHashMap<>();
        for (int i2 = start; i2 < size; i2++) {
            ItemStack stack = furnace.getItem(factoryInput[i2]);
            // THE DEFECT. Item id only: every Froglight variant is one id, so
            // variants that are not the same thing get pooled and averaged.
            if (stack.isEmpty() || stack.getItem() != itemToCheck.getItem()) {
                continue;
            }
            items.put(factoryInput[i2], stack.getCount());
        }
        if (items.isEmpty()) {
            return;
        }

        int[] slot = new int[items.size()];
        int[] input = new int[items.size()];
        int j = 0;
        for (Map.Entry<Integer, Integer> entry : items.entrySet()) {
            slot[j] = entry.getKey();
            input[j] = entry.getValue();
            j++;
        }

        Map<Integer, Integer> setCounts = getSplitCounts(slot, input);

        int check = 0;
        for (Map.Entry<Integer, Integer> entry : setCounts.entrySet()) {
            if (furnace.getItem(entry.getKey()).getCount() == entry.getValue()) {
                check++;
            }
        }
        if (check == setCounts.size()) {
            return;
        }

        for (Map.Entry<Integer, Integer> entry : setCounts.entrySet()) {
            ItemStack newStack = furnace.getItem(entry.getKey()).copy();
            newStack.setCount(entry.getValue());
            furnace.setItem(entry.getKey(), newStack);
            furnace.setChanged();
        }
    }

    private static Map<Integer, Integer> getSplitCounts(int[] slot, int[] input) {
        if (slot.length != input.length) {
            return null;
        }
        Map<Integer, Integer> output = new LinkedHashMap<>();

        double sum = 0.0;
        for (int value : input) {
            sum += value;
        }
        double splitted = sum / input.length;

        if (sum % input.length != 0.0) {
            if (Math.floor(splitted) < splitted) {
                double lowest = Math.floor(sum / input.length) * input.length;
                int itemsLeftOver = (int) sum - (int) lowest;
                for (int i = 0; i < input.length; i++) {
                    if (itemsLeftOver > 0) {
                        input[i] = (int) Math.ceil(splitted);
                        itemsLeftOver--;
                    } else {
                        input[i] = (int) splitted;
                    }
                }
            }
        } else {
            for (int i = 0; i < input.length; i++) {
                input[i] = (int) splitted;
            }
        }

        for (int i = 0; i < input.length; i++) {
            output.put(slot[i], input[i]);
        }
        return output;
    }

    private static void fillEmptySlots(Container furnace, int[] factoryInput, int start, int size) {
        int amount = 0;
        for (int i = start; i < size; i++) {
            if (furnace.getItem(factoryInput[i]).isEmpty()) {
                amount++;
            }
        }
        if (amount == 0) {
            return;
        }

        for (int j = start; j < size; j++) {
            ItemStack donor = furnace.getItem(factoryInput[j]);
            if (donor.isEmpty() || donor.getCount() <= 1 || amount <= 0) {
                continue;
            }
            if (amount >= donor.getCount()) {
                amount = donor.getCount() - 1;
            }
            ItemStack stack = donor.copy();
            furnace.setItem(factoryInput[j], stack.copyWithCount(stack.getCount() - amount));

            for (int i = start; i < size; i++) {
                if (!furnace.getItem(factoryInput[i]).isEmpty() || amount <= 0) {
                    continue;
                }
                furnace.setItem(factoryInput[i], stack.copyWithCount(1));
                amount--;
                furnace.setChanged();
            }
            furnace.setChanged();
            break;
        }
    }
}
