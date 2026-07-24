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
    private static final ApplianceProvider APPLIANCES = new ApplianceProvider();

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
        // Mimic Milk source (#253) shares the MILK_SOURCE provider (same UID, so no
        // extra config.jade.plugin lang key) - it branches on the block type.
        registration.registerBlockDataProvider(MILK_SOURCE,
            com.flatts.productivefrogs.content.block.MimicMilkSourceBlock.class);
        // The Terrarium machines change state fast; fetch their readouts from the
        // server BE each Jade refresh (see ApplianceProvider#appendServerData).
        registration.registerBlockDataProvider(APPLIANCES,
            com.flatts.productivefrogs.content.block.SprinklerBlock.class);
        registration.registerBlockDataProvider(APPLIANCES,
            com.flatts.productivefrogs.content.block.TerrariumControllerBlock.class);
        registration.registerBlockDataProvider(APPLIANCES,
            com.flatts.productivefrogs.content.block.IncubatorBlock.class);
        registration.registerBlockDataProvider(APPLIANCES,
            com.flatts.productivefrogs.content.block.HatchBlock.class);
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
        registration.registerBlockComponent(provider,
            com.flatts.productivefrogs.content.block.EndDragonAltarHatchBlock.class);
        registration.registerBlockComponent(provider,
            com.flatts.productivefrogs.content.block.WitherAltarHatchBlock.class);
        registration.registerBlockComponent(provider,
            com.flatts.productivefrogs.content.block.TerrariumControllerBlock.class);
        registration.registerBlockComponent(provider,
            com.flatts.productivefrogs.content.block.SprinklerBlock.class);
        registration.registerBlockComponent(provider,
            com.flatts.productivefrogs.content.block.IncubatorBlock.class);
        registration.registerBlockComponent(provider,
            com.flatts.productivefrogs.content.block.HatchBlock.class);
        registration.registerBlockComponent(MILK_SOURCE, SlimeMilkSourceBlock.class);
        registration.registerBlockComponent(MILK_SOURCE,
            com.flatts.productivefrogs.content.block.MimicMilkSourceBlock.class);
        // The Basin holds the same charge a source does, so it reads out through
        // the same provider (and the same UID, so no extra Jade config key).
        registration.registerBlockComponent(MILK_SOURCE,
            com.flatts.productivefrogs.content.block.SlimeMilkBasinBlock.class);
        registration.registerEntityComponent(new FrogStatsProvider(), ResourceFrog.class);
        registration.registerBlockComponent(PRIMED_EGG_STATS, PrimedFrogEggBlock.class);
        registration.registerEntityComponent(TADPOLE_STATS, ResourceTadpole.class);
    }

    /** Provider for the Slime Milker + Spawnery appliances; branches on the BlockEntity. */
    private static final class ApplianceProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

        /**
         * The Terrarium machines change state fast (per spawn / per distribution
         * tick), so - like {@link MilkSourceProvider} - they read the authoritative
         * SERVER BlockEntity here and Jade re-fetches on its interval. A client-BE
         * read can lag (the look-at sticks on a stale count). The other appliances
         * (Milker / Spawnery / Crucible / Mold / Froglight) ride their update tag and
         * stay client reads in {@link #appendTooltip}.
         */
        @Override
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
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockEntity be = accessor.getBlockEntity();
            if (be == null) {
                return;
            }
            // End Dragon Altar Hatch (#249): show whether the surrounding altar
            // validates. Reads only block identity, so it is a pure client check.
            if (be instanceof com.flatts.productivefrogs.content.block.entity.EndDragonAltarHatchBlockEntity) {
                com.flatts.productivefrogs.content.multiblock.DragonAltarValidator.Result r =
                    com.flatts.productivefrogs.content.multiblock.DragonAltarValidator.validate(
                        accessor.getLevel(), accessor.getPosition());
                tooltip.add(Component.translatable(r.valid()
                    ? "productivefrogs.jade.dragon_altar.ready"
                    : "productivefrogs.jade.dragon_altar.incomplete", r.detail()));
                return;
            }
            // Wither Altar Hatch (#247): same as the dragon altar - whether the
            // surrounding altar validates. Pure client block-identity check.
            if (be instanceof com.flatts.productivefrogs.content.block.entity.WitherAltarHatchBlockEntity) {
                com.flatts.productivefrogs.content.multiblock.WitherAltarValidator.Result r =
                    com.flatts.productivefrogs.content.multiblock.WitherAltarValidator.validate(
                        accessor.getLevel(), accessor.getPosition());
                tooltip.add(Component.translatable(r.valid()
                    ? "productivefrogs.jade.wither_altar.ready"
                    : "productivefrogs.jade.wither_altar.incomplete", r.detail()));
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
                ResourceLocation synthesizedItem = froglight.getSynthesizedItem();
                ResourceLocation variantId = froglight.getVariantId();
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
                if (data != null && data.getBoolean("C_present")) {
                    formed = data.getBoolean("C_formed");
                    tooltip.add(Component.translatable(formed
                        ? "productivefrogs.jade.terrarium_formed" : "productivefrogs.jade.terrarium_unformed"));
                    if (data.contains("C_variant")) {
                        tooltip.add(Component.translatable("productivefrogs.jade.controller_buffer",
                            data.getInt("C_charges"), data.getInt("C_depth")));
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
                    ResourceLocation variant = ResourceLocation.tryParse(data.getString("S_variant"));
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
                    if (data.getBoolean("S_infinite")) {
                        tooltip.add(Component.translatable("productivefrogs.jade.spawns_unlimited"));
                    } else {
                        tooltip.add(Component.translatable("productivefrogs.jade.spawns_left",
                            data.getInt("S_remaining"), data.getInt("S_cap")));
                    }
                    if (data.getInt("S_speed") > 0) {
                        tooltip.add(Component.translatable("productivefrogs.jade.catalyst_speed",
                            data.getInt("S_speed"), data.getInt("S_speedMax")));
                    }
                    if (data.getInt("S_quantity") > 0) {
                        tooltip.add(Component.translatable("productivefrogs.jade.catalyst_quantity",
                            data.getInt("S_quantity"), data.getInt("S_quantityMax")));
                    }
                }
            } else if (be instanceof com.flatts.productivefrogs.content.block.entity.IncubatorBlockEntity) {
                if (data != null && data.getBoolean("I_present")) {
                    if (data.getBoolean("I_waiting")) {
                        tooltip.add(Component.translatable("productivefrogs.gui.incubator.waiting"));
                    } else {
                        tooltip.add(Component.translatable("productivefrogs.jade.incubator_growing",
                            percent(data.getInt("I_total") - data.getInt("I_remaining"), data.getInt("I_total"))));
                    }
                }
            } else if (be instanceof com.flatts.productivefrogs.content.block.entity.HatchBlockEntity) {
                if (data != null && data.getBoolean("H_present")) {
                    tooltip.add(Component.translatable("productivefrogs.jade.hatch_fill",
                        data.getInt("H_fill"), com.flatts.productivefrogs.content.block.entity.HatchBlockEntity.SLOTS));
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
    private static final class MilkSourceProvider
            implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

        @Override
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
            // Slime Milk Basin: the container form of a source. Same charge, same
            // lines - plus the held variant's name, because a Basin's block name
            // is generic where a per-variant source block's already says it.
            if (state.getBlock() instanceof com.flatts.productivefrogs.content.block.SlimeMilkBasinBlock) {
                if (accessor.getBlockEntity()
                        instanceof com.flatts.productivefrogs.content.block.entity.SlimeMilkBasinBlockEntity basin) {
                    data.putBoolean("MilkSource", true);
                    if (basin.getContainedVariant() == null) {
                        // An empty Basin says so, rather than reading as a plain
                        // block with no tooltip at all.
                        data.putBoolean("BasinEmpty", true);
                        return;
                    }
                    data.putString("BasinVariant", basin.getContainedVariant().toString());
                    if (basin.getSpeedLevel() > 0) {
                        data.putInt("Speed", basin.getSpeedLevel());
                        data.putInt("SpeedMax", PFConfig.catalystMaxSpeedLevel());
                    }
                    if (basin.getQuantityLevel() > 0) {
                        data.putInt("Quantity", basin.getQuantityLevel());
                        data.putInt("QuantityMax", PFConfig.catalystMaxQuantityLevel());
                    }
                    boolean unlimited = basin.isInfinite()
                        || (PFConfig.SPEC.isLoaded() && !PFConfig.DEPLETION_ENABLED.get());
                    if (unlimited) {
                        data.putBoolean("Unlimited", true);
                    } else {
                        data.putInt("SpawnsRemaining", basin.getSpawnsRemaining());
                        data.putInt("SpawnsCap", basin.getSpawnsCapacity());
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
            ResourceLocation variant = be.getVariantId();
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
            if (data == null || !data.getBoolean("MilkSource")) {
                return;
            }
            // Mimic Milk source (#253): name it "<item> Slime Milk" (the generic
            // block name is "Mimic Slime Milk"); the spawns-left lines below apply.
            if (data.contains("MimicItem")) {
                ResourceLocation itemId = ResourceLocation.tryParse(data.getString("MimicItem"));
                net.minecraft.world.item.Item item = itemId == null ? null
                    : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
                Component itemName = item != null
                    ? Component.translatable(item.getDescriptionId())
                    : Component.literal(data.getString("MimicItem"));
                tooltip.replace(JadeIds.CORE_OBJECT_NAME,
                    Component.translatable("block.productivefrogs.mimic_slime_milk.item", itemName));
            }
            if (data.getBoolean("BasinEmpty")) {
                tooltip.add(Component.translatable("productivefrogs.jade.basin_empty"));
                return;
            }
            // The Basin's own name is generic, so name what it is holding. Same
            // title-cased fallback the milk bucket and the Sprinkler line use.
            if (data.contains("BasinVariant")) {
                ResourceLocation held = ResourceLocation.tryParse(data.getString("BasinVariant"));
                if (held != null) {
                    tooltip.add(Component.translatable("productivefrogs.jade.basin_milk",
                        Component.translatableWithFallback(
                            "block.productivefrogs." + held.getPath() + "_slime_milk",
                            com.flatts.productivefrogs.util.VariantNames.titleCase(held) + " Slime Milk")));
                }
            }
            if (data.contains("AltarFaces")) {
                tooltip.add(Component.translatable("productivefrogs.jade.altar", data.getInt("AltarFaces"), 6));
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
        if (data == null || !data.getBoolean("HasStats")) {
            return;
        }
        appendStatLines(tooltip, data.getInt("Appetite"), data.getInt("Bounty"),
            data.getInt("Reach"), data.getInt("Cap"));
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
                    "productivefrogs.jade.growing_time", formatTime(data.getInt("GrowingTicks"))));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return TADPOLE_STATS_UID;
        }
    }
}
