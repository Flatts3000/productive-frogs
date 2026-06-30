package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity;
import com.flatts.productivefrogs.registry.PFDataComponents;
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
 * A per-variant Slime Milk bucket (v1.8). One {@code <variant>_slime_milk_bucket}
 * is minted per variant by {@link com.flatts.productivefrogs.registry.PFVariantMilk},
 * each wrapping its own variant source fluid - so the <b>item identity</b> carries
 * the variant (no {@code SLIME_VARIANT} component, no dynamic name; the registry id
 * drives the display name and the per-item tint). This is what lets tank/pipe mods
 * round-trip the variant through automation: the variant is a distinct {@code Fluid}.
 *
 * <p>The only thing the bucket still carries on its components is the catalyst /
 * spawn-budget upgrade set, so a buffed source survives the world -> bucket -> world
 * round-trip. The placed source's variant + default budget are seeded by the block
 * itself ({@code SlimeMilkSourceBlock#onPlace} from its baked-in variant);
 * {@link #checkExtraContent} then restores any stored upgrades over those defaults.
 * See {@code docs/slime_milk_catalysts.md} and {@code docs/automated_milk_variants.md}.
 */
public final class SlimeMilkBucketItem extends BucketItem {

    /** The variant this bucket belongs to - drives the fallback display name. */
    private final Identifier variant;

    public SlimeMilkBucketItem(Fluid content, Identifier variant, Properties properties) {
        super(content, properties);
        this.variant = variant;
    }

    /**
     * The variant this bucket belongs to. The item identity carries it (v1.8,
     * per-variant items) - this accessor is how appliances that consume milk
     * buckets (the Slime Churn) resolve the variant without NBT.
     */
    public Identifier variantId() {
        return variant;
    }

    /**
     * Title-cased fallback name so a pack-added variant's bucket reads cleanly
     * ("Bucket of Adamantite Slime Milk") without shipping a lang key; PF's own
     * variants ship explicit {@code item.productivefrogs.<v>_slime_milk_bucket} keys.
     */
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatableWithFallback(
            getDescriptionId(),
            "Bucket of " + VariantNames.titleCase(variant) + " Slime Milk");
    }

    /**
     * Post-placement hook (vanilla calls this after the fluid block is set). The
     * block already seeded the BE's variant + default budget in {@code onPlace};
     * restore the catalyst/budget upgrades the bucket carried so a buffed or
     * partially-depleted source is faithfully replaced. A freshly-milked bucket
     * carries none of these components and keeps the seeded full default.
     */
    @Override
    public void checkExtraContent(@Nullable LivingEntity user, Level level, ItemStack stack, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SlimeMilkSourceBlockEntity milkBe)) {
            return;
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
