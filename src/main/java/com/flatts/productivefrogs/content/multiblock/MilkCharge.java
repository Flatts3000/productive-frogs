package com.flatts.productivefrogs.content.multiblock;

import com.flatts.productivefrogs.content.block.MilkSpawnEconomy;
import com.flatts.productivefrogs.registry.PFDataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * One bucket-equivalent of Slime Milk inside the Controller's funnel buffer,
 * carrying its full catalyst stat set. The Controller holds a FIFO of these (one
 * variant at a time); distribution pops a whole charge and stamps it onto a
 * Sprinkler, so a Sprinkler spawns identically to a hand-placed catalyzed source.
 *
 * <p>This is the fidelity fix from the spec: catalysts live as data components on
 * the bucket / fluid (not blended into a tank), so a charge preserves
 * Count / Speed / Quantity / Infinite exactly. A charge is built from a bucket
 * {@link ItemStack} ({@link #fromBucket}) or a {@link FluidStack} ({@link #fromFluid});
 * both read the same five {@link PFDataComponents} the placed source round-trips.
 */
public record MilkCharge(int spawnsRemaining, int capacity, int speed, int quantity, boolean infinite) {

    /** Build a charge from a milk bucket item's catalyst components (always exact). */
    public static MilkCharge fromBucket(ItemStack bucket) {
        return read(
            bucket.get(PFDataComponents.SPAWNS_REMAINING.get()),
            bucket.get(PFDataComponents.MILK_CAPACITY.get()),
            bucket.get(PFDataComponents.MILK_SPEED.get()),
            bucket.get(PFDataComponents.MILK_QUANTITY.get()),
            bucket.get(PFDataComponents.MILK_INFINITE.get()));
    }

    /** Build a charge from a fluid stack's catalyst components (the piped-milk path). */
    public static MilkCharge fromFluid(FluidStack fluid) {
        return read(
            fluid.get(PFDataComponents.SPAWNS_REMAINING.get()),
            fluid.get(PFDataComponents.MILK_CAPACITY.get()),
            fluid.get(PFDataComponents.MILK_SPEED.get()),
            fluid.get(PFDataComponents.MILK_QUANTITY.get()),
            fluid.get(PFDataComponents.MILK_INFINITE.get()));
    }

    private static MilkCharge read(Integer remaining, Integer capacity, Integer speed, Integer quantity, Boolean infinite) {
        int rem = remaining != null ? remaining : MilkSpawnEconomy.defaultSpawnCount();
        int cap = capacity != null ? Math.max(capacity, rem) : rem;
        return new MilkCharge(rem, cap, speed != null ? speed : 0, quantity != null ? quantity : 0,
            Boolean.TRUE.equals(infinite));
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Remaining", spawnsRemaining);
        tag.putInt("Capacity", capacity);
        tag.putInt("Speed", speed);
        tag.putInt("Quantity", quantity);
        tag.putBoolean("Infinite", infinite);
        return tag;
    }

    public static MilkCharge fromTag(CompoundTag tag) {
        return new MilkCharge(tag.getInt("Remaining"), tag.getInt("Capacity"),
            tag.getInt("Speed"), tag.getInt("Quantity"), tag.getBoolean("Infinite"));
    }
}
