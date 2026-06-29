package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.MimicSlime;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.StoredEffect;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
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
        if (!PFConfig.froglightWeaponEnabled() || event.getEntity().level().isClientSide()) {
            return;
        }
        // The Froglight to drop, by slime kind: a variant Resource Slime yields its
        // variant Froglight; a Mimic Slime (Equivalence lane, #253) yields a
        // Prismatic Froglight carrying the eaten item. Same size-1 "ripe slime"
        // rule for both (bigger slimes split rather than yield).
        ItemStack froglight;
        LivingEntity slime;
        if (event.getEntity() instanceof ResourceSlime resource) {
            if (resource.getSize() != 1 || resource.getVariantId() == null) {
                return;
            }
            StoredEffect captured = PFConfig.brewedFroglightsEnabled()
                ? StoredEffect.pick(resource.getActiveEffects()) : null;
            froglight = FrogTongueDropHandler.buildFroglight(resource.getVariantId(), captured);
            slime = resource;
        } else if (event.getEntity() instanceof MimicSlime mimic) {
            if (mimic.getSize() != 1 || mimic.getSynthesizedItem() == null) {
                return;
            }
            froglight = MidasTongueDropHandler.buildPrismaticFroglight(mimic.getSynthesizedItem());
            slime = mimic;
        } else {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity killer)
                || !killer.getMainHandItem().is(PFItems.FROGLIGHT_CLEAVER.get())) {
            return;
        }

        Level level = slime.level();
        int looting = lootingLevel(level, event.getSource());
        int count = 1 + level.getRandom().nextInt(looting + 1);

        Vec3 pos = slime.position();
        for (int i = 0; i < count; i++) {
            ItemEntity drop = new ItemEntity(level, pos.x, pos.y, pos.z, froglight.copy());
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
