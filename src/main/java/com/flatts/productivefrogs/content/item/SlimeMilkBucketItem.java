package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.util.VariantNames;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
        if (!(level.getBlockEntity(pos) instanceof SlimeMilkSourceBlockEntity milkBe)) {
            return;
        }
        // setVariantId seeds the default spawn budget onto a fresh source. A
        // bucket filled by re-bucketing a buffed source also carries the
        // upgrade components; restore them over the seeded defaults so a
        // partially-depleted or catalyst-buffed source is faithfully replaced.
        // A freshly-milked bucket (from the Slime Milker) carries none of these
        // and keeps the seeded full default. See docs/slime_milk_catalysts.md.
        milkBe.setVariantId(variantId);
        Integer remaining = stack.get(PFDataComponents.SPAWNS_REMAINING.get());
        Integer speed = stack.get(PFDataComponents.MILK_SPEED.get());
        Integer quantity = stack.get(PFDataComponents.MILK_QUANTITY.get());
        Boolean infinite = stack.get(PFDataComponents.MILK_INFINITE.get());
        // Restore if the bucket carries ANY upgrade component, reading each
        // independently - they're independent on the bucket, so don't gate the
        // whole restore behind the count component (a /give or future fill path
        // could stamp infinite without a count). For the count arg, fall back to
        // the source's seeded default when the component is absent, so a
        // speed/quantity-only bucket doesn't reset the freshly-seeded budget.
        if (remaining != null || speed != null || quantity != null || Boolean.TRUE.equals(infinite)) {
            milkBe.restoreUpgrades(
                remaining != null ? remaining : milkBe.getSpawnsRemaining(),
                speed != null ? speed : 0,
                quantity != null ? quantity : 0,
                Boolean.TRUE.equals(infinite));
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
