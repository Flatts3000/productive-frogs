package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.PFConfig;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The Ender Net (#281, predation Phase 3): the any-mob counterpart to the Frog
 * Net. Catches any living mob <b>except the denylist</b> whole-entity
 * ({@code saveWithoutId}, the #210 lesson) and releases it elsewhere, or feeds
 * it to the Slurry Press (which converts the netted mob + an empty bucket into
 * a Mob Slurry bucket and hands the empty net back).
 *
 * <p><b>Denylist</b> (maintainer ruling): the
 * {@code productivefrogs:ender_net_denylist} entity-type tag - shipping with
 * {@code #c:bosses} (wither, ender dragon, modded bosses), pack-extensible -
 * can never be caught. The Slurry Press ALSO rejects boss nets independently,
 * as defense in depth against tampered NBT.
 *
 * <p>The catch/release mechanic lives on {@link EntityNetItem} (shared with the
 * Frog Net); this class supplies the gate and the predation config flag.
 */
public class EnderNetItem extends EntityNetItem {

    public EnderNetItem(Properties properties) {
        super(properties);
    }

    /**
     * Any living {@link Mob} not on the denylist. The Mob bound (not
     * LivingEntity) excludes players and armor stands by construction.
     */
    public static boolean isCatchable(Entity target) {
        return target instanceof Mob
            && !target.getType().builtInRegistryHolder().is(
                com.flatts.productivefrogs.registry.PFEntityTags.ENDER_NET_DENYLIST);
    }

    @Override
    protected boolean canCatch(Entity target) {
        return isCatchable(target);
    }

    @Override
    protected boolean enabled() {
        return PFConfig.predatorsEnabled();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltip, flag);
        if (!isFilled(stack)) {
            tooltip.accept(Component.translatable("productivefrogs.ender_net.empty")
                .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.accept(Component.translatable("productivefrogs.ender_net.release")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
