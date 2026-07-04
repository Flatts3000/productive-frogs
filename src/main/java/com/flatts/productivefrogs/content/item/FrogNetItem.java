package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.registry.PFEntityTags;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A reusable tool that catches a frog into the item and releases it elsewhere, so
 * a bred-up Resource Frog can be relocated (or a Terrarium restocked) without
 * leashing or killing it (issue #205). Catches <b>any</b> frog, vanilla or
 * modded - see {@link PFEntityTags#isFrog}; non-frogs are left alone (the Ender
 * Net is the any-mob counterpart, predation Phase 3).
 *
 * <p>The catch/release mechanic lives on {@link EntityNetItem} (shared with the
 * Ender Net); this class supplies the frog gate, the config flag, and the
 * frog-specific tooltip (the bred Appetite/Bounty/Reach stats read straight off
 * the captured NBT so a caught frog can be inspected without releasing it).
 */
public class FrogNetItem extends EntityNetItem {

    public FrogNetItem(Properties properties) {
        super(properties);
    }

    /**
     * Whether this net is allowed to catch the target - any vanilla/modded frog.
     * Shares {@link PFEntityTags#isFrog} with the frog-leg drop so "what's a frog"
     * is defined in one place. (Static because GameTests and the Phase 4 altar
     * install path check it without an item instance.)
     */
    public static boolean isCatchable(Entity target) {
        return PFEntityTags.isFrog(target);
    }

    @Override
    protected boolean canCatch(Entity target) {
        return isCatchable(target);
    }

    @Override
    protected boolean enabled() {
        return PFConfig.frogNetEnabled();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltip, flag);
        if (!isFilled(stack)) {
            tooltip.accept(Component.translatable("productivefrogs.frog_net.empty")
                .withStyle(ChatFormatting.GRAY));
            return;
        }
        CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) {
            return;
        }
        net.minecraft.nbt.CompoundTag tag = data.copyTag();
        // Bred stats live on the frog NBT (ResourceFrog.addAdditionalSaveData);
        // surface them so the player can read a caught frog without releasing it.
        if (tag.contains("Appetite") || tag.contains("Bounty") || tag.contains("Reach")) {
            tooltip.accept(Component.translatable("productivefrogs.frog_net.stats",
                    tag.getIntOr("Appetite", 0), tag.getIntOr("Bounty", 0), tag.getIntOr("Reach", 0))
                .withStyle(ChatFormatting.GRAY));
        }
        tooltip.accept(Component.translatable("productivefrogs.frog_net.release")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
