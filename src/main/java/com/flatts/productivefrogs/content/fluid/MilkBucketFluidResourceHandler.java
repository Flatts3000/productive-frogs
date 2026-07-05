package com.flatts.productivefrogs.content.fluid;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The 26.1 replacement for {@link MilkFluidBucketWrapper} on the item-fluid
 * capability surface. 26.1 removed {@code Capabilities.FluidHandler.ITEM} (which
 * took an {@code IFluidHandlerItem}) in favour of {@code Capabilities.Fluid.ITEM}
 * (a {@code ResourceHandler<FluidResource>} over an {@link ItemAccess}). The vanilla
 * bucket handler {@code net.neoforged.neoforge.transfer.fluid.BucketResourceHandler}
 * is {@code final}, so this mirrors its swap-between-empty-and-filled-bucket logic
 * and additionally copies a fixed set of data components off the bucket onto the
 * drained {@link FluidResource} - the catalyst / spawn-budget stats for Slime Milk,
 * the synthesized-item id for Mimic Milk - so a catalyzed bucket pumped into a pipe
 * network keeps those stats as a fluid (the Terrarium Controller reads them back
 * into a {@code MilkCharge}).
 *
 * <p>Since R-1 (single {@code slime_milk} fluid) the Slime Milk <b>variant</b> also
 * rides a data component ({@code SLIME_VARIANT}), not the fluid identity, so it is
 * one of the {@code carried} types and is copied on both legs: bucket -&gt; fluid in
 * {@link #getResourceFrom} and fluid -&gt; bucket in {@link #update} (vanilla
 * {@link FluidType#getBucket(net.neoforged.neoforge.fluids.FluidStack)} mints a bare
 * bucket, so the reverse copy is explicit). A tank round-trip therefore preserves
 * the variant and every carried stat in both directions.
 *
 * <p>Transaction correctness is inherited from {@link ItemAccessResourceHandler},
 * which journals the underlying {@link ItemAccess}; this subclass only customises
 * the read mapping and is otherwise non-mutating.
 */
public final class MilkBucketFluidResourceHandler extends ItemAccessResourceHandler<FluidResource> {

    private final DataComponentType<?>[] carried;

    public MilkBucketFluidResourceHandler(ItemAccess itemAccess, DataComponentType<?>[] carried) {
        super(itemAccess, 1);
        this.carried = carried;
    }

    @Override
    protected FluidResource getResourceFrom(ItemResource accessResource, int index) {
        if (!(accessResource.getItem() instanceof BucketItem bucketItem)) {
            return FluidResource.EMPTY;
        }
        FluidResource fluid = FluidResource.of(bucketItem.content);
        if (fluid.isEmpty()) {
            return fluid;
        }
        for (DataComponentType<?> type : carried) {
            fluid = copyComponent(fluid, accessResource, type);
        }
        return fluid;
    }

    private static <D> FluidResource copyComponent(FluidResource fluid, ItemResource from, DataComponentType<D> type) {
        D value = from.getComponents().get(type);
        return value == null ? fluid : fluid.with(type, value);
    }

    private static <D> ItemResource copyComponentToItem(ItemResource bucket, FluidResource from, DataComponentType<D> type) {
        D value = from.getComponents().get(type);
        return value == null ? bucket : bucket.with(type, value);
    }

    @Override
    protected int getAmountFrom(ItemResource accessResource, int index) {
        return getResourceFrom(accessResource, index).isEmpty() ? 0 : FluidType.BUCKET_VOLUME;
    }

    @Override
    protected ItemResource update(ItemResource accessResource, int index, FluidResource newResource, int newAmount) {
        if (newAmount == 0) {
            return ItemResource.of(Items.BUCKET);
        } else if (newAmount != FluidType.BUCKET_VOLUME) {
            return ItemResource.EMPTY;
        }
        var newStack = newResource.toStack(newAmount);
        ItemResource bucket = ItemResource.of(newStack.getFluidType().getBucket(newStack));
        // FluidType.getBucket(FluidStack) mints a bare bucket (no component copy), so
        // re-stamp the carried components (variant / synthesized-item + catalyst stats)
        // from the FluidResource back onto it - the reverse of getResourceFrom - so a
        // tank -> bucket drain keeps them.
        for (DataComponentType<?> type : carried) {
            bucket = copyComponentToItem(bucket, newResource, type);
        }
        return bucket;
    }

    @Override
    protected int getCapacity(int index, FluidResource resource) {
        return FluidType.BUCKET_VOLUME;
    }
}
