package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFItems;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/**
 * The Mob Slurry bucket (#281, predation Phase 3). One {@code mob_slurry_bucket}
 * item; the condensed mob rides as the top-level
 * {@link PFDataComponents#SLURRIED_ENTITY} component (display name "Bucket of
 * &lt;Mob&gt; Slurry"), exactly the Slime Milk bucket's R-1 shape keyed on an
 * EntityType id instead of a variant. Produced by the Slurry Press; spent by
 * pouring into a Mob Slurry Basin (the Basin's own interaction consumes it -
 * this item never places anything, the fluid has no world form).
 *
 * <p>Carries the same catalyst/budget components as milk
 * ({@code SPAWNS_REMAINING} / capacity / speed / quantity / infinite) so the
 * slurry-vs-milk parity principle holds through buckets, pipes, and Basins.
 */
public final class MobSlurryBucketItem extends BucketItem {

    public MobSlurryBucketItem(Fluid content, Properties properties) {
        super(content, properties);
    }

    /** Mint a Slurry bucket carrying {@code entityTypeId} (the Slurry Press's writer). */
    public static ItemStack forEntity(Identifier entityTypeId) {
        ItemStack stack = new ItemStack(PFItems.MOB_SLURRY_BUCKET.get());
        stack.set(PFDataComponents.SLURRIED_ENTITY.get(), entityTypeId);
        return stack;
    }

    /** The mob a Slurry bucket stack carries, or null (empty/unstamped). */
    @Nullable
    public static Identifier entityOf(ItemStack stack) {
        return stack.get(PFDataComponents.SLURRIED_ENTITY.get());
    }

    /**
     * Mob Slurry has no world form: plain right-click does nothing (the Basin's
     * block interaction is the pour path), so the vanilla place path - which
     * would void the fluid against the missing block - is never taken.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    /**
     * Mob-aware display name from the {@code SLURRIED_ENTITY} component
     * ("Bucket of Zombie Slurry"), falling back to the base name when unstamped.
     */
    @Override
    public Component getName(ItemStack stack) {
        Identifier entityId = entityOf(stack);
        if (entityId != null) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
            Component mobName = type != null ? type.getDescription() : Component.literal(entityId.toString());
            return Component.translatable("item.productivefrogs.mob_slurry_bucket.item", mobName);
        }
        return Component.translatable(getDescriptionId());
    }

    /**
     * Surface the budget on the bucket, mirroring the Slime Milk bucket's
     * tooltip (same components, same Jade format keys) - parity by reuse.
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
            int rem = remaining != null ? remaining
                : com.flatts.productivefrogs.content.block.MilkSpawnEconomy.defaultSpawnCount();
            int cap = Math.max(capacity != null ? capacity
                : com.flatts.productivefrogs.content.block.MilkSpawnEconomy.defaultSpawnCount(), rem);
            tooltip.accept(Component.translatable("productivefrogs.jade.spawns_left", rem, cap)
                .withStyle(ChatFormatting.GRAY));
        }
        tooltip.accept(Component.translatable("productivefrogs.mob_slurry_bucket.pour")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
