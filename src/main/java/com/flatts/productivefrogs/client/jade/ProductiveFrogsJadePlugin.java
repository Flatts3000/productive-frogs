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
 *   <li><b>Slime Milk source block</b> - spawns remaining (from the
 *       {@code SPAWNS_REMAINING} blockstate), or "unlimited" when depletion is
 *       config-disabled. This resolves the long-standing "no depletion countdown"
 *       limitation without a custom block/fluid renderer.</li>
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

    /** Shared instances: each is both the client tooltip and the server-data fetcher. */
    private static final PrimedEggStatsProvider PRIMED_EGG_STATS = new PrimedEggStatsProvider();
    private static final TadpoleStatsProvider TADPOLE_STATS = new TadpoleStatsProvider();

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
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        ApplianceProvider provider = new ApplianceProvider();
        registration.registerBlockComponent(provider, SlimeMilkSourceBlock.class);
        registration.registerBlockComponent(provider, SlimeMilkerBlock.class);
        registration.registerBlockComponent(provider, SpawneryBlock.class);
        registration.registerEntityComponent(new FrogStatsProvider(), ResourceFrog.class);
        registration.registerBlockComponent(PRIMED_EGG_STATS, PrimedFrogEggBlock.class);
        registration.registerEntityComponent(TADPOLE_STATS, ResourceTadpole.class);
    }

    /** One provider for all three appliances; branches on the block / BlockEntity. */
    private static final class ApplianceProvider implements IBlockComponentProvider {

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockState state = accessor.getBlockState();

            if (state.getBlock() instanceof SlimeMilkSourceBlock) {
                // Only a real, variant-carrying source spawns slimes and depletes.
                // Spread/flowing milk is the SAME LiquidBlock (so it carries the
                // SPAWNS_REMAINING property at its default 16) but has no variant on
                // its BE and is inert decoration - don't annotate it. Mirror the
                // server's own gate in SlimeMilkSourceBlock#tick: the fluid must be a
                // source AND the BE must carry a variant.
                BlockEntity sourceBe = accessor.getBlockEntity();
                boolean realSource = state.getFluidState().isSource()
                    && sourceBe instanceof SlimeMilkSourceBlockEntity milkBe
                    && milkBe.getVariantId() != null;
                if (!realSource) {
                    return;
                }
                // The fluid block depletes after SPAWNS_REMAINING spawns - surface
                // the count (or "unlimited" when depletion is turned off in config).
                int remaining = state.getValue(SlimeMilkSourceBlock.SPAWNS_REMAINING);
                if (PFConfig.SPEC.isLoaded() && !PFConfig.DEPLETION_ENABLED.get()) {
                    tooltip.add(Component.translatable("productivefrogs.jade.spawns_unlimited"));
                } else {
                    // Denominator is the configured depletionCount (a fresh source starts
                    // there, not always 16 - see SlimeMilkSourceBlock.onPlace), clamped to at
                    // least the current remaining so a mid-life config change can't render
                    // "remaining > capacity". Falls back to MAX if config isn't loaded yet.
                    int cap = PFConfig.SPEC.isLoaded()
                        ? Math.max(remaining, PFConfig.DEPLETION_COUNT.get())
                        : SlimeMilkSourceBlock.MAX_SPAWNS_REMAINING;
                    tooltip.add(Component.translatable("productivefrogs.jade.spawns_left", remaining, cap));
                }
                return;
            }

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
     * its hatchlings roll fresh starter stats. See {@code docs/frog_breeding.md}.
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
     * rolls fresh starter stats when it matures. See {@code docs/frog_breeding.md}.
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
