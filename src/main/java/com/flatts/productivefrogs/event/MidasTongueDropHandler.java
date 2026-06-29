package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.FrogStats;
import com.flatts.productivefrogs.content.entity.MimicSlime;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * The Equivalence-lane counterpart to {@link FrogTongueDropHandler} (#253): when
 * a <b>Midas</b> frog eats a {@link MimicSlime}, drop a Prismatic Froglight (a
 * {@code configurable_froglight} stamped with the eaten slime's
 * {@link PFDataComponents#SYNTHESIZED_ITEM}) scaled by the frog's Bounty, then
 * extracted back to the item by the Distiller.
 *
 * <p>Walled off from the six species by construction: it fires only for
 * {@code MimicSlime} (a sibling of {@code ResourceSlime}) killed by a frog with
 * {@code isMidas()} set. {@link FrogTongueDropHandler} ignores Mimic Slimes (they
 * aren't {@code ResourceSlime}), and this handler ignores everything else, so the
 * two never overlap. Reuses the same Bounty curve + Terrarium-Hatch deposit.
 */
@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID)
public final class MidasTongueDropHandler {

    private MidasTongueDropHandler() {
        // event handler, not instantiable
    }

    @SubscribeEvent
    public static void onMimicSlimeKilled(LivingDeathEvent event) {
        if (!com.flatts.productivefrogs.PFConfig.equivalenceEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof MimicSlime slime)) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof ResourceFrog frog) || !frog.isMidas()) {
            return;
        }
        if (slime.getSize() != 1 || frog.level().isClientSide()) {
            return;
        }
        Identifier itemId = slime.getSynthesizedItem();
        if (itemId == null) {
            return;
        }
        frog.startEatCooldown();
        dropPrismaticFroglight(frog, itemId);
    }

    private static void dropPrismaticFroglight(ResourceFrog frog, Identifier itemId) {
        Level level = frog.level();
        // Bounty multiplies yield exactly like the species drop (the EE printer
        // compounds: milk 1->N Mimic Slimes x Bounty Froglights per slime).
        int count = FrogStats.bountyDropCount(frog.effectiveBounty(), PFConfig.bountyMaxDrops(), PFConfig.statCap());

        // Terrarium (#185): a Midas housed in a formed Terrarium deposits straight
        // into the Hatch; a full Hatch is backpressure (stop, don't spill).
        com.flatts.productivefrogs.content.multiblock.TerrariumManager.FormedTerrarium terrarium =
            com.flatts.productivefrogs.content.multiblock.TerrariumManager.containing(level, frog.position());
        if (terrarium != null) {
            com.flatts.productivefrogs.content.block.entity.HatchBlockEntity hatch =
                level.getBlockEntity(terrarium.hatchPos())
                    instanceof com.flatts.productivefrogs.content.block.entity.HatchBlockEntity h ? h : null;
            for (int i = 0; i < count && hatch != null; i++) {
                if (!hatch.insert(buildPrismaticFroglight(itemId))) {
                    break;
                }
            }
            PFDebug.log(PFDebug.Area.TONGUE, () -> String.format(
                "midas terrarium drop: -> Hatch %s (item=%s)", terrarium.hatchPos(), itemId));
            return;
        }

        Vec3 pos = frog.position();
        for (int i = 0; i < count; i++) {
            ItemEntity drop = new ItemEntity(level, pos.x, pos.y, pos.z, buildPrismaticFroglight(itemId));
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
        }
        PFDebug.log(PFDebug.Area.TONGUE, () -> String.format(
            "midas drop: bounty=%d -> %d x Prismatic Froglight (item=%s) at %s",
            frog.getBounty(), count, itemId, frog.blockPosition()));
    }

    /** A Prismatic Froglight: a configurable Froglight carrying the synthesized item id. */
    public static ItemStack buildPrismaticFroglight(Identifier itemId) {
        ItemStack froglight = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
        froglight.set(PFDataComponents.SYNTHESIZED_ITEM.get(), itemId);
        return froglight;
    }
}
