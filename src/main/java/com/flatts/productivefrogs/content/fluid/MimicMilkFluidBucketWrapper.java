package com.flatts.productivefrogs.content.fluid;

import com.flatts.productivefrogs.registry.PFDataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Fluid-item wrapper for the Mimic Milk bucket (#253). Mimic Milk is a single fluid
 * (the synthesized item rides the bucket, not the fluid identity - see
 * {@link MimicMilkFluid}), so a plain {@code FluidBucketWrapper} would let a tank
 * drain the bucket but lose the item id. This wrapper copies the synthesized item id
 * (on top of the catalyst components {@link MilkFluidBucketWrapper} already carries)
 * onto the drained {@link FluidStack}, so a bucket round-tripped through a single tank
 * keeps its item. Registered for the Mimic Milk bucket in {@code PFModBusEvents}.
 *
 * <p>The documented caveat from {@link MimicMilkFluid} still stands for pipe networks
 * that place the fluid block directly (bypassing the bucket): PF's own machines read
 * the item off the source BE, which only the bucket-placement path seeds.
 */
public class MimicMilkFluidBucketWrapper extends MilkFluidBucketWrapper {

    public MimicMilkFluidBucketWrapper(ItemStack container) {
        super(container);
    }

    @Override
    public FluidStack getFluid() {
        FluidStack fluid = super.getFluid();
        if (!fluid.isEmpty()) {
            var id = this.container.get(PFDataComponents.SYNTHESIZED_ITEM.get());
            if (id != null) {
                fluid.set(PFDataComponents.SYNTHESIZED_ITEM.get(), id);
            }
        }
        return fluid;
    }
}
