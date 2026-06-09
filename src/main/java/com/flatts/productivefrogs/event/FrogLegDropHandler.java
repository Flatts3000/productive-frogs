package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.registry.PFEntityTags;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * Any frog killed drops Frog Legs (#194), replicating how cows/chickens drop their
 * meat: 1-2 raw legs, Looting-scaled, and <b>cooked</b> legs instead if the frog
 * was on fire (the vanilla {@code furnace_smelt}-when-on-fire trick). Cooking raw
 * legs the normal way (furnace / smoker / campfire) is covered by recipes.
 *
 * <p>Fires for <b>every</b> frog - vanilla, the Resource Frog, or a modded frog in
 * the {@code productivefrogs:frogs} tag ({@link PFEntityTags#isFrog}) - which a
 * plain per-entity loot table can't reach (it would only cover our own frog). An
 * event also config-gates cleanly. This mirrors {@code FrogTongueDropHandler},
 * which uses an event for the same "the vanilla loot-table path can't express it"
 * reason. Resource Tadpoles are not frogs, so they drop nothing.
 */
@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID)
public final class FrogLegDropHandler {

    private FrogLegDropHandler() {
        // event handler, not instantiable
    }

    @SubscribeEvent
    public static void onFrogDrops(LivingDropsEvent event) {
        if (!PFConfig.frogLegsEnabled()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (!PFEntityTags.isFrog(entity)) {
            return;
        }
        Level level = entity.level();
        if (level.isClientSide()) {
            return;
        }

        int looting = lootingLevel(level, event.getSource());
        // 1-2 base, plus 0..looting (the cow/chicken enchanted_count_increase shape).
        int count = 1 + entity.getRandom().nextInt(2);
        if (looting > 0) {
            count += entity.getRandom().nextInt(looting + 1);
        }
        // Killed while on fire -> pre-cooked, exactly like cow/chicken furnace_smelt.
        ItemStack legs = new ItemStack(
            entity.isOnFire() ? PFItems.COOKED_FROG_LEGS.get() : PFItems.RAW_FROG_LEGS.get(), count);

        ItemEntity drop = new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), legs);
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
    }

    /** Looting level on the killer's main-hand weapon, or 0 if there is no living killer. */
    private static int lootingLevel(Level level, DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity killer)) {
            return 0;
        }
        return EnchantmentHelper.getItemEnchantmentLevel(
            level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING),
            killer.getMainHandItem());
    }
}
