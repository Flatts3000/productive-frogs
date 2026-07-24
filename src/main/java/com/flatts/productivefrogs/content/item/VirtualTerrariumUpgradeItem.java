package com.flatts.productivefrogs.content.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A Virtual Terrarium upgrade item. Plain, single-tier; the only behaviour is a
 * one-line tooltip pulled from {@code <descriptionId>.tooltip} so each upgrade
 * states its effect in the tin.
 */
public class VirtualTerrariumUpgradeItem extends Item {

    public VirtualTerrariumUpgradeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(this.getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
