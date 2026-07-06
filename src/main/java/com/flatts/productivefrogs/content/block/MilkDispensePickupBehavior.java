package com.flatts.productivefrogs.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Replacement for vanilla's empty-bucket dispense behavior, fixing dispenser
 * pickups of PF milk sources (#326).
 *
 * <p>Vanilla's {@code Items.BUCKET} behavior calls {@link BucketPickup#pickupBlock}
 * and then keeps only the ITEM of the returned stack
 * ({@code new ItemStack(pickup.getItem())}), discarding every data component.
 * The player pickup path uses the returned stack whole. On this line that is
 * WORSE than an upgrade reset: milk is the single R-1 fluid whose identity
 * rides the {@code SLIME_VARIANT} component, so a dispenser scooping a
 * {@link SlimeMilkSourceBlock} got back a generic, variant-less milk bucket
 * (and a {@link MimicMilkSourceBlock} scoop lost its {@code SYNTHESIZED_ITEM}).
 *
 * <p>This behavior replicates vanilla's logic exactly, except when the scooped
 * block is one of PF's milk source blocks it passes the FULL pickup stack
 * through, keeping the stamped components. Every other block (vanilla fluids,
 * other mods) keeps byte-identical vanilla behavior - vanilla pickups return
 * component-less buckets anyway, so nothing observable changes for them.
 *
 * <p>Registered on common setup via {@code enqueueWork} (the dispenser registry
 * isn't thread-safe) - the same posture as
 * {@link com.flatts.productivefrogs.content.item.SlimeBucketItem#registerDispenseBehavior}.
 * Registration order vs vanilla's bootstrap is safe: common setup runs after
 * vanilla registers, so this cleanly replaces the {@code Items.BUCKET} entry.
 */
public final class MilkDispensePickupBehavior {

    private MilkDispensePickupBehavior() {
        // static registration only
    }

    public static void register() {
        DispenserBlock.registerBehavior(Items.BUCKET, new DefaultDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource source, ItemStack dispensed) {
                LevelAccessor level = source.level();
                BlockPos target = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
                BlockState blockState = level.getBlockState(target);
                if (blockState.getBlock() instanceof BucketPickup bucket) {
                    ItemStack pickup = bucket.pickupBlock(null, level, target, blockState);
                    if (pickup.isEmpty()) {
                        return super.execute(source, dispensed);
                    }
                    level.gameEvent(null, GameEvent.FLUID_PICKUP, target);
                    // The one-line divergence from vanilla: PF sources keep the
                    // stack pickupBlock stamped; everything else gets vanilla's
                    // bare re-mint.
                    boolean pfSource = blockState.getBlock() instanceof SlimeMilkSourceBlock
                        || blockState.getBlock() instanceof MimicMilkSourceBlock;
                    Item targetType = pickup.getItem();
                    return this.consumeWithRemainder(source, dispensed,
                        pfSource ? pickup : new ItemStack(targetType));
                }
                return super.execute(source, dispensed);
            }
        });
    }
}
