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
 * Net. Catches <b>any living mob</b> - hostile, passive, boss - whole-entity
 * ({@code saveWithoutId}, the #210 lesson) and releases it elsewhere, or feeds
 * it to the Slurry Press (which converts the netted mob + an empty bucket into
 * a Mob Slurry bucket and hands the empty net back). The Press, not the net,
 * rejects boss mobs ({@code c:bosses}) - the net itself may relocate one.
 *
 * <p>The catch/release mechanic lives on {@link EntityNetItem} (shared with the
 * Frog Net); this class supplies the any-mob gate and the predation config flag.
 */
public class EnderNetItem extends EntityNetItem {

    public EnderNetItem(Properties properties) {
        super(properties);
    }

    /**
     * Any living {@link Mob}. The Mob bound (not LivingEntity) excludes players
     * and armor stands by construction; everything with an AI brain qualifies.
     */
    public static boolean isCatchable(Entity target) {
        return target instanceof Mob;
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
