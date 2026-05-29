package com.flatts.productivefrogs.content.item;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The four Slime Milk catalysts, the early-game stopgap toward lower-friction
 * production (docs/slime_milk_catalysts.md). Each is a plain crafted item the
 * player drops into a placed Slime Milk source to buff it; the actual effect is
 * applied by {@code SlimeMilkSourceBlockEntity#applyCatalyst}, keyed off this
 * enum.
 *
 * <p>This enum is the seam between the four registered items
 * ({@link PFItems#COUNT_CATALYST} etc.) and the apply logic, so neither the
 * source block nor its BlockEntity has to hard-code item identity. The items
 * stay dumb {@link Item}s; the behaviour lives in the source block.
 *
 * <ul>
 *   <li>{@link #COUNT} - adds {@code slime_milk_catalysts.countPerCatalyst} to the
 *       source's remaining spawns (uncapped).</li>
 *   <li>{@link #SPEED} - +1 speed level (faster spawn cadence), capped at
 *       {@code maxSpeedLevel}.</li>
 *   <li>{@link #QUANTITY} - +1 quantity level (more slimes per spawn), capped at
 *       {@code maxQuantityLevel}.</li>
 *   <li>{@link #INFINITE} - sets the source to never deplete (built from Count
 *       catalysts; the final tier of the count line).</li>
 * </ul>
 */
public enum MilkCatalyst {
    COUNT,
    SPEED,
    QUANTITY,
    INFINITE;

    /**
     * Resolve the catalyst a held/dropped stack represents, or {@code null} if the
     * stack is not a catalyst. Reads the type straight off the
     * {@link MilkCatalystItem} (single source of truth), so there's no item-id
     * if-chain to keep in sync and the hot {@code entityInside} path does one
     * {@code instanceof} + field read.
     */
    @Nullable
    public static MilkCatalyst fromStack(ItemStack stack) {
        return stack.getItem() instanceof MilkCatalystItem item ? item.getCatalyst() : null;
    }
}
