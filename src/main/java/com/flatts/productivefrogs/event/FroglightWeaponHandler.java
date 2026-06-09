package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.StoredEffect;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * The Froglight Cleaver (#212): when a player kills a {@link ResourceSlime} with
 * the cleaver, drop that slime's Froglight directly - the active-play counterpart
 * to a frog eating it. Reuses {@link FrogTongueDropHandler#buildFroglight} so the
 * variant + brewed-effect stamping is identical to the tongue path. Looting raises
 * the count. Gated by {@code froglight_weapon.enabled}; the recipe's boss-Froglight
 * gate keeps it endgame.
 */
@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID)
public final class FroglightWeaponHandler {

    private FroglightWeaponHandler() {
        // event handler, not instantiable
    }

    @SubscribeEvent
    public static void onSlimeKilled(LivingDeathEvent event) {
        if (!PFConfig.froglightWeaponEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof ResourceSlime slime) || slime.level().isClientSide()) {
            return;
        }
        // Same size-1 "ripe slime" rule as the tongue drop: bigger slimes split
        // into smaller ones rather than yielding a Froglight.
        if (slime.getSize() != 1) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity killer)
                || !killer.getMainHandItem().is(PFItems.FROGLIGHT_CLEAVER.get())) {
            return;
        }
        ResourceLocation variantId = slime.getVariantId();
        if (variantId == null) {
            return;
        }

        Level level = slime.level();
        StoredEffect captured = PFConfig.brewedFroglightsEnabled()
            ? StoredEffect.pick(slime.getActiveEffects()) : null;
        int looting = lootingLevel(level, event.getSource());
        int count = 1 + level.getRandom().nextInt(looting + 1);

        Vec3 pos = slime.position();
        for (int i = 0; i < count; i++) {
            ItemEntity drop = new ItemEntity(level, pos.x, pos.y, pos.z,
                FrogTongueDropHandler.buildFroglight(variantId, captured));
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
        }
    }

    /** Looting level on the killer's main-hand weapon (the cleaver itself can carry Looting). */
    private static int lootingLevel(Level level, DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity killer)) {
            return 0;
        }
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
            .get(Enchantments.LOOTING)
            .map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, killer.getMainHandItem()))
            .orElse(0);
    }
}
