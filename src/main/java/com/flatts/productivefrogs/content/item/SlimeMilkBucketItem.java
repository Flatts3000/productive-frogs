package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.util.VariantNames;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/**
 * The single Slime Milk bucket. Replaces the ~35 per-variant
 * {@code <variant>_slime_milk_bucket} items: variant identity rides in the
 * {@code SLIME_VARIANT} data component (the same one-item-N-surfaces pattern as
 * {@link ConfigurableFroglightItem} / {@link ResourceSlimeSpawnEggItem}), so a
 * datapack-added variant gets milk with no Java edit.
 *
 * <p>The bucket wraps the one generic {@code slime_milk} source fluid. On
 * placement, {@link #checkExtraContent} copies the stack's variant into the
 * placed {@link SlimeMilkSourceBlockEntity}, so that source block spawns the
 * right variant slime and tints per-variant. Re-bucketing the inverse direction
 * (source -> bucket, preserving the variant) is handled in
 * {@code SlimeMilkSourceBlock#pickupBlock}.
 */
public final class SlimeMilkBucketItem extends BucketItem {

    public SlimeMilkBucketItem(Fluid content, Properties properties) {
        super(content, properties);
    }

    /**
     * Post-placement hook (vanilla calls this after the fluid block is set, the
     * same point {@code MobBucketItem} spawns its mob). Write the bucket's
     * variant into the freshly-placed source block's BlockEntity so it knows
     * which slime to spawn and how to tint.
     */
    @Override
    public void checkExtraContent(@Nullable Player player, Level level, ItemStack stack, BlockPos pos) {
        ResourceLocation variantId = stack.get(PFDataComponents.SLIME_VARIANT.get());
        if (variantId == null) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SlimeMilkSourceBlockEntity milkBe) {
            milkBe.setVariantId(variantId);
        }
        // Restore the depletion counter if this bucket was filled by re-bucketing
        // a partially-depleted source (set in SlimeMilkSourceBlock#pickupBlock).
        // Runs after the fluid block is placed and onPlace seeded the default
        // count, so this overrides it back to the carried value. A freshly-milked
        // bucket has no such component and keeps the full default. See
        // docs/known_issues.md.
        Integer remaining = stack.get(PFDataComponents.SPAWNS_REMAINING.get());
        if (remaining != null) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof SlimeMilkSourceBlock
                && state.hasProperty(SlimeMilkSourceBlock.SPAWNS_REMAINING)) {
                int clamped = Mth.clamp(remaining, 0, SlimeMilkSourceBlock.MAX_SPAWNS_REMAINING);
                level.setBlock(pos, state.setValue(SlimeMilkSourceBlock.SPAWNS_REMAINING, clamped),
                    Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation variantId = stack.get(PFDataComponents.SLIME_VARIANT.get());
        if (variantId != null) {
            return Component.translatableWithFallback(
                getDescriptionId() + "." + variantId.getPath(),
                "Bucket of " + VariantNames.titleCase(variantId) + " Slime Milk");
        }
        return Component.translatable(getDescriptionId());
    }
}
