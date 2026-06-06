package com.flatts.productivefrogs.client.jade;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.PrimedFrogEggBlock;
import com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock;
import com.flatts.productivefrogs.content.block.SlimeMilkerBlock;
import com.flatts.productivefrogs.content.block.SpawneryBlock;
import com.flatts.productivefrogs.content.block.entity.PrimedFrogEggBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SpawneryBlockEntity;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ResourceTadpole;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade plugin: in-world look-at tooltips for the mod's appliances, adding the lines
 * Jade's default block-name display does not surface:
 *
 * <ul>
 *   <li><b>Slime Milk source block</b> - spawns remaining (read from the source
 *       BlockEntity), or "unlimited" when the source is infinite or depletion is
 *       config-disabled, plus any catalyst upgrade levels (Speed / Quantity).
 *       Resolves the "no depletion countdown" limitation without a custom
 *       block/fluid renderer.</li>
 *   <li><b>Slime Milker / Spawnery</b> - cook progress while working.</li>
 * </ul>
 *
 * <p>All data is read client-side from the synced blockstate / BlockEntity, so no
 * {@code IServerDataProvider} round-trip is needed. Jade is a {@code compileOnly}
 * dependency (a manual {@code run/mods} drop-in at runtime); when Jade is absent
 * this class is simply never loaded, so it is inert in environments without it.
 */
@WailaPlugin
public final class ProductiveFrogsJadePlugin implements IWailaPlugin {

