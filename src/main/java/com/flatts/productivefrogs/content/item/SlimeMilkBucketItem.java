package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.util.VariantNames;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
        Integer capacity = stack.get(PFDataComponents.MILK_CAPACITY.get());
        Integer speed = stack.get(PFDataComponents.MILK_SPEED.get());
        Integer quantity = stack.get(PFDataComponents.MILK_QUANTITY.get());
        Boolean infinite = stack.get(PFDataComponents.MILK_INFINITE.get());
        // Restore if the bucket carries ANY upgrade component, reading each
        // independently - they're independent on the bucket, so don't gate the
        // whole restore behind the count component (a /give or future fill path
        // could stamp infinite without a count). For the count/capacity args,
        // fall back to the source's seeded default when absent, so a
        // speed/quantity-only bucket doesn't reset the freshly-seeded budget.
        if (remaining != null || capacity != null || speed != null || quantity != null
                || Boolean.TRUE.equals(infinite)) {
            milkBe.restoreUpgrades(
                remaining != null ? remaining : milkBe.getSpawnsRemaining(),
                capacity != null ? capacity : milkBe.getSpawnsCapacity(),
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

    /**
     * Surface the source's state on the bucket so a player can read it without
     * placing it: spawns left / capacity (or "unlimited" for an Endless source or
     * when depletion is config-off), plus any installed Speed / Quantity upgrades.
     * The values come straight off the bucket's data components - the same set
     * {@link #checkExtraContent} writes onto the placed source - with the
     * configured default used when a freshly-milked bucket carries none. Reuses
     * the Jade format keys so the bucket and the look-at readout read identically.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (stack.get(PFDataComponents.SLIME_VARIANT.get()) == null) {
            return;
        }
        boolean infinite = Boolean.TRUE.equals(stack.get(PFDataComponents.MILK_INFINITE.get()));
        boolean depletionOff = PFConfig.SPEC.isLoaded() && !PFConfig.DEPLETION_ENABLED.get();
        if (infinite || depletionOff) {
            tooltip.add(Component.translatable("productivefrogs.jade.spawns_unlimited")
                .withStyle(ChatFormatting.GRAY));
        } else {
            Integer remaining = stack.get(PFDataComponents.SPAWNS_REMAINING.get());
            Integer capacity = stack.get(PFDataComponents.MILK_CAPACITY.get());
            int rem = remaining != null ? remaining : defaultSpawnCount();
            int cap = Math.max(capacity != null ? capacity : defaultSpawnCount(), rem);
            tooltip.add(Component.translatable("productivefrogs.jade.spawns_left", rem, cap)
                .withStyle(ChatFormatting.GRAY));
        }
        Integer speed = stack.get(PFDataComponents.MILK_SPEED.get());
        if (speed != null && speed > 0) {
            tooltip.add(Component.translatable("productivefrogs.jade.catalyst_speed",
                speed, PFConfig.catalystMaxSpeedLevel()).withStyle(ChatFormatting.GRAY));
        }
        Integer quantity = stack.get(PFDataComponents.MILK_QUANTITY.get());
        if (quantity != null && quantity > 0) {
            tooltip.add(Component.translatable("productivefrogs.jade.catalyst_quantity",
                quantity, PFConfig.catalystMaxQuantityLevel()).withStyle(ChatFormatting.GRAY));
        }
    }

    private static int defaultSpawnCount() {
        return PFConfig.SPEC.isLoaded() ? PFConfig.DEPLETION_COUNT.get() : 16;
    }
}
