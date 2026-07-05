package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock;
import com.flatts.productivefrogs.content.item.SlimeMilkBucketItem;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/**
 * The Slime Milk Basin's engine (#281, predation Phase 3): the slime-side
 * sibling of the Mob Slurry Basin, charged with any variant's Slime Milk
 * bucket and spawning that variant's Resource Slimes on the same economy.
 * <b>Additive</b> - the placeable Slime Milk source block stays; the Basin is
 * the automation-friendly form (persists when depleted, waterloggable,
 * pipe-fillable in place).
 *
 * <p>Spawning goes through {@link SlimeMilkSourceBlock#createSlimeForVariant}
 * - the same single seam the source and the Terrarium use (sentinels, custom
 * {@code spawn_entity}, category resolution), never re-forked. (The 1.x
 * boss-milk refusal retired with the catalyst altars in Phase 5.)
 */
public class SlimeMilkBasinBlockEntity extends AbstractBasinBlockEntity {

    public SlimeMilkBasinBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.SLIME_MILK_BASIN.get(), pos, state);
    }

    @Override
    @Nullable
    public Identifier keyFromBucket(ItemStack stack) {
        return stack.getItem() instanceof SlimeMilkBucketItem ? SlimeMilkBucketItem.variantOf(stack) : null;
    }

    @Override
    public boolean acceptsKey(ServerLevel level, Identifier key) {
        // Anything works - including the vanilla/magma sentinels, which
        // createSlimeForVariant handles. (The boss-milk refusal retired with
        // the catalyst altars in Phase 5.)
        return true;
    }

    @Override
    protected ItemStack mintBucket(Identifier key) {
        return SlimeMilkBucketItem.forVariant(key);
    }

    @Override
    public Fluid pipeFluid() {
        return PFFluids.SLIME_MILK.get();
    }

    @Override
    public DataComponentType<Identifier> pipeKeyComponent() {
        return PFDataComponents.SLIME_VARIANT.get();
    }

    @Override
    @Nullable
    protected Entity createSpawnEntity(ServerLevel level, Identifier key) {
        Slime slime = SlimeMilkSourceBlock.createSlimeForVariant(level, key);
        if (slime != null) {
            slime.setSize(1, true);
        }
        return slime;
    }

    @Override
    protected boolean isCrowded(ServerLevel level, BlockPos pos, Identifier key) {
        return SlimeMilkSourceBlock.isAreaCrowded(level, pos, key);
    }

    @Override
    protected boolean systemEnabled() {
        // The slime side is core machinery, not predation content: the Basin
        // block itself is always live once placed (its RECIPE is what gates
        // availability). Mirrors the Spawnery's "placed block still works".
        return true;
    }
}
