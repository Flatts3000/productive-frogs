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
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.frog.Tadpole;
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
import snownee.jade.api.JadeIds;
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

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "appliances");
    private static final Identifier FROG_STATS_UID =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "frog_stats");
    private static final Identifier PRIMED_EGG_STATS_UID =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "primed_egg_stats");
    private static final Identifier TADPOLE_STATS_UID =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "tadpole_stats");
    private static final Identifier MILK_SOURCE_UID =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "milk_source");
    private static final Identifier BASIN_UID =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "basin");

    /** Shared client-tooltip instances (their server-data halves are the delegates below). */
    private static final PrimedEggStatsProvider PRIMED_EGG_STATS = new PrimedEggStatsProvider();
    private static final TadpoleStatsProvider TADPOLE_STATS = new TadpoleStatsProvider();
    private static final MilkSourceProvider MILK_SOURCE = new MilkSourceProvider();
    private static final BasinProvider BASIN = new BasinProvider();
    private static final ApplianceProvider APPLIANCES = new ApplianceProvider();

    // Jade 26.1 (MC 1.21.6+) forbids a data provider from also implementing
    // IComponentProvider, so each provider's appendServerData registers through
    // this single-interface generic delegate (same UID as its client half) -
    // one record covers Block and Entity accessors alike.
    private record DataDelegate<A extends snownee.jade.api.Accessor<?>>(
        Identifier uid, java.util.function.BiConsumer<CompoundTag, A> body
    ) implements IServerDataProvider<A> {
        @Override
        public void appendServerData(CompoundTag data, A accessor) {
            body.accept(data, accessor);
        }

        @Override
        public Identifier getUid() {
            return uid;
        }
    }

    private static final DataDelegate<BlockAccessor> PRIMED_EGG_STATS_DATA =
        new DataDelegate<>(PRIMED_EGG_STATS.getUid(), PRIMED_EGG_STATS::appendServerData);
    private static final DataDelegate<EntityAccessor> TADPOLE_STATS_DATA =
        new DataDelegate<>(TADPOLE_STATS.getUid(), TADPOLE_STATS::appendServerData);
    private static final DataDelegate<BlockAccessor> MILK_SOURCE_DATA =
        new DataDelegate<>(MILK_SOURCE.getUid(), MILK_SOURCE::appendServerData);
    private static final DataDelegate<BlockAccessor> APPLIANCES_DATA =
        new DataDelegate<>(APPLIANCES.getUid(), APPLIANCES::appendServerData);
    private static final DataDelegate<BlockAccessor> BASIN_DATA =
        new DataDelegate<>(BASIN.getUid(), BASIN::appendServerData);

    /**
     * Common (server-side) registration. The pending offspring stats on a laid
     * egg and on a bred tadpole live only in server-side state (never synced),
     * so we fetch them on look via {@link IServerDataProvider}s rather than
     * permanently syncing them to every client.
     */
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(PRIMED_EGG_STATS_DATA, PrimedFrogEggBlock.class);
        registration.registerEntityDataProvider(TADPOLE_STATS_DATA, ResourceTadpole.class);
        // The spawns-remaining readout reads the authoritative server-side
        // blockstate so it updates live as the source depletes (the prior
        // client-blockstate read could show a stale full count).
        registration.registerBlockDataProvider(MILK_SOURCE_DATA, SlimeMilkSourceBlock.class);
        // Mimic Milk source (#253) shares the MILK_SOURCE provider (same UID, so no
        // extra config.jade.plugin lang key) - it branches on the block type.
        registration.registerBlockDataProvider(MILK_SOURCE_DATA,
            com.flatts.productivefrogs.content.block.MimicMilkSourceBlock.class);
        // The Terrarium machines change state fast; fetch their readouts from the
        // server BE each Jade refresh (see ApplianceProvider#appendServerData).
        registration.registerBlockDataProvider(APPLIANCES_DATA,
            com.flatts.productivefrogs.content.block.SprinklerBlock.class);
        registration.registerBlockDataProvider(APPLIANCES_DATA,
            com.flatts.productivefrogs.content.block.TerrariumControllerBlock.class);
        registration.registerBlockDataProvider(APPLIANCES_DATA,
            com.flatts.productivefrogs.content.block.IncubatorBlock.class);
        registration.registerBlockDataProvider(APPLIANCES_DATA,
            com.flatts.productivefrogs.content.block.HatchBlock.class);
        // The Basins (#281 Phase 3): the held charge's budget drains server-side,
        // so fetch it per look, exactly like the milk source it mirrors.
        registration.registerBlockDataProvider(BASIN_DATA,
            com.flatts.productivefrogs.content.block.BasinBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        ApplianceProvider provider = APPLIANCES;
        registration.registerBlockComponent(provider, SlimeMilkerBlock.class);
        registration.registerBlockComponent(provider, SpawneryBlock.class);
        registration.registerBlockComponent(provider,
            com.flatts.productivefrogs.content.block.CrucibleBlock.class);
        registration.registerBlockComponent(provider,
            com.flatts.productivefrogs.content.block.CastingMoldBlock.class);
        registration.registerBlockComponent(provider,
            com.flatts.productivefrogs.content.block.ConfigurableFroglightBlock.class);
        // One shared BossAltarHatchBlock backs every altar hatch; the provider
        // dispatches on the hatch BE subclass.
        registration.registerBlockComponent(provider,
            com.flatts.productivefrogs.content.block.BossAltarHatchBlock.class);
        registration.registerBlockComponent(provider,
            com.flatts.productivefrogs.content.block.TerrariumControllerBlock.class);
        registration.registerBlockComponent(provider,
            com.flatts.productivefrogs.content.block.SprinklerBlock.class);
        registration.registerBlockComponent(provider,
            com.flatts.productivefrogs.content.block.IncubatorBlock.class);
        registration.registerBlockComponent(provider,
            com.flatts.productivefrogs.content.block.HatchBlock.class);
        registration.registerBlockComponent(MILK_SOURCE, SlimeMilkSourceBlock.class);
        registration.registerBlockComponent(BASIN,
            com.flatts.productivefrogs.content.block.BasinBlock.class);
        registration.registerBlockComponent(MILK_SOURCE,
            com.flatts.productivefrogs.content.block.MimicMilkSourceBlock.class);
        registration.registerEntityComponent(new FrogStatsProvider(), ResourceFrog.class);
        registration.registerBlockComponent(PRIMED_EGG_STATS, PrimedFrogEggBlock.class);
        registration.registerEntityComponent(TADPOLE_STATS, ResourceTadpole.class);
    }

    /** Provider for the Slime Milker + Spawnery appliances; branches on the BlockEntity. */
    private static final class ApplianceProvider implements IBlockComponentProvider {

        /**
         * The Terrarium machines change state fast (per spawn / per distribution
         * tick), so - like {@link MilkSourceProvider} - they read the authoritative
         * SERVER BlockEntity here and Jade re-fetches on its interval. A client-BE
         * read can lag (the look-at sticks on a stale count). The other appliances
         * (Milker / Spawnery / Crucible / Mold / Froglight) ride their update tag and
         * stay client reads in {@link #appendTooltip}.
         */
        public void appendServerData(net.minecraft.nbt.CompoundTag data, BlockAccessor accessor) {
            BlockEntity be = accessor.getBlockEntity();
            if (be instanceof com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity s && !s.isEmpty()) {
                data.putString("S_variant", String.valueOf(s.getVariantId()));
                data.putBoolean("S_infinite", s.isInfinite());
                data.putInt("S_remaining", s.getSpawnsRemaining());
                data.putInt("S_cap", s.getSpawnsCapacity());
                data.putInt("S_speed", s.getSpeedLevel());
                data.putInt("S_speedMax", PFConfig.catalystMaxSpeedLevel());
                data.putInt("S_quantity", s.getQuantityLevel());
                data.putInt("S_quantityMax", PFConfig.catalystMaxQuantityLevel());
            } else if (be instanceof com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity c) {
                data.putBoolean("C_present", true);
                data.putBoolean("C_formed", c.isFormed());
                data.putInt("C_charges", c.bufferedCharges());
                data.putInt("C_depth", PFConfig.terrariumControllerBufferDepth());
                if (c.tankVariant() != null) {
                    data.putString("C_variant", c.tankVariant().toString());
                }
            } else if (be instanceof com.flatts.productivefrogs.content.block.entity.IncubatorBlockEntity i
                    && i.getCategory() != null) {
                data.putBoolean("I_present", true);
                data.putBoolean("I_waiting", i.isWaitingForSpace() || i.growthRemaining() <= 0);
                data.putInt("I_remaining", i.growthRemaining());
                data.putInt("I_total", i.growthTotal());
            } else if (be instanceof com.flatts.productivefrogs.content.block.entity.HatchBlockEntity h) {
                data.putBoolean("H_present", true);
                data.putInt("H_fill", h.fillCount());
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }

        /** Per-altar validation outcome, normalized: the lang-key prefix + valid + detail. */
        private record AltarStatus(String prefix, boolean valid, String detail) {
        }

        /** Dispatch the shared hatch BE to its altar's validator (pure client block-identity checks). */
        private static AltarStatus altarStatus(
                com.flatts.productivefrogs.content.block.entity.BossAltarHatchBlockEntity hatch,
                BlockAccessor accessor) {
            if (hatch instanceof com.flatts.productivefrogs.content.block.entity.EndDragonAltarHatchBlockEntity) {
                var r = com.flatts.productivefrogs.content.multiblock.DragonAltarValidator.validate(
                    accessor.getLevel(), accessor.getPosition());
                return new AltarStatus("productivefrogs.jade.dragon_altar", r.valid(), r.detail());
            }
            if (hatch instanceof com.flatts.productivefrogs.content.block.entity.WitherAltarHatchBlockEntity) {
                var r = com.flatts.productivefrogs.content.multiblock.WitherAltarValidator.validate(
                    accessor.getLevel(), accessor.getPosition());
                return new AltarStatus("productivefrogs.jade.wither_altar", r.valid(), r.detail());
            }
            if (hatch instanceof com.flatts.productivefrogs.content.block.entity.WardenAltarHatchBlockEntity) {
                var r = com.flatts.productivefrogs.content.multiblock.WardenAltarValidator.validate(
                    accessor.getLevel(), accessor.getPosition());
                return new AltarStatus("productivefrogs.jade.warden_altar", r.valid(), r.detail());
            }
            var r = com.flatts.productivefrogs.content.multiblock.ElderAltarValidator.validate(
                accessor.getLevel(), accessor.getPosition());
            return new AltarStatus("productivefrogs.jade.elder_altar", r.valid(), r.detail());
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockEntity be = accessor.getBlockEntity();
            if (be == null) {
                return;
            }
            // Boss altar Hatches (#247/#249/#279/#280): one branch on the shared
            // hatch BE - the per-altar validator is a pure client block-identity
            // check, plus the Phase 4 armed warning: a structurally complete altar
            // that has no Apex frog installed says so instead of reading "ready".
            if (be instanceof com.flatts.productivefrogs.content.block.entity.BossAltarHatchBlockEntity hatch) {
                var status = altarStatus(hatch, accessor);
                boolean unarmed = status.valid()
                    && com.flatts.productivefrogs.PFConfig.predatorsEnabled()
                    && !hatch.apexInstalled();
                if (unarmed) {
                    tooltip.add(Component.translatable(status.prefix() + ".no_frog"));
                } else {
                    tooltip.add(Component.translatable(status.valid()
                        ? status.prefix() + ".ready"
                        : status.prefix() + ".incomplete", status.detail()));
                }
                return;
            }
            net.minecraft.nbt.CompoundTag data = accessor.getServerData();
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
            } else if (be instanceof com.flatts.productivefrogs.content.block.entity.CastingMoldBlockEntity mold) {
                // Buffered molten + cast progress, both off the BE's update tag
                // (same client-read shape as the Crucible above).
                var fluid = mold.fluid();
                if (!fluid.isEmpty()) {
                    tooltip.add(Component.translatable("productivefrogs.jade.crucible_fluid",
                        fluid.getFluid().getFluidType().getDescription(),
                        fluid.getAmount(),
                        com.flatts.productivefrogs.content.block.entity.CastingMoldBlockEntity.TANK_CAPACITY));
                }
                int progress = mold.progress();
                if (progress > 0) {
                    tooltip.add(Component.translatable("productivefrogs.jade.progress",
                        percent(progress, com.flatts.productivefrogs.content.block.entity.CastingMoldBlockEntity.CAST_TIME)));
                }
            } else if (be instanceof com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity froglight) {
                // #251: Jade's core object-name line is the generic block name
                // ("Froglight"); replace it with the variant's name (the same name the
                // item shows), read from the BE's variant. Plain/unvariant froglights
                // keep the generic name.
                // Equivalence lane (#253): a placed Prismatic Froglight carries a
                // synthesized item, not a variant - name it "<item> Froglight" the
                // same way the item does. Takes precedence over the variant path
                // (the two are mutually exclusive).
                Identifier synthesizedItem = froglight.getSynthesizedItem();
                Identifier variantId = froglight.getVariantId();
                if (synthesizedItem != null) {
                    net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getOptional(synthesizedItem).orElse(null);
                    Component itemName = item != null
                        ? Component.translatable(item.getDescriptionId())
                        : Component.literal(synthesizedItem.toString());
                    tooltip.replace(JadeIds.CORE_OBJECT_NAME,
                        Component.translatable("block.productivefrogs.configurable_froglight.synthesized", itemName));
                } else if (variantId != null) {
                    tooltip.replace(JadeIds.CORE_OBJECT_NAME,
                        com.flatts.productivefrogs.event.FrogTongueDropHandler.buildFroglight(variantId, null).getHoverName());
                }
                // Brewed Froglight aura (#162): name the effect (with level) and its
                // on/off state. Plain Froglights add no aura line.
                if (froglight.getEffect() != null && PFConfig.brewedFroglightsEnabled()) {
                    com.flatts.productivefrogs.data.StoredEffect stored = froglight.getEffect();
                    net.minecraft.world.effect.MobEffect mobEffect = stored.effect().value();
                    Component effectName = stored.amplifier() > 0
                        ? Component.translatable("potion.withAmplifier", mobEffect.getDisplayName(),
                            Component.translatable("potion.potency." + stored.amplifier()))
                        : mobEffect.getDisplayName();
                    tooltip.add(Component.translatable(
                        stored.enabled() ? "productivefrogs.jade.aura_on" : "productivefrogs.jade.aura_off",
                        effectName));
                }
            } else if (be instanceof com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity) {
                // Formed-state + milk buffer, read from authoritative server data.
                boolean formed;
                if (data != null && data.getBooleanOr("C_present", false)) {
                    formed = data.getBooleanOr("C_formed", false);
                    tooltip.add(Component.translatable(formed
                        ? "productivefrogs.jade.terrarium_formed" : "productivefrogs.jade.terrarium_unformed"));
                    if (data.contains("C_variant")) {
                        tooltip.add(Component.translatable("productivefrogs.jade.controller_buffer",
                            data.getIntOr("C_charges", 0), data.getIntOr("C_depth", 0)));
                    }
                } else {
                    BlockState state = accessor.getBlockState();
                    formed = state.hasProperty(com.flatts.productivefrogs.content.block.TerrariumControllerBlock.FORMED)
                        && state.getValue(com.flatts.productivefrogs.content.block.TerrariumControllerBlock.FORMED);
                    tooltip.add(Component.translatable(formed
                        ? "productivefrogs.jade.terrarium_formed" : "productivefrogs.jade.terrarium_unformed"));
                }
            } else if (be instanceof com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity) {
                // Held milk variant + the source-style Count / Speed / Quantity lines,
                // from server data (re-fetched each Jade refresh) so the count never
                // sticks on a stale value.
                if (data != null && data.contains("S_variant")) {
                    Identifier variant = Identifier.tryParse(data.getStringOr("S_variant", ""));
                    if (variant != null) {
                        // translatableWithFallback so a pack/datapack-added variant
                        // (no shipped lang key) reads as a title-cased name instead
                        // of the raw "block.productivefrogs.<v>_slime_milk" key - the
                        // same fallback SlimeMilkBucketItem.getName uses.
                        tooltip.add(Component.translatable("productivefrogs.jade.sprinkler_milk",
                            Component.translatableWithFallback(
                                "block.productivefrogs." + variant.getPath() + "_slime_milk",
                                com.flatts.productivefrogs.util.VariantNames.titleCase(variant) + " Slime Milk")));
                    }
                    if (data.getBooleanOr("S_infinite", false)) {
                        tooltip.add(Component.translatable("productivefrogs.jade.spawns_unlimited"));
                    } else {
                        tooltip.add(Component.translatable("productivefrogs.jade.spawns_left",
                            data.getIntOr("S_remaining", 0), data.getIntOr("S_cap", 0)));
                    }
                    if (data.getIntOr("S_speed", 0) > 0) {
                        tooltip.add(Component.translatable("productivefrogs.jade.catalyst_speed",
                            data.getIntOr("S_speed", 0), data.getIntOr("S_speedMax", 0)));
                    }
                    if (data.getIntOr("S_quantity", 0) > 0) {
                        tooltip.add(Component.translatable("productivefrogs.jade.catalyst_quantity",
                            data.getIntOr("S_quantity", 0), data.getIntOr("S_quantityMax", 0)));
                    }
                }
            } else if (be instanceof com.flatts.productivefrogs.content.block.entity.IncubatorBlockEntity) {
                if (data != null && data.getBooleanOr("I_present", false)) {
                    if (data.getBooleanOr("I_waiting", false)) {
                        tooltip.add(Component.translatable("productivefrogs.gui.incubator.waiting"));
                    } else {
                        tooltip.add(Component.translatable("productivefrogs.jade.incubator_growing",
                            percent(data.getIntOr("I_total", 0) - data.getIntOr("I_remaining", 0), data.getIntOr("I_total", 0))));
                    }
                }
            } else if (be instanceof com.flatts.productivefrogs.content.block.entity.HatchBlockEntity) {
                if (data != null && data.getBooleanOr("H_present", false)) {
                    tooltip.add(Component.translatable("productivefrogs.jade.hatch_fill",
                        data.getIntOr("H_fill", 0), com.flatts.productivefrogs.content.block.entity.HatchBlockEntity.SLOTS));
                }
            }
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
    private static final class MilkSourceProvider implements IBlockComponentProvider {

        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            BlockState state = accessor.getBlockState();
            // Mimic Milk source (Equivalence lane, #253): a different block + BE.
            // Surface the carried item (for the name) + the spawns-left readout.
            if (state.getBlock() instanceof com.flatts.productivefrogs.content.block.MimicMilkSourceBlock) {
                if (state.getFluidState().isSource()
                        && accessor.getBlockEntity()
                            instanceof com.flatts.productivefrogs.content.block.entity.MimicMilkSourceBlockEntity mbe
                        && mbe.getSynthesizedItem() != null) {
                    data.putBoolean("MilkSource", true);
                    data.putString("MimicItem", mbe.getSynthesizedItem().toString());
                    if (PFConfig.SPEC.isLoaded() && !PFConfig.DEPLETION_ENABLED.get()) {
                        data.putBoolean("Unlimited", true);
                    } else {
                        data.putInt("SpawnsRemaining", mbe.getSpawnsRemaining());
                        data.putInt("SpawnsCap",
                            com.flatts.productivefrogs.content.block.entity.MimicMilkSourceBlockEntity.defaultSpawns());
                    }
                }
                return;
            }
            if (!(state.getBlock() instanceof SlimeMilkSourceBlock)) {
                return;
            }
            if (!(state.getFluidState().isSource()
                    && accessor.getBlockEntity() instanceof SlimeMilkSourceBlockEntity be
                    && be.getVariantId() != null)) {
                return;
            }
            data.putBoolean("MilkSource", true);
            // Boss-tier altar (#184): N/6 catalyst faces, so an incomplete shell
            // is debuggable. Written before the Unlimited early-return below so it
            // shows even on an infinite (Endless-catalyst) boss source.
            Identifier variant = be.getVariantId();
            if (SlimeMilkSourceBlock.variantRequiresCatalyst(accessor.getLevel(), variant)) {
                data.putInt("AltarFaces",
                    SlimeMilkSourceBlock.catalystFaceCount(accessor.getLevel(), accessor.getPosition(), variant));
            }
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
            if (data == null || !data.getBooleanOr("MilkSource", false)) {
                return;
            }
            // Mimic Milk source (#253): name it "<item> Slime Milk" (the generic
            // block name is "Mimic Slime Milk"); the spawns-left lines below apply.
            if (data.contains("MimicItem")) {
                Identifier itemId = Identifier.tryParse(data.getStringOr("MimicItem", ""));
                net.minecraft.world.item.Item item = itemId == null ? null
                    : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
                Component itemName = item != null
                    ? Component.translatable(item.getDescriptionId())
                    : Component.literal(data.getStringOr("MimicItem", ""));
                tooltip.replace(JadeIds.CORE_OBJECT_NAME,
                    Component.translatable("block.productivefrogs.mimic_slime_milk.item", itemName));
            }
            if (data.contains("AltarFaces")) {
                tooltip.add(Component.translatable("productivefrogs.jade.altar", data.getIntOr("AltarFaces", 0), 6));
            }
            if (data.getBooleanOr("Unlimited", false)) {
                tooltip.add(Component.translatable("productivefrogs.jade.spawns_unlimited"));
            } else {
                tooltip.add(Component.translatable("productivefrogs.jade.spawns_left",
                    data.getIntOr("SpawnsRemaining", 0), data.getIntOr("SpawnsCap", 0)));
            }
            if (data.contains("Speed")) {
                tooltip.add(Component.translatable("productivefrogs.jade.catalyst_speed",
                    data.getIntOr("Speed", 0), data.getIntOr("SpeedMax", 0)));
            }
            if (data.contains("Quantity")) {
                tooltip.add(Component.translatable("productivefrogs.jade.catalyst_quantity",
                    data.getIntOr("Quantity", 0), data.getIntOr("QuantityMax", 0)));
            }
        }

        @Override
        public Identifier getUid() {
            return MILK_SOURCE_UID;
        }
    }


    /**
     * Look-at readout for the two Basins (#281 Phase 3, maintainer ruling: a
     * Basin shows the SAME stats the fluid inside would). One provider, both
     * flavours: a contents line (the slurried mob, or the milk variant named
     * exactly as the milk source names it), then the milk source's own
     * spawns-left / unlimited / catalyst lines, from server data so the count
     * drains live.
     */
    private static final class BasinProvider implements IBlockComponentProvider {

        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity()
                    instanceof com.flatts.productivefrogs.content.block.entity.AbstractBasinBlockEntity basin)) {
                return;
            }
            data.putBoolean("Basin", true);
            if (!basin.isCharged()) {
                return;
            }
            data.putString("B_key", basin.getContainedKey().toString());
            data.putBoolean("B_mob",
                basin instanceof com.flatts.productivefrogs.content.block.entity.MobSlurryBasinBlockEntity);
            if (basin.getSpeedLevel() > 0) {
                data.putInt("Speed", basin.getSpeedLevel());
                data.putInt("SpeedMax", PFConfig.catalystMaxSpeedLevel());
            }
            if (basin.getQuantityLevel() > 0) {
                data.putInt("Quantity", basin.getQuantityLevel());
                data.putInt("QuantityMax", PFConfig.catalystMaxQuantityLevel());
            }
            boolean depletionOff = PFConfig.SPEC.isLoaded() && !PFConfig.DEPLETION_ENABLED.get();
            if (basin.isInfinite() || depletionOff) {
                data.putBoolean("Unlimited", true);
                return;
            }
            data.putInt("SpawnsRemaining", basin.getSpawnsRemaining());
            data.putInt("SpawnsCap", basin.getSpawnsCapacity());
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (data == null || !data.getBooleanOr("Basin", false)) {
                return;
            }
            if (!data.contains("B_key")) {
                tooltip.add(Component.translatable("productivefrogs.jade.basin_empty"));
                return;
            }
            Identifier key = Identifier.tryParse(data.getStringOr("B_key", ""));
            if (key != null) {
                Component contents;
                if (data.getBooleanOr("B_mob", false)) {
                    contents = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .getOptional(key)
                        .map(net.minecraft.world.entity.EntityType::getDescription)
                        .orElse(Component.literal(key.toString()));
                } else {
                    // The milk source's naming: shipped lang key with a
                    // title-cased fallback for pack-added variants.
                    contents = Component.translatableWithFallback(
                        "block.productivefrogs." + key.getPath() + "_slime_milk",
                        com.flatts.productivefrogs.util.VariantNames.titleCase(key) + " Slime Milk");
                }
                tooltip.add(Component.translatable("productivefrogs.jade.basin_contains", contents));
            }
            // The exact stat lines the fluid inside would show (milk source parity).
            if (data.getBooleanOr("Unlimited", false)) {
                tooltip.add(Component.translatable("productivefrogs.jade.spawns_unlimited"));
            } else {
                tooltip.add(Component.translatable("productivefrogs.jade.spawns_left",
                    data.getIntOr("SpawnsRemaining", 0), data.getIntOr("SpawnsCap", 0)));
            }
            if (data.contains("Speed")) {
                tooltip.add(Component.translatable("productivefrogs.jade.catalyst_speed",
                    data.getIntOr("Speed", 0), data.getIntOr("SpeedMax", 0)));
            }
            if (data.contains("Quantity")) {
                tooltip.add(Component.translatable("productivefrogs.jade.catalyst_quantity",
                    data.getIntOr("Quantity", 0), data.getIntOr("QuantityMax", 0)));
            }
        }

        @Override
        public Identifier getUid() {
            return BASIN_UID;
        }
    }

    // ---- shared stat-readout helpers (used by all three stat providers) ----

    /** Append the three breeding-stat lines as {@code value/cap} to a tooltip. */
    private static void appendStatLines(ITooltip tooltip, int appetite, int bounty, int reach, int cap) {
        // Stat layer off (#202): hide the readout entirely (frog, egg, and tadpole
        // providers all route through here). Stored stats are untouched - this only
        // suppresses the display - so re-enabling brings the lines back.
        if (!PFConfig.frogStatsEnabled()) {
            return;
        }
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
        if (data == null || !data.getBooleanOr("HasStats", false)) {
            return;
        }
        appendStatLines(tooltip, data.getIntOr("Appetite", 0), data.getIntOr("Bounty", 0),
            data.getIntOr("Reach", 0), data.getIntOr("Cap", 0));
    }

    /** Ticks -> {@code m:ss} (20 ticks per second). Shared by the egg hatch + tadpole growth countdowns. */
    private static String formatTime(int ticks) {
        int totalSeconds = ticks / 20;
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
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
        public Identifier getUid() {
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
    private static final class PrimedEggStatsProvider implements IBlockComponentProvider {

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
                    "productivefrogs.jade.hatch_countdown", formatTime(data.getIntOr("HatchTicks", 0))));
            }
        }

        @Override
        public Identifier getUid() {
            return PRIMED_EGG_STATS_UID;
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
    private static final class TadpoleStatsProvider implements IEntityComponentProvider {

        public void appendServerData(CompoundTag data, EntityAccessor accessor) {
            if (!(accessor.getEntity() instanceof ResourceTadpole tadpole)) {
                return;
            }
            if (tadpole.hasPendingStats()) {
                writePendingStats(data, tadpole.getPendingAppetite(), tadpole.getPendingBounty(),
                    tadpole.getPendingReach());
            }
            // Accelerated growth (#238): Jade's stock "Growing time" assumes the vanilla
            // 1-age/tick rate, so it reads ~24000/target too long once tadpoleGrowthTicks
            // is lowered. Ship the corrected remaining real-ticks (age is authoritative
            // here on the server) so appendTooltip can replace that line. At the default
            // (>= vanilla) there is no acceleration and Jade's own line is already right.
            if (PFConfig.tadpoleGrowthTicks() < Tadpole.ticksToBeFrog) {
                data.putInt("GrowingTicks", tadpole.remainingGrowthTicks());
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            appendPendingStats(tooltip, data);
            if (data != null && data.contains("GrowingTicks")) {
                // Drop Jade's vanilla-rate growth line and render the corrected one.
                tooltip.remove(JadeIds.MC_MOB_GROWTH);
                tooltip.add(Component.translatable(
                    "productivefrogs.jade.growing_time", formatTime(data.getIntOr("GrowingTicks", 0))));
            }
        }

        @Override
        public Identifier getUid() {
            return TADPOLE_STATS_UID;
        }
    }
}
