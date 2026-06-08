package com.flatts.productivefrogs.content.fluid;

import com.flatts.productivefrogs.registry.PFDataComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;

/**
 * A {@link FluidBucketWrapper} that copies a Slime Milk bucket's catalyst
 * components onto the drained {@link FluidStack}, so a catalyzed milk bucket
 * pumped into a pipe network keeps its Count / Speed / Quantity / Infinite stats
 * as a fluid - which the Terrarium Controller's fluid intake then reads back into
 * a {@code MilkCharge}. Registered in place of the plain wrapper for every
 * per-variant milk bucket (see {@code PFModBusEvents}).
 *
 * <p>The variant itself always survives (it rides the per-variant fluid identity);
 * this wrapper adds the catalyst fidelity on the item-&gt;fluid leg. The bucket
 * slot and hand-feed paths are exact regardless; a foreign handler that refills a
 * bucket from a plain {@code FluidStack} may still drop the catalyst components
 * (the spec's acknowledged caveat).
 */
public class MilkFluidBucketWrapper extends FluidBucketWrapper {

    public MilkFluidBucketWrapper(ItemStack container) {
        super(container);
    }

    @Override
    public FluidStack getFluid() {
        FluidStack fluid = super.getFluid();
        if (!fluid.isEmpty()) {
            copy(PFDataComponents.SPAWNS_REMAINING.get(), fluid);
            copy(PFDataComponents.MILK_CAPACITY.get(), fluid);
            copy(PFDataComponents.MILK_SPEED.get(), fluid);
            copy(PFDataComponents.MILK_QUANTITY.get(), fluid);
            copy(PFDataComponents.MILK_INFINITE.get(), fluid);
        }
        return fluid;
    }

    private <T> void copy(DataComponentType<T> type, FluidStack fluid) {
        T value = this.container.get(type);
        if (value != null) {
            fluid.set(type, value);
        }
    }
}
