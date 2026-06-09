package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.PFConfig;
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

    /**
     * Whether this specific catalyst is enabled in config (#201). Each per-catalyst
     * flag is ANDed with the catalysts master toggle. Reads live config (delegates
     * to {@code PFConfig.catalyst*Enabled()}), so it is not a pure value query and
     * fails open before the config spec loads.
     *
     * <p>The two consume sites ({@code SlimeMilkSourceBlock#entityInside},
     * {@code SprinklerBlockEntity#absorbCatalystsFromAbove}) call this to leave a
     * disabled catalyst floating for the player rather than eating it. This is the
     * <i>enabled</i> gate only; the separate "is this upgrade already maxed / not
     * applicable" gate is {@code applyCatalyst}'s return value. Both gates live at
     * the consume site, not inside {@code applyCatalyst}, so upgrades already on an
     * existing source stay honoured.
     */
    public boolean isEnabled() {
        return switch (this) {
            case COUNT -> PFConfig.catalystCountEnabled();
            case SPEED -> PFConfig.catalystSpeedEnabled();
            case QUANTITY -> PFConfig.catalystQuantityEnabled();
            case INFINITE -> PFConfig.catalystInfiniteEnabled();
        };
    }
}
