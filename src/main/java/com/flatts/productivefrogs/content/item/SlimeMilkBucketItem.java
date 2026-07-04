package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.util.VariantNames;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/**
 * The single Slime Milk bucket (26.1 R-1). One {@code slime_milk_bucket} item
 * placing the single Slime Milk source block; the variant rides as the top-level
 * {@link PFDataComponents#SLIME_VARIANT} component (runtime tint + name), and on
 * placement writes that variant onto the placed
 * {@link SlimeMilkSourceBlockEntity} via {@link #checkExtraContent}. This replaces
 * the v1.8 per-variant milk buckets ({@code PFVariantMilk}): the 26.1 transfer API
 * preserves the component through tanks/pipes, so the variant no longer needs to be
 * the fluid's identity. Mirrors {@code MimicMilkBucketItem}, keyed on SLIME_VARIANT
 * instead of SYNTHESIZED_ITEM. See {@code docs/port_mc_26_1_reimplementation.md} (R-1).
 */
public final class SlimeMilkBucketItem extends BucketItem {

    public SlimeMilkBucketItem(Fluid content, Properties properties) {
        super(content, properties);
    }

    /** Mint a Slime Milk bucket carrying {@code variantId} (the Milker / re-bucket writer). */
    public static ItemStack forVariant(Identifier variantId) {
        ItemStack stack = new ItemStack(PFItems.SLIME_MILK_BUCKET.get());
        stack.set(PFDataComponents.SLIME_VARIANT.get(), variantId);
        return stack;
    }

    /** The variant a Slime Milk bucket stack carries, or null (empty/unstamped). */
    @Nullable
    public static Identifier variantOf(ItemStack stack) {
        return stack.get(PFDataComponents.SLIME_VARIANT.get());
    }

    /**
     * Post-placement hook (vanilla calls this after the fluid block is set). Seed
     * the placed BE's variant from the bucket's {@code SLIME_VARIANT} component
     * (which also seeds the default budget), then restore any catalyst/budget
     * upgrades the bucket carried so a buffed or partially-depleted source is
     * faithfully replaced. A freshly-milked bucket carries only the variant and
     * keeps the seeded full default.
     */
    @Override
    public void checkExtraContent(@Nullable LivingEntity user, Level level, ItemStack stack, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SlimeMilkSourceBlockEntity milkBe)) {
            return;
        }
        Identifier variantId = stack.get(PFDataComponents.SLIME_VARIANT.get());
        if (variantId != null) {
            milkBe.setVariantId(variantId); // seeds the default budget on first placement
        }
        Integer remaining = stack.get(PFDataComponents.SPAWNS_REMAINING.get());
        Integer capacity = stack.get(PFDataComponents.MILK_CAPACITY.get());
        Integer speed = stack.get(PFDataComponents.MILK_SPEED.get());
        Integer quantity = stack.get(PFDataComponents.MILK_QUANTITY.get());
        Boolean infinite = stack.get(PFDataComponents.MILK_INFINITE.get());
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

    /**
     * Variant-aware display name from the {@code SLIME_VARIANT} component
     * ("Bucket of Iron Slime Milk"), falling back to the base name when unstamped.
     * Title-cases the variant path so a pack-added variant reads cleanly without
     * shipping a lang key.
     */
    @Override
    public Component getName(ItemStack stack) {
        Identifier variantId = stack.get(PFDataComponents.SLIME_VARIANT.get());
        if (variantId != null) {
            return Component.translatable("item.productivefrogs.slime_milk_bucket.item",
                VariantNames.titleCase(variantId));
        }
        return Component.translatable(getDescriptionId());
    }

    /**
     * Surface the source's state on the bucket so a player can read it without
     * placing it: spawns left / capacity (or "unlimited" for an Endless source or
     * when depletion is config-off), plus any installed Speed / Quantity upgrades.
     * Reuses the Jade format keys so the bucket and the look-at readout match.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltip, flag);
        boolean infinite = Boolean.TRUE.equals(stack.get(PFDataComponents.MILK_INFINITE.get()));
        boolean depletionOff = PFConfig.SPEC.isLoaded() && !PFConfig.DEPLETION_ENABLED.get();
        if (infinite || depletionOff) {
            tooltip.accept(Component.translatable("productivefrogs.jade.spawns_unlimited")
                .withStyle(ChatFormatting.GRAY));
        } else {
            Integer remaining = stack.get(PFDataComponents.SPAWNS_REMAINING.get());
            Integer capacity = stack.get(PFDataComponents.MILK_CAPACITY.get());
            int rem = remaining != null ? remaining : defaultSpawnCount();
            int cap = Math.max(capacity != null ? capacity : defaultSpawnCount(), rem);
            tooltip.accept(Component.translatable("productivefrogs.jade.spawns_left", rem, cap)
                .withStyle(ChatFormatting.GRAY));
        }
        Integer speed = stack.get(PFDataComponents.MILK_SPEED.get());
        if (speed != null && speed > 0) {
            tooltip.accept(Component.translatable("productivefrogs.jade.catalyst_speed",
                speed, PFConfig.catalystMaxSpeedLevel()).withStyle(ChatFormatting.GRAY));
        }
        Integer quantity = stack.get(PFDataComponents.MILK_QUANTITY.get());
        if (quantity != null && quantity > 0) {
            tooltip.accept(Component.translatable("productivefrogs.jade.catalyst_quantity",
                quantity, PFConfig.catalystMaxQuantityLevel()).withStyle(ChatFormatting.GRAY));
        }
    }

    private static int defaultSpawnCount() {
        return com.flatts.productivefrogs.content.block.MilkSpawnEconomy.defaultSpawnCount();
    }
}
