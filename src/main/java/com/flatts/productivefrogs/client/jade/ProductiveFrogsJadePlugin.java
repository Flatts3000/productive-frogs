package com.flatts.productivefrogs.client.jade;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock;
import com.flatts.productivefrogs.content.block.SlimeMilkerBlock;
import com.flatts.productivefrogs.content.block.SpawneryBlock;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SpawneryBlockEntity;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
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

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        ApplianceProvider provider = new ApplianceProvider();
        registration.registerBlockComponent(provider, SlimeMilkSourceBlock.class);
        registration.registerBlockComponent(provider, SlimeMilkerBlock.class);
        registration.registerBlockComponent(provider, SpawneryBlock.class);
        registration.registerEntityComponent(new FrogStatsProvider(), ResourceFrog.class);
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

    /**
     * Look-at readout for a {@link ResourceFrog}: its three breeding stats
     * (Appetite / Bounty / Reach) as {@code value/cap} lines. Stats are synced
     * to the client on the entity ({@code DATA_APPETITE}/{@code _BOUNTY}/{@code _REACH}),
     * so no server-data round-trip is needed. See {@code docs/frog_breeding.md}.
     */
    private static final class FrogStatsProvider implements IEntityComponentProvider {

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (!(accessor.getEntity() instanceof ResourceFrog frog)) {
                return;
            }
            int cap = frog.getStatCap();
            tooltip.add(Component.translatable("productivefrogs.jade.appetite", frog.getAppetite(), cap));
            tooltip.add(Component.translatable("productivefrogs.jade.bounty", frog.getBounty(), cap));
            tooltip.add(Component.translatable("productivefrogs.jade.reach", frog.getReach(), cap));
        }

        @Override
        public ResourceLocation getUid() {
            return FROG_STATS_UID;
        }
    }
}
