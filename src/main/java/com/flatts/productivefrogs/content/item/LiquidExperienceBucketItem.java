package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.content.fluid.LiquidExperienceFluid;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

/**
 * The Liquid Experience bucket (#281 Phase 2). Right-click is the spend path:
 * the player absorbs the bucket's worth of XP -
 * {@link LiquidExperienceFluid#POINTS_PER_BUCKET exactly 50 points} at the
 * {@code c:experience} standard of 20 mB/point - and is left holding the empty
 * bucket. Points go straight to the player (no orb entities), so the grant is
 * exact and instant; the bucket volume divides the ratio evenly, so no XP is
 * created or destroyed at the boundary.
 *
 * <p>Liquid Experience has no block form, so this override fully replaces
 * {@link BucketItem#use} - there is nothing to place, and the vanilla place
 * path would void the fluid against the missing block. Tank/pipe transfer goes
 * through {@code Capabilities.Fluid.ITEM} instead (NeoForge's stock
 * {@code BucketResourceHandler}, wired in {@code PFModBusEvents} - subclasses
 * of {@code BucketItem} don't get the vanilla auto-registration).
 */
public final class LiquidExperienceBucketItem extends BucketItem {

    public LiquidExperienceBucketItem(Fluid content, Properties properties) {
        super(content, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltip, flag);
        tooltip.accept(Component.translatable("item.productivefrogs.liquid_experience_bucket.tooltip")
            .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            player.giveExperiencePoints(LiquidExperienceFluid.POINTS_PER_BUCKET);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);
            player.awardStat(Stats.ITEM_USED.get(this));
            // createFilledResult handles creative mode (keeps the full bucket)
            // and survival (swaps to the empty bucket) in one call.
            player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.BUCKET)));
        }
        return InteractionResult.SUCCESS;
    }
}
