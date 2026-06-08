package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.PFConfig;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The Slime Milk spawn-economy math, shared by every surface that "spawns
 * from milk": the placed {@link SlimeMilkSourceBlock} and the Slime Churn
 * ({@code SlimeChurnBlockEntity}). Extracted so the cadence and batch rules
 * cannot drift between the in-world source and the appliance that mirrors
 * it - a catalyzed bucket must behave identically wherever it is spent.
 *
 * <p>Both methods read live {@link PFConfig} values, so they are only
 * callable once the config spec is loaded (server context). See
 * {@code docs/slime_milk_catalysts.md} for the catalyst design.
 */
public final class MilkSpawnEconomy {

    private MilkSpawnEconomy() {
    }

    /**
     * Random delay until the next spawn event, applying the Speed catalyst:
     * the base {@code [min, max]} interval is reduced by {@code speedLevel *
     * speedReductionPerLevel}, clamped down to {@code minIntervalFloorTicks}
     * so stacked Speed levels can't drive the cadence to zero.
     */
    public static int intervalTicks(int speedLevel, RandomSource random) {
        int min = PFConfig.MIN_SPAWN_INTERVAL_TICKS.get();
        int max = PFConfig.MAX_SPAWN_INTERVAL_TICKS.get();
        if (speedLevel > 0) {
            double factor = Math.max(0.0, 1.0 - speedLevel * PFConfig.catalystSpeedReductionPerLevel());
            int floor = PFConfig.catalystMinIntervalFloorTicks();
            min = Math.max(floor, (int) Math.round(min * factor));
            max = Math.max(floor, (int) Math.round(max * factor));
        }
        return max <= min ? min : min + random.nextInt(max - min + 1);
    }

    /**
     * Slimes produced per spawn event: {@code 1 + quantityLevel} (Quantity
     * catalyst), clamped to the config max. The spawn budget is decremented
     * once per <b>event</b>, not per slime, so Quantity is strictly additive
     * to throughput and never burns extra count.
     */
    public static int batchQuantity(int quantityLevel) {
        return 1 + Mth.clamp(quantityLevel, 0, PFConfig.catalystMaxQuantityLevel());
    }
}
