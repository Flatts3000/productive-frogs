package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.event.PrincessKissHandler;
import com.flatts.productivefrogs.registry.PFAttachments;
import com.flatts.productivefrogs.registry.PFEntityTags;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Princess's Kiss (#216) - a rare Ender Dragon drop. Right-click a frog to start
 * turning it into a villager (the Frog Prince), a timed conversion modelled on the
 * zombie-villager cure. Works on any frog ({@link PFEntityTags#isFrog}); the
 * countdown + the conversion itself live in {@link PrincessKissHandler}.
 */
public class PrincessKissItem extends Item {

    public PrincessKissItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!PFConfig.princessKissEnabled()) {
            return InteractionResult.PASS;
        }
        // Any frog, not already converting.
        if (!PFEntityTags.isFrog(target) || target.hasData(PFAttachments.PRINCESS_CONVERTING)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide()) {
            player.swing(hand);
            return InteractionResult.SUCCESS;
        }
        target.setData(PFAttachments.PRINCESS_CONVERTING, PrincessKissHandler.CONVERSION_TICKS);
        stack.consume(1, player);
        player.swing(hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltip, flag);
        tooltip.accept(Component.translatable("productivefrogs.princess_kiss.hint").withStyle(ChatFormatting.GRAY));
    }
}