    private static final ResourceLocation UID =
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "appliances");
    private static final ResourceLocation FROG_STATS_UID =
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "frog_stats");
    private static final ResourceLocation PRIMED_EGG_STATS_UID =
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "primed_egg_stats");
    private static final ResourceLocation TADPOLE_STATS_UID =
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "tadpole_stats");
    private static final ResourceLocation MILK_SOURCE_UID =
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "milk_source");

    /** Shared instances: each is both the client tooltip and the server-data fetcher. */
    private static final PrimedEggStatsProvider PRIMED_EGG_STATS = new PrimedEggStatsProvider();
    private static final TadpoleStatsProvider TADPOLE_STATS = new TadpoleStatsProvider();
    private static final MilkSourceProvider MILK_SOURCE = new MilkSourceProvider();

    /**
     * Common (server-side) registration. The pending offspring stats on a laid
     * egg and on a bred tadpole live only in server-side state (never synced),
     * so we fetch them on look via {@link IServerDataProvider}s rather than
     * permanently syncing them to every client.
     */
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(PRIMED_EGG_STATS, PrimedFrogEggBlock.class);
        registration.registerEntityDataProvider(TADPOLE_STATS, ResourceTadpole.class);
        // The spawns-remaining readout reads the authoritative server-side
        // blockstate so it updates live as the source depletes (the prior
        // client-blockstate read could show a stale full count).
        registration.registerBlockDataProvider(MILK_SOURCE, SlimeMilkSourceBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        ApplianceProvider provider = new ApplianceProvider();
        registration.registerBlockComponent(provider, SlimeMilkerBlock.class);
        registration.registerBlockComponent(provider, SpawneryBlock.class);
        registration.registerBlockComponent(provider,
            com.flatts.productivefrogs.content.block.CrucibleBlock.class);
        registration.registerBlockComponent(MILK_SOURCE, SlimeMilkSourceBlock.class);
        registration.registerEntityComponent(new FrogStatsProvider(), ResourceFrog.class);
        registration.registerBlockComponent(PRIMED_EGG_STATS, PrimedFrogEggBlock.class);
        registration.registerEntityComponent(TADPOLE_STATS, ResourceTadpole.class);
    }

    /** Provider for the Slime Milker + Spawnery appliances; branches on the BlockEntity. */
    private static final class ApplianceProvider implements IBlockComponentProvider {

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockEntity be = accessor.getBlockEntity();
            if (be == null) {
                return;
            }
            if (be instanceof SlimeMilkerBlockEntity milker) {
                int progress = milker.getCookProgress();
                if (progress > 0) {
                    tooltip.add(Component.translatable("productivefrogs.jade.progress",
                        percent(progress, SlimeMilkerBlockEntity.COOK_TIME_TOTAL)));
                }
            } else if (be instanceof SpawneryBlockEntity spawnery) {
                int progress = spawnery.getCookProgress();
                if (progress > 0) {
                    // Guard the config read like the depletion reads above (Jade can
                    // render a frame before COMMON config loads); fall back to the default.
                    int total = PFConfig.SPEC.isLoaded()
                        ? Math.max(1, PFConfig.SPAWNERY_PRODUCTION_TICKS.get())
                        : 200;
                    tooltip.add(Component.translatable("productivefrogs.jade.progress",
                        percent(progress, total)));
                }
            } else if (be instanceof com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity crucible) {
                // Tank + solids both ride the BE's update tag, so this is a
                // pure client read like the other appliance lines.
                var fluid = crucible.fluid();
                if (!fluid.isEmpty()) {
                    tooltip.add(Component.translatable("productivefrogs.jade.crucible_fluid",
                        fluid.getFluid().getFluidType().getDescription(),
                        fluid.getAmount(),
                        com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity.TANK_CAPACITY));
                }
                int solids = crucible.solids();
                if (solids > 0) {
                    // 1 Froglight = 1,000 mB, so the queue is also shown in
                    // Froglight units (the number the player actually thinks in).
                    tooltip.add(Component.translatable("productivefrogs.jade.crucible_solids",
                        solids, String.format("%.1f", solids / 1000.0F)));
                }
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        private static int percent(int value, int total) {
            return total <= 0 ? 0 : Math.min(100, value * 100 / total);
        }
    }

    /**
     * Look-at readout for a Slime Milk source block: spawns remaining before it
     * depletes ("Slime spawns left: N / cap"), or "unlimited" when depletion is
     * config-disabled. This reads the authoritative <b>server-side</b> blockstate
     * via {@link IServerDataProvider} and re-requests it on Jade's interval, so
     * the count updates live as the source depletes - the earlier client-side
     * blockstate read could show a stale full count (docs/known_issues.md). Same
     * server-fetch shape as the egg hatch countdown above.
     *
     * <p>Only a real, variant-carrying source spawns + depletes; spread/flowing
     * milk is the same LiquidBlock (carrying the SPAWNS_REMAINING property at its
     * default) but has no variant on its BE and is inert decoration, so it is not
     * annotated - mirroring the server's own gate in {@code SlimeMilkSourceBlock#tick}.
     */
    private static final class MilkSourceProvider
            implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            BlockState state = accessor.getBlockState();
            if (!(state.getBlock() instanceof SlimeMilkSourceBlock)) {
                return;
            }
            if (!(state.getFluidState().isSource()
                    && accessor.getBlockEntity() instanceof SlimeMilkSourceBlockEntity be
                    && be.getVariantId() != null)) {
                return;
            }
            data.putBoolean("MilkSource", true);
            // Catalyst upgrade levels (v1.7): written before the count branch so
            // they still show on an infinite source. SpeedMax/QuantityMax travel
            // with the data so the client needs no config read.
            if (be.getSpeedLevel() > 0) {
                data.putInt("Speed", be.getSpeedLevel());
                data.putInt("SpeedMax", PFConfig.catalystMaxSpeedLevel());
            }
            if (be.getQuantityLevel() > 0) {
                data.putInt("Quantity", be.getQuantityLevel());
                data.putInt("QuantityMax", PFConfig.catalystMaxQuantityLevel());
            }
            // Count line: "unlimited" when the source is infinite (Infinite Count
            // catalyst) or depletion is globally config-off; else remaining / cap.
            boolean depletionOff = PFConfig.SPEC.isLoaded() && !PFConfig.DEPLETION_ENABLED.get();
            if (be.isInfinite() || depletionOff) {
                data.putBoolean("Unlimited", true);
                return;
            }
            // Denominator is the source's tracked CAPACITY (high-water mark), not
            // max(remaining, base): Count catalysts raise capacity alongside
            // remaining, and it stays put as the source drains, so the readout
            // counts down N / cap instead of the cap chasing N downward.
            data.putInt("SpawnsRemaining", be.getSpawnsRemaining());
            data.putInt("SpawnsCap", be.getSpawnsCapacity());
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (data == null || !data.getBoolean("MilkSource")) {
                return;
            }
            if (data.getBoolean("Unlimited")) {
                tooltip.add(Component.translatable("productivefrogs.jade.spawns_unlimited"));
            } else {
                tooltip.add(Component.translatable("productivefrogs.jade.spawns_left",
                    data.getInt("SpawnsRemaining"), data.getInt("SpawnsCap")));
            }
            if (data.contains("Speed")) {
                tooltip.add(Component.translatable("productivefrogs.jade.catalyst_speed",
                    data.getInt("Speed"), data.getInt("SpeedMax")));
            }
            if (data.contains("Quantity")) {
                tooltip.add(Component.translatable("productivefrogs.jade.catalyst_quantity",
                    data.getInt("Quantity"), data.getInt("QuantityMax")));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return MILK_SOURCE_UID;
        }
    }

    // ---- shared stat-readout helpers (used by all three stat providers) ----

    /** Append the three breeding-stat lines as {@code value/cap} to a tooltip. */
    private static void appendStatLines(ITooltip tooltip, int appetite, int bounty, int reach, int cap) {
        tooltip.add(Component.translatable("productivefrogs.jade.appetite", appetite, cap));
        tooltip.add(Component.translatable("productivefrogs.jade.bounty", bounty, cap));
        tooltip.add(Component.translatable("productivefrogs.jade.reach", reach, cap));
    }

    /** Server-side: stamp pending offspring stats (+ the cap) into the look-at data tag. */
    private static void writePendingStats(CompoundTag data, int appetite, int bounty, int reach) {
        data.putBoolean("HasStats", true);
        data.putInt("Appetite", appetite);
        data.putInt("Bounty", bounty);
        data.putInt("Reach", reach);
        // Cap travels with the data so the client needs no config read.
        data.putInt("Cap", PFConfig.statCap());
    }

    /** Client-side: render the pending stat lines from server data when present (egg + tadpole). */
    private static void appendPendingStats(ITooltip tooltip, CompoundTag data) {
        if (data == null || !data.getBoolean("HasStats")) {
            return;
        }
        appendStatLines(tooltip, data.getInt("Appetite"), data.getInt("Bounty"),
            data.getInt("Reach"), data.getInt("Cap"));
    }

    /**
     * Look-at readout for a {@link ResourceFrog}: its three breeding stats
     * (Appetite / Bounty / Reach) as {@code value/cap} lines. Stats are synced
     * to the client on the entity ({@code DATA_APPETITE}/{@code _BOUNTY}/{@code _REACH}),
     * so this reads them straight off the entity (no server-data round-trip).
     * See {@code docs/frog_breeding.md}.
     */
    private static final class FrogStatsProvider implements IEntityComponentProvider {

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (accessor.getEntity() instanceof ResourceFrog frog) {
                appendStatLines(tooltip, frog.getAppetite(), frog.getBounty(), frog.getReach(), frog.getStatCap());
            }
        }

        @Override
        public ResourceLocation getUid() {
            return FROG_STATS_UID;
        }
    }

    /**
     * Look-at readout for a laid (bred) Primed Frog Egg block: the offspring
     * stats it will hatch into. Those stats live only in the egg's server-side
     * {@link PrimedFrogEggBlockEntity} (deliberately never synced - the egg
     * renders identically regardless), so this is both an
     * {@link IServerDataProvider} (server: read the BE, write the stats into the
     * look-at packet) and an {@link IBlockComponentProvider} (client: read that
     * packet back and render the lines). A non-bred egg (creative placement,
     * Spawnery output, {@code /setblock}) carries no stats and shows nothing -
     * its hatchlings mature into baseline (1/1/1) frogs. See {@code docs/frog_breeding.md}.
     */
    private static final class PrimedEggStatsProvider
            implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof PrimedFrogEggBlockEntity egg)) {
                return;
            }
            if (egg.hasStats()) {
                writePendingStats(data, egg.getAppetite(), egg.getBounty(), egg.getReach());
            }
            // Ticks until the scheduled hatch, recomputed server-side each time
            // Jade re-requests, so the tooltip counts down live while watched.
            long hatchAt = egg.getHatchGameTime();
            if (hatchAt > 0) {
                long remaining = hatchAt - accessor.getLevel().getGameTime();
                data.putInt("HatchTicks", (int) Math.max(0, remaining));
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (data == null) {
                return;
            }
            appendPendingStats(tooltip, data);
            if (data.contains("HatchTicks")) {
                tooltip.add(Component.translatable(
                    "productivefrogs.jade.hatch_countdown", formatTime(data.getInt("HatchTicks"))));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return PRIMED_EGG_STATS_UID;
        }

        /** Ticks -> {@code m:ss} (20 ticks per second). */
        private static String formatTime(int ticks) {
            int totalSeconds = ticks / 20;
            return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
        }
    }

    /**
     * Look-at readout for a bred Resource Tadpole: the stats it will mature into.
     * Like the egg, a tadpole's inherited stats are a server-side payload (carried
     * to {@code ResourceTadpole#ageUp}, never synced), so this is both an
     * {@link IServerDataProvider} (server) and an {@link IEntityComponentProvider}
     * (client). A non-bred tadpole carries no pending stats and shows nothing - it
     * matures into a baseline (1/1/1) frog. See {@code docs/frog_breeding.md}.
     */
    private static final class TadpoleStatsProvider
            implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {

        @Override
        public void appendServerData(CompoundTag data, EntityAccessor accessor) {
            if (accessor.getEntity() instanceof ResourceTadpole tadpole && tadpole.hasPendingStats()) {
                writePendingStats(data, tadpole.getPendingAppetite(), tadpole.getPendingBounty(),
                    tadpole.getPendingReach());
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            appendPendingStats(tooltip, accessor.getServerData());
        }

        @Override
        public ResourceLocation getUid() {
            return TADPOLE_STATS_UID;
        }
    }
}
