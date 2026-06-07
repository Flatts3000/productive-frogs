package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.PrimedFrogEggBlock;
import com.flatts.productivefrogs.content.block.entity.SpawneryBlockEntity;
import com.flatts.productivefrogs.content.entity.CaveSlime;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.content.entity.ResourceTadpole;
import com.flatts.productivefrogs.content.item.ResourceTadpoleBucketItem;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFRegistries;
import com.flatts.productivefrogs.event.SlimeInfusionHandler;
import com.flatts.productivefrogs.event.SlimeSplitDiscoveryHandler;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFFluidTypes;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.registry.PFVariantMilk;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-world GameTests for Productive Frogs. Each test is a headless scenario
 * that runs inside a real Minecraft server via {@code ./gradlew runGameTestServer};
 * each is given a small plot via the referenced structure NBT and asserts
 * behavior via {@link GameTestHelper}.
 *
 * <p>Registration in MC 1.21.1 uses {@code @GameTestHolder} + {@code @GameTest}
 * annotations (the registry-based scaffold landed in 1.21.6). Methods marked
 * with {@link GameTest} are auto-discovered when this class is passed to
 * {@link RegisterGameTestsEvent#register(Class)}.
 *
 * <p>Each test must be {@code public static} and take a single
 * {@link GameTestHelper} parameter. Structure NBT lives at
 * {@code data/<modid>/structure/<name>.nbt} — singular {@code structure/}
 * since 1.21.0 (mirrors the singular {@code tags/item/}, {@code tags/entity_type/}
 * naming the rest of 1.21.x uses).
 *
 * <p>{@link PrefixGameTestTemplate}{@code (false)} disables NeoForge's default
 * class-name prefix on the structure path. Without it, {@code template =
 * "empty_5x5x5"} resolves to {@code productivefrogs:pfgametests.empty_5x5x5},
 * which would require all 47 tests to live under a {@code pfgametests/}
 * subdirectory. Since every test uses the same shared empty plot, dropping
 * the prefix keeps the asset layout flat.
 */
@GameTestHolder(ProductiveFrogs.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PFGameTests {

    private static final String EMPTY_STRUCTURE = ProductiveFrogs.MOD_ID + ":empty_5x5x5";

    private PFGameTests() {
        // static-only
    }

    /** Wire up via the mod event bus from {@code ProductiveFrogs} constructor. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(PFGameTests::onRegisterGameTests);
    }

    /**
     * Hand the entire class to NeoForge — it reflects over the methods and
     * picks up every {@code @GameTest}-annotated one. No per-test wiring.
     */
    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        event.register(PFGameTests.class);
    }

    // ---------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------

    // =================================================================
    // V1.5 species-as-category — Q1/Q2/Q3/Q4 regression pins
    // =================================================================

    /**
     * Q1=A regression pin: vanilla {@code minecraft:slime} cannot be infused.
     * Verified at the helper level via {@link SlimeInfusionHandler#resolveParentSpecies}
     * — must return null for vanilla Slime so the handler's gate rejects it.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 20)
    public static void vanillaSlimeIsNotAParentSpecies(GameTestHelper helper) {
        net.minecraft.world.entity.monster.Slime vanilla =
            helper.spawn(net.minecraft.world.entity.EntityType.SLIME, new BlockPos(2, 2, 2));
        Category resolved = SlimeInfusionHandler.resolveParentSpecies(vanilla);
        if (resolved != null) {
            helper.fail("vanilla Slime must resolve to null parent species (Q1=A), got " + resolved);
        }
        helper.succeed();
    }

    /**
     * Q1=A regression pin: vanilla {@code minecraft:magma_cube} cannot be
     * infused. Parallel to {@link #vanillaSlimeIsNotAParentSpecies}.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 20)
    public static void vanillaMagmaCubeIsNotAParentSpecies(GameTestHelper helper) {
        net.minecraft.world.entity.monster.MagmaCube vanilla =
            helper.spawn(net.minecraft.world.entity.EntityType.MAGMA_CUBE, new BlockPos(2, 2, 2));
        Category resolved = SlimeInfusionHandler.resolveParentSpecies(vanilla);
        if (resolved != null) {
            helper.fail("vanilla MagmaCube must resolve to null parent species (Q1=A), got " + resolved);
        }
        helper.succeed();
    }

    /**
     * Q1=A positive pin: all 6 PF parent species resolve to their matching
     * {@link Category}. Catches accidental species removal or wrong class
     * binding in {@link SlimeInfusionHandler#resolveParentSpecies}.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 20)
    public static void allPfParentSpeciesResolveToTheirCategory(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        assertResolves(helper, helper.spawn(PFEntities.BOG_SLIME.get(), pos),      Category.BOG);
        assertResolves(helper, helper.spawn(PFEntities.CAVE_SLIME.get(), pos),     Category.CAVE);
        assertResolves(helper, helper.spawn(PFEntities.GEODE_SLIME.get(), pos),    Category.GEODE);
        assertResolves(helper, helper.spawn(PFEntities.TIDE_SLIME.get(), pos),     Category.TIDE);
        assertResolves(helper, helper.spawn(PFEntities.INFERNAL_SLIME.get(), pos), Category.INFERNAL);
        assertResolves(helper, helper.spawn(PFEntities.VOID_SLIME.get(), pos),     Category.VOID);
        helper.succeed();
    }

    private static void assertResolves(GameTestHelper helper, net.minecraft.world.entity.monster.Slime s, Category expected) {
        Category got = SlimeInfusionHandler.resolveParentSpecies(s);
        if (got != expected) {
            helper.fail(s.getClass().getSimpleName() + " resolved to " + got + ", expected " + expected);
        }
    }

    /**
     * Q3 regression pin: an already-infused {@link ResourceSlime} is hard-rejected
     * by {@link SlimeInfusionHandler#resolveParentSpecies} (which is also
     * checked separately for the ResourceSlime instanceof early-return in
     * the handler). No variant swapping.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 20)
    public static void resourceSlimeResolvesToNullParentSpecies(GameTestHelper helper) {
        ResourceSlime resource = helper.spawn(PFEntities.RESOURCE_SLIME.get(), new BlockPos(2, 2, 2));
        resource.setCategory(Category.CAVE);
        Category resolved = SlimeInfusionHandler.resolveParentSpecies(resource);
        if (resolved != null) {
            helper.fail("ResourceSlime must NOT resolve to a parent species (Q3 hard-reject), got " + resolved);
        }
        helper.succeed();
    }

    /**
     * Q4 = Path A regression pin: a variant primer item that matches a Cave
     * variant (e.g., iron) infuses a Cave Slime into the matching variant.
     * Inverse of the cross-species rejection — confirms the happy path
     * still works under the new gate.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void caveSlimePlusCaveVariantPrimerProducesVariantSlime(GameTestHelper helper) {
        net.minecraft.core.Registry<SlimeVariant> registry =
            helper.getLevel().registryAccess().registryOrThrow(PFRegistries.SLIME_VARIANT);
        java.util.Map.Entry<ResourceLocation, SlimeVariant> ironEntry =
            SlimeVariant.findByPrimerItem(registry,
                BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT));
        if (ironEntry == null) {
            helper.fail("iron variant should be in slime_variant registry");
            return;
        }
        if (ironEntry.getValue().category() != Category.CAVE) {
            helper.fail("iron must be a Cave variant after V1.5 remap, got " + ironEntry.getValue().category());
            return;
        }

        // Exercise the helper directly — the player-interaction event would
        // produce the same outcome but requires a player which the gametest
        // harness doesn't expose cleanly.
        com.flatts.productivefrogs.content.entity.CaveSlime parent =
            helper.spawn(PFEntities.CAVE_SLIME.get(), new BlockPos(2, 2, 2));
        parent.setSize(1, true);
        ResourceSlime resource = SlimeInfusionHandler.transformInPlace(parent, Category.CAVE);
        if (resource == null) {
            helper.fail("transformInPlace returned null");
            return;
        }
        resource.setVariant(ironEntry.getKey());
        if (!ironEntry.getKey().equals(resource.getVariantId())) {
            helper.fail("expected variant " + ironEntry.getKey() + ", got " + resource.getVariantId());
        }
        helper.succeed();
    }

    /**
     * Place a Primed Frog Egg on a water source, then remove the water. The
     * block's {@code updateShape} runs {@code canSurvive}, sees no water below,
     * and replaces itself with air. Verifies the survive-on-water rule.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void primedEggBreaksWhenWaterRemoved(GameTestHelper helper) {
        BlockPos eggPos = new BlockPos(2, 2, 2);
        helper.setBlock(eggPos.below(), Blocks.WATER);
        helper.setBlock(eggPos, PFBlocks.primedEgg(Category.BOG));
        helper.assertBlockPresent(PFBlocks.primedEgg(Category.BOG), eggPos);

        // Knock out the water — the egg should detect via neighbor update
        // (PrimedFrogEggBlock.updateShape → canSurvive false → air) and disappear.
        helper.setBlock(eggPos.below(), Blocks.AIR);
        helper.succeedWhen(() -> helper.assertBlockNotPresent(PFBlocks.primedEgg(Category.BOG), eggPos));
    }

    /**
     * Place a category-primed egg, force its scheduled tick to fire, and assert
     * the hatch produced between 1 and 3 Resource Tadpoles all carrying the
     * matching category. This is the headline egg→tadpole pipeline behavior.
     *
     * <p>Vanilla schedules the hatch at random(3600..12000) ticks via
     * {@code onPlace} → {@code scheduleTick}. We can't wait that long in a test,
     * so we invoke {@link PrimedFrogEggBlock#tick} directly — exercising the
     * exact same code path the schedule would have triggered.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void primedEggHatchesIntoMatchingCategoryTadpoles(GameTestHelper helper) {
        Category cat = Category.GEODE;
        BlockPos eggPos = new BlockPos(2, 2, 2);
        helper.setBlock(eggPos.below(), Blocks.WATER);

        PrimedFrogEggBlock eggBlock = PFBlocks.primedEgg(cat);
        helper.setBlock(eggPos, eggBlock);

        ServerLevel level = helper.getLevel();
        BlockPos absEggPos = helper.absolutePos(eggPos);

        // Invoke hatch directly via the block's tick() — PrimedFrogEggBlock
        // widens the override to public, so we can call it on the concrete
        // reference and bypass the random 3600..12000-tick schedule that
        // onPlace queues up. Exercises the exact code path the schedule
        // would have reached.
        eggBlock.tick(level.getBlockState(absEggPos), level, absEggPos, level.getRandom());

        helper.assertBlockNotPresent(eggBlock, eggPos);

        List<ResourceTadpole> tadpoles = helper.getEntities(PFEntities.RESOURCE_TADPOLE.get());
        if (tadpoles.isEmpty()) {
            helper.fail("expected 1-3 Resource Tadpoles after hatch, got 0");
        }
        if (tadpoles.size() > 3) {
            helper.fail("expected 1-3 Resource Tadpoles after hatch, got " + tadpoles.size());
        }
        for (ResourceTadpole tadpole : tadpoles) {
            if (tadpole.getCategory() != cat) {
                helper.fail("hatched tadpole has category " + tadpole.getCategory() + ", expected " + cat);
            }
        }
        helper.succeed();
    }

    /**
     * Spawn a category-locked Resource Tadpole, force its maturation via the
     * access-transformer-exposed {@code ageUp()}, and assert exactly one
     * Resource Frog of the same category exists afterward. Tadpole entity
     * itself should be gone (converted, not duplicated).
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void tadpoleAgesUpIntoResourceFrogOfSameCategory(GameTestHelper helper) {
        Category cat = Category.VOID;
        BlockPos spawnPos = new BlockPos(2, 2, 2);
        helper.setBlock(spawnPos.below(), Blocks.WATER);

        ResourceTadpole tadpole = helper.spawn(PFEntities.RESOURCE_TADPOLE.get(), spawnPos);
        tadpole.setCategory(cat);
        tadpole.ageUp();

        helper.succeedWhen(() -> {
            List<ResourceFrog> frogs = helper.getEntities(PFEntities.RESOURCE_FROG.get());
            if (frogs.size() != 1) {
                helper.fail("expected 1 Resource Frog after maturation, got " + frogs.size());
            }
            if (frogs.get(0).getCategory() != cat) {
                helper.fail("matured frog has category " + frogs.get(0).getCategory() + ", expected " + cat);
            }
            if (!helper.getEntities(PFEntities.RESOURCE_TADPOLE.get()).isEmpty()) {
                helper.fail("tadpole entity must be removed during ageUp conversion");
            }
        });
    }

    /**
     * Verify the full bucket round-trip preserves category: spawn a tadpole,
     * write its state into a bucket via {@code saveToBucketTag}, read the
     * category back from the NBT, then spawn a fresh tadpole of a different
     * default category and call {@code loadFromBucketTag} on it. The fresh
     * tadpole must end up with the source's category — that's the path
     * vanilla {@code Bucketable.bucketMobPickup} → release hook takes.
     *
     * <p>The pre-PR-#60 version of this test only verified the save→read half
     * (the bucket NBT contains the category). Extending to cover
     * {@code loadFromBucketTag} closes the gap flagged in backlog.md against
     * PR #22's slime-bucket strengthening — the tadpole bucket now has the
     * same coverage shape.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void tadpoleBucketRoundTripPreservesCategory(GameTestHelper helper) {
        Category cat = Category.INFERNAL;
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.WATER);

        ResourceTadpole source = helper.spawn(PFEntities.RESOURCE_TADPOLE.get(), pos);
        source.setCategory(cat);

        ItemStack bucket = new ItemStack(PFItems.RESOURCE_TADPOLE_BUCKET.get());
        source.saveToBucketTag(bucket);

        // 1. Save → read half: bucket NBT carries the category.
        Category readBack = ResourceTadpoleBucketItem.readCategory(bucket);
        if (readBack != cat) {
            helper.fail("bucket NBT lost category: wrote " + cat + ", read " + readBack);
            return;
        }

        // 2. loadFromBucketTag half: a fresh tadpole of a DIFFERENT category
        //    has its category overwritten when the bucket is released. Picking
        //    BOG as the starting state so the assertion fails loudly if
        //    loadFromBucketTag silently no-ops.
        ResourceTadpole released = helper.spawn(PFEntities.RESOURCE_TADPOLE.get(), pos.east());
        released.setCategory(Category.BOG);
        net.minecraft.world.item.component.CustomData data =
            bucket.get(net.minecraft.core.component.DataComponents.BUCKET_ENTITY_DATA);
        if (data == null) {
            helper.fail("bucket's BUCKET_ENTITY_DATA is unexpectedly null after saveToBucketTag");
            return;
        }
        released.loadFromBucketTag(data.copyTag());

        if (released.getCategory() != cat) {
            helper.fail("released tadpole category was " + released.getCategory()
                + ", expected " + cat + " (loadFromBucketTag did not restore the bucket's category)");
            return;
        }
        helper.succeed();
    }

    /**
     * Verify {@link SlimeVariant#findByPrimerItem} resolves item ids to the
     * correct variants — covers the path the slime infusion handler walks
     * before calling {@code setVariant} on the transformed slime.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void infusionWithVariantPrimerSetsSpecificVariant(GameTestHelper helper) {
        net.minecraft.core.Registry<SlimeVariant> registry =
            helper.getLevel().registryAccess().registryOrThrow(PFRegistries.SLIME_VARIANT);

        java.util.Map.Entry<ResourceLocation, SlimeVariant> ironEntry = SlimeVariant.findByPrimerItem(
            registry, ResourceLocation.fromNamespaceAndPath("minecraft", "iron_ingot"));
        if (ironEntry == null) {
            helper.fail("iron_ingot should resolve to a variant (productivefrogs:iron)");
            return;
        }
        if (!ironEntry.getKey().equals(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"))) {
            helper.fail("expected productivefrogs:iron, got " + ironEntry.getKey());
        }

        // An infernal-flavoured item that ISN'T any variant's primer should
        // miss the variant lookup so the handler falls back to category-only.
        // (Was ghast_tear, which became a primer in the v1.13 vanilla
        // straggler sweep - ghast tears now resolve. Nether star is the
        // never-a-primer stand-in: boss drops are progression gates, not
        // resources, per #161.)
        if (SlimeVariant.findByPrimerItem(registry,
                ResourceLocation.fromNamespaceAndPath("minecraft", "nether_star")) != null) {
            helper.fail("nether_star is not a variant primer: lookup should miss");
        }

        // Stick is in NO primer tag — must miss too.
        if (SlimeVariant.findByPrimerItem(registry,
                ResourceLocation.fromNamespaceAndPath("minecraft", "stick")) != null) {
            helper.fail("stick is not a primer for any variant — lookup should miss");
        }
        helper.succeed();
    }

    /**
     * V1.5: force discovery chance to 100% and split a Cave Slime; assert
     * every offspring is a Resource Slime carrying a variant from the live CAVE
     * pool. Verifies {@link SlimeVariant#pickWeighted} integration in
     * {@code SlimeSplitDiscoveryHandler}.
     *
     * <p>Pre-V1.5 this test used vanilla slime + METALLIC pool. Post-V1.5,
     * vanilla slime is not a parent (Q1=A) so the test uses Cave Slime
     * (the canonical CAVE parent) which carries the bulk of V1's variants.
     *
     * <p>The expected CAVE pool is derived from the loaded {@code slime_variant}
     * registry rather than a hardcoded name set, so it auto-tracks any CAVE
     * variants added in later versions (v1.1 added echo_shard / glow_ink_sac /
     * obsidian, which a hardcoded set would have missed). The split children are
     * grounded on a stone floor so they cannot fall out of the structure-bounded
     * entity query during the poll window (which previously caused an
     * intermittent "got 0").
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void splitDiscoveryPicksVariantFromPool(GameTestHelper helper) {
        // Derive the expected CAVE pool from the live registry (future-proof).
        net.minecraft.core.Registry<SlimeVariant> registry =
            helper.getLevel().registryAccess().registryOrThrow(PFRegistries.SLIME_VARIANT);
        java.util.Set<String> cavePool = new java.util.HashSet<>();
        registry.entrySet().forEach(entry -> {
            if (entry.getValue().category() == Category.CAVE) {
                cavePool.add(entry.getKey().location().getPath());
            }
        });
        if (cavePool.isEmpty()) {
            helper.fail("CAVE pool is empty in the slime_variant registry; test cannot run");
            return;
        }

        // Ground the split children on a floor so they stay inside the
        // structure-bounded getEntities() query for the whole poll window.
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), net.minecraft.world.level.block.Blocks.STONE);
            }
        }
        BlockPos pos = new BlockPos(2, 2, 2);
        Float originalOverride = SlimeSplitDiscoveryHandler.getTestOverride();
        SlimeSplitDiscoveryHandler.setTestOverride(1.0f);
        try {
            com.flatts.productivefrogs.content.entity.CaveSlime parent =
                helper.spawn(PFEntities.CAVE_SLIME.get(), pos);
            parent.setSize(3, true);
            parent.setHealth(0.0F);
            parent.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);

            helper.succeedWhen(() -> {
                List<ResourceSlime> resources = helper.getEntities(PFEntities.RESOURCE_SLIME.get());
                if (resources.isEmpty()) {
                    helper.fail("expected at least one Resource Slime after forced discovery, got 0");
                }
                // At 100% chance every child must convert.
                List<com.flatts.productivefrogs.content.entity.CaveSlime> remaining =
                    helper.getEntities(PFEntities.CAVE_SLIME.get());
                if (!remaining.isEmpty()) {
                    helper.fail("expected zero Cave Slime children at 100% discovery, got "
                        + remaining.size());
                }
                for (ResourceSlime s : resources) {
                    ResourceLocation variantId = s.getVariantId();
                    if (variantId == null) {
                        helper.fail("split-discovered slime should carry a variant (CAVE pool is non-empty)");
                        return;
                    }
                    if (s.getCategory() != Category.CAVE) {
                        helper.fail("variant sync should leave category CAVE, got " + s.getCategory());
                    }
                    if (!cavePool.contains(variantId.getPath())) {
                        helper.fail("variant " + variantId.getPath() + " is not in the CAVE pool");
                    }
                }
            });
        } finally {
            SlimeSplitDiscoveryHandler.setTestOverride(originalOverride);
        }
    }

    /**
     * Mirror of {@code matching_frog_kill_drops_category_froglight} for the
     * variant-aware drop path: spawn a CAVE frog, an IRON-variant slime,
     * deal damage from the frog → assert a {@code configurable_froglight}
     * item entity drops carrying the {@code productivefrogs:iron}
     * SLIME_VARIANT component. The original category-Froglight test still
     * covers the fallback path (slime without a variant).
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void variantSlimeKillDropsConfigurableFroglight(GameTestHelper helper) {
        BlockPos frogPos = new BlockPos(2, 2, 2);
        BlockPos slimePos = new BlockPos(3, 2, 2);

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        // V1.5: iron is a Cave variant — frog category must match the
        // slime's category for FrogTongueDropHandler to emit the drop.
        frog.setCategory(Category.CAVE);

        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), slimePos);
        slime.setSize(1, true);
        ResourceLocation ironVariant = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");
        slime.setVariant(ironVariant);
        // setVariant syncs category from the registry. V1.5: iron is a Cave
        // variant (was Metallic / BOG pre-remap).
        if (slime.getCategory() != Category.CAVE) {
            helper.fail("setVariant(iron) should have synced category to CAVE, got " + slime.getCategory());
        }

        slime.hurt(helper.getLevel().damageSources().mobAttack(frog), 999.0F);

        helper.succeedWhen(() -> {
            net.minecraft.world.item.Item expected = PFItems.CONFIGURABLE_FROGLIGHT.get();
            boolean found = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .anyMatch(itemEntity -> {
                    ItemStack stack = itemEntity.getItem();
                    if (!stack.is(expected)) return false;
                    ResourceLocation variant = stack.get(
                        com.flatts.productivefrogs.registry.PFDataComponents.SLIME_VARIANT.get());
                    return ironVariant.equals(variant);
                });
            if (!found) {
                helper.fail("expected configurable_froglight stamped with iron variant to drop at frog");
            }
        });
    }

    /**
     * Verify the {@code productivefrogs:slime_variant} datapack registry is
     * populated by server boot. Spot-checks the 11 v1.0 variants (the v1.1
     * additions extend the registry further; this list is a representative
     * sample, not an exhaustive count). Confirms three things end-to-end:
     * (a) the {@code DataPackRegistryEvent.NewRegistry} listener fires and binds
     * the codec, (b) NeoForge loads JSONs from the conventional
     * {@code data/<ns>/productivefrogs/slime_variant/} path, and (c) the codec
     * decodes them without throwing.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void slimeVariantDatapackRegistryLoadsInitialVariants(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.core.Registry<SlimeVariant> registry =
            level.registryAccess().registryOrThrow(PFRegistries.SLIME_VARIANT);

        String[] expected = {
            "iron", "copper", "gold",
            "redstone", "lapis", "coal",
            "diamond", "emerald",
            "prismarine", "sponge",
            "ender_pearl"
        };
        for (String name : expected) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, name);
            SlimeVariant variant = registry.get(id);
            if (variant == null) {
                helper.fail("expected variant " + id + " to be loaded from datapack registry");
            }
        }

        // Spot-check a specific variant's decoded fields so a codec regression
        // (e.g., a field name typo) fails the test, not just "registry empty."
        SlimeVariant iron = registry.get(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        if (iron == null) {
            helper.fail("iron variant must be loaded");
            return;
        }
        if (iron.category() != Category.CAVE) {
            helper.fail("iron variant should be CAVE (V1.5 remap), got " + iron.category());
        }
        if (!iron.primerItem().equals(java.util.Optional.of(
                ResourceLocation.fromNamespaceAndPath("minecraft", "iron_ingot")))) {
            helper.fail("iron variant primer should be minecraft:iron_ingot, got " + iron.primerItem());
        }
        // v1.0.1 renders the variant's vanilla resource block inside the slime
        // via the `inner_block` field. Every shipped variant JSON declares one;
        // spot-check that the codec round-tripped it rather than silently
        // dropping it. (The pre-v1.0.1 per-variant atlas `texture` field is
        // retired; the 12 atlas PNGs were deleted.)
        if (iron.innerBlock().isEmpty()) {
            helper.fail("iron variant must declare an `inner_block` field after the v1.0.1 inner-block ship");
            return;
        }
        ResourceLocation expectedInnerBlock = ResourceLocation.parse("minecraft:iron_block");
        if (!expectedInnerBlock.equals(iron.innerBlock().get())) {
            helper.fail("iron inner_block should be " + expectedInnerBlock
                + ", got " + iron.innerBlock().get());
            return;
        }
        helper.succeed();
    }

    /**
     * Every bundled cross-mod variant is gated {@code mod_loaded(provider)}, so
     * whether it should be present depends on the runtime mod set: CI runs a lean
     * env (every provider absent), while the local dev env pulls provider mods
     * into {@code run/mods} via {@code scripts/fetch_dev_mods.py}. The expectation
     * is therefore derived per-launch instead of hardcoded:
     * {@code VariantFluidDiscovery.discover()} evaluates each bundled variant's
     * conditions against the live {@code ModList}, and the {@code slime_variant}
     * registry must agree exactly - present iff the conditions hold. That pins
     * three things at once: conditions gate variants out when the provider is
     * absent (the original regression guard), a variant DOES load when its
     * provider is present (newly exercised by the dev run/mods set), and the
     * mod-init milk discovery stays in lockstep with datapack-registry condition
     * evaluation (drift either way is the v1.8 orphan-fluid bug).
     *
     * <p>(Replaced the hardcoded absent-variant list 2026-06-06: it assumed no
     * provider mod is ever present, which broke the moment Powah landed in
     * run/mods for the #146 smoke-test environment.)
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void crossModVariantPresenceMatchesModLoadedConditions(GameTestHelper helper) {
        net.minecraft.core.Registry<SlimeVariant> registry =
            helper.getLevel().registryAccess().registryOrThrow(PFRegistries.SLIME_VARIANT);
        java.util.Set<ResourceLocation> expectedPresent =
            com.flatts.productivefrogs.setup.VariantFluidDiscovery.discover();
        java.util.List<String> allBundled =
            com.flatts.productivefrogs.setup.VariantFluidDiscovery.bundledVariantNames();
        if (allBundled.isEmpty()) {
            helper.fail("bundled variants_index.json read back empty - index resource missing?");
            return;
        }
        for (String name : allBundled) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, name);
            boolean inRegistry = registry.get(id) != null;
            boolean expected = expectedPresent.contains(id);
            if (inRegistry != expected) {
                helper.fail("variant " + id + (expected
                    ? " has its mod_loaded conditions satisfied but did not load into the registry"
                    : " should be condition-gated out (provider mod absent), but it loaded"));
                return;
            }
        }
        // Sanity: the unconditional iron variant is always expected AND present,
        // so the loop above wasn't comparing two empty sets.
        if (registry.get(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron")) == null) {
            helper.fail("built-in iron variant should be present");
            return;
        }
        helper.succeed();
    }

    /**
     * {@link SlimeVariant#primerMatches} resolves a tag-driven cross-mod variant
     * by {@code primer_tag} membership (any item in the tag primes it) and an
     * item-driven variant by exact {@code primer_item}. Uses the live
     * {@code c:ingots/iron} tag (NeoForge populates it with the vanilla iron
     * ingot), so this exercises the real runtime tag lookup the infusion handler
     * walks - the path that lets one cross-mod variant accept any mod's ingot.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void primerTagResolvesAnyTaggedItem(GameTestHelper helper) {
        SlimeVariant tagVariant = new SlimeVariant(
            java.util.Optional.empty(),
            java.util.Optional.of(net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ITEM, ResourceLocation.parse("c:ingots/iron"))),
            Category.CAVE, 0xFFFFFF, 0xFFFFFF, 1, java.util.Optional.empty(), java.util.Optional.empty());
        if (!SlimeVariant.primerMatches(tagVariant, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT))) {
            helper.fail("primer_tag c:ingots/iron should match an iron ingot");
            return;
        }
        if (SlimeVariant.primerMatches(tagVariant, new ItemStack(net.minecraft.world.item.Items.STICK))) {
            helper.fail("primer_tag c:ingots/iron must NOT match a stick");
            return;
        }

        SlimeVariant itemVariant = new SlimeVariant(
            java.util.Optional.of(ResourceLocation.parse("minecraft:diamond")),
            java.util.Optional.empty(),
            Category.GEODE, 0xFFFFFF, 0xFFFFFF, 1, java.util.Optional.empty(), java.util.Optional.empty());
        if (!SlimeVariant.primerMatches(itemVariant, new ItemStack(net.minecraft.world.item.Items.DIAMOND))) {
            helper.fail("primer_item minecraft:diamond should match a diamond");
            return;
        }
        if (SlimeVariant.primerMatches(itemVariant, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT))) {
            helper.fail("primer_item minecraft:diamond must NOT match an iron ingot");
            return;
        }
        helper.succeed();
    }

    /**
     * {@link SlimeVariant#findByPrimer} must prefer an exact {@code primer_item}
     * match over a {@code primer_tag} match when a single stack satisfies both,
     * deterministically and regardless of registry iteration order. Guards the
     * overlap case a datapack can create (add {@code c:ingots/iron} alongside a
     * first-party item-primed variant): the specific item must win. The tag
     * variant is registered FIRST here, so a naive first-match-wins resolver
     * would return it - this pins the exact-item preference.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void findByPrimerPrefersExactItemOverTag(GameTestHelper helper) {
        net.minecraft.core.MappedRegistry<SlimeVariant> registry = new net.minecraft.core.MappedRegistry<>(
            PFRegistries.SLIME_VARIANT, com.mojang.serialization.Lifecycle.stable());
        SlimeVariant tagVariant = new SlimeVariant(
            java.util.Optional.empty(),
            java.util.Optional.of(net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ITEM, ResourceLocation.parse("c:ingots/iron"))),
            Category.CAVE, 0xFFFFFF, 0xFFFFFF, 1, java.util.Optional.empty(), java.util.Optional.empty());
        SlimeVariant itemVariant = new SlimeVariant(
            java.util.Optional.of(ResourceLocation.parse("minecraft:iron_ingot")),
            java.util.Optional.empty(),
            Category.CAVE, 0xFFFFFF, 0xFFFFFF, 1, java.util.Optional.empty(), java.util.Optional.empty());
        // Tag variant registered first: iteration order would surface it first.
        net.minecraft.core.Registry.register(registry,
            ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "test_tag_iron"), tagVariant);
        net.minecraft.core.Registry.register(registry,
            ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "test_item_iron"), itemVariant);
        registry.freeze();

        java.util.Map.Entry<ResourceLocation, SlimeVariant> resolved =
            SlimeVariant.findByPrimer(registry, new ItemStack(Items.IRON_INGOT));
        if (resolved == null) {
            helper.fail("an iron ingot should resolve to one of the two overlapping variants");
            return;
        }
        if (!resolved.getKey().getPath().equals("test_item_iron")) {
            helper.fail("exact primer_item must win over primer_tag, got " + resolved.getKey());
            return;
        }
        helper.succeed();
    }

    /**
     * Verify that the {@code productivefrogs:parent_species} datapack registry
     * is populated by server boot with the 6 default entries (vanilla slime +
     * magma_cube + 4 PF parent species). Confirms the {@link
     * com.flatts.productivefrogs.data.ParentSpeciesEntry} codec decodes the
     * shipped JSONs without throwing AND that each entry maps the expected
     * entity type to the expected category. Regression-pins
     * {@code SlimeSplitDiscoveryHandler.categoryForParent}'s registry lookup —
     * if any default JSON is dropped, renamed, or has a category typo, the
     * existing 6 split-discovery GameTests would also fail, but this one
     * surfaces the registry-load failure directly without the indirection.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void parentSpeciesDatapackRegistryLoadsSixDefaults(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.core.Registry<com.flatts.productivefrogs.data.ParentSpeciesEntry> registry =
            level.registryAccess().registryOrThrow(
                com.flatts.productivefrogs.registry.PFRegistries.PARENT_SPECIES);

        // V1.5: vanilla slime + magma cube REMOVED from parent_species registry
        // (Q1=A — vanilla mobs are no longer parents). Six PF parent species
        // cover all categories.
        java.util.Map<ResourceLocation, Category> expected = new java.util.LinkedHashMap<>();
        expected.put(ResourceLocation.parse("productivefrogs:bog_slime"),      Category.BOG);
        expected.put(ResourceLocation.parse("productivefrogs:cave_slime"),     Category.CAVE);
        expected.put(ResourceLocation.parse("productivefrogs:geode_slime"),    Category.GEODE);
        expected.put(ResourceLocation.parse("productivefrogs:tide_slime"),     Category.TIDE);
        expected.put(ResourceLocation.parse("productivefrogs:infernal_slime"), Category.INFERNAL);
        expected.put(ResourceLocation.parse("productivefrogs:void_slime"),     Category.VOID);

        java.util.Map<ResourceLocation, Category> actual = new java.util.HashMap<>();
        for (com.flatts.productivefrogs.data.ParentSpeciesEntry entry : registry) {
            actual.put(entry.entityType(), entry.category());
        }

        for (var e : expected.entrySet()) {
            Category got = actual.get(e.getKey());
            if (got == null) {
                helper.fail("parent_species registry is missing " + e.getKey());
                return;
            }
            if (got != e.getValue()) {
                helper.fail("parent_species " + e.getKey() + " maps to " + got
                    + ", expected " + e.getValue());
                return;
            }
        }
        helper.succeed();
    }

    /**
     * Spawn a Cave Slime, run it through the infusion helper, and assert the
     * source is gone and a ResourceSlime of the matching category sits at the
     * same place with the same size. Exercises the {@link SlimeInfusionHandler#transformInPlace}
     * helper directly — does not go through the player-interaction gate
     * (that gate is covered separately by the Q1/Q2/Q3/Q4 regression tests).
     *
     * <p>V1.5: the test was previously called slimeInfusionTransformsVanillaIntoResourceSlime
     * and used a vanilla {@code minecraft:slime} as the source. Per Q1=A,
     * vanilla mobs are no longer parent species, so the higher-level handler
     * rejects them. The low-level helper {@code transformInPlace} still
     * accepts any {@link net.minecraft.world.entity.monster.Slime} (it
     * doesn't read the parent_species registry) — but for a realistic
     * positive test we now seed it with a Cave Slime which is the canonical
     * CAVE parent.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void slimeInfusionTransformsParentIntoVariantSlime(GameTestHelper helper) {
        Category cat = Category.CAVE;
        BlockPos spawnPos = new BlockPos(2, 2, 2);

        com.flatts.productivefrogs.content.entity.CaveSlime parent =
            helper.spawn(PFEntities.CAVE_SLIME.get(), spawnPos);
        parent.setSize(2, true);
        int originalSize = parent.getSize();

        ResourceSlime resource = SlimeInfusionHandler.transformInPlace(parent, cat);
        if (resource == null) {
            helper.fail("transformInPlace returned null");
            return;
        }
        if (resource.getCategory() != cat) {
            helper.fail("expected category " + cat + ", got " + resource.getCategory());
        }
        if (resource.getSize() != originalSize) {
            helper.fail("expected size " + originalSize + ", got " + resource.getSize());
        }
        if (parent.isAlive()) {
            helper.fail("source Cave Slime should be discarded after infusion");
        }
        if (!helper.getEntities(PFEntities.CAVE_SLIME.get()).isEmpty()) {
            helper.fail("no Cave Slimes should remain in the test plot after infusion");
        }
        helper.succeed();
    }

    /**
     * Spawn a BOG ResourceFrog with both a BOG and an INFERNAL slime
     * within tongue range. The category-filtered sensor should write only the
     * BOG slime into {@code NEAREST_ATTACKABLE}; the INFERNAL one must be
     * filtered out. Verifies {@link
     * com.flatts.productivefrogs.content.entity.ai.ResourceFrogAttackablesSensor}
     * is wired into ResourceFrog's brain provider and the category check
     * actually fires.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 200)
    public static void frogTongueTargetsOnlyMatchingCategorySlime(GameTestHelper helper) {
        Category cat = Category.BOG;
        BlockPos frogPos = new BlockPos(2, 2, 2);

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(cat);

        // Same-category prey at one offset, off-category prey at the other.
        // Both within the 10-block sensor radius.
        ResourceSlime matching = helper.spawn(PFEntities.RESOURCE_SLIME.get(), frogPos.east());
        matching.setSize(1, true);
        matching.setCategory(cat);

        ResourceSlime offCategory = helper.spawn(PFEntities.RESOURCE_SLIME.get(), frogPos.west());
        offCategory.setSize(1, true);
        offCategory.setCategory(Category.INFERNAL);

        // Two sensors chain to populate NEAREST_ATTACKABLE:
        //   1. NEAREST_LIVING_ENTITIES   → writes NEAREST_VISIBLE_LIVING_ENTITIES
        //   2. RESOURCE_FROG_ATTACKABLES → reads NEAREST_VISIBLE_LIVING_ENTITIES,
        //                                  filters by category, writes NEAREST_ATTACKABLE
        //
        // Each Sensor's first scan is offset by a random tick in [0, scanRate)
        // chosen at construction, and the chain only settles once both sensors
        // have fired in the right order. A fixed-tick assertion (the original
        // runAfterDelay(60L, ...)) raced against worst-case timing and went
        // flaky on PR #32.
        //
        // Polling pattern below:
        //   - Fail immediately if NEAREST_ATTACKABLE ever points at the
        //     off-category slime (the category filter is broken).
        //   - Require a stability window of STABLE_TICKS consecutive ticks of
        //     NEAREST_ATTACKABLE == matching before succeeding. A single
        //     correct tick isn't enough -- the memory could oscillate after
        //     and we'd miss it.
        //   - Delayed-fallback assertion at tick 180 reports a specific
        //     last-observed-state failure if the chain never settles, instead
        //     of falling through to the generic 200-tick timeout.
        final int STABLE_TICKS = 10;
        java.util.concurrent.atomic.AtomicInteger consecutiveMatches = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<LivingEntity> lastTarget = new java.util.concurrent.atomic.AtomicReference<>();
        helper.onEachTick(() -> {
            LivingEntity target = frog.getBrain()
                .getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_ATTACKABLE)
                .orElse(null);
            lastTarget.set(target);
            if (target == offCategory) {
                helper.fail("sensor targeted the off-category slime — category filter is broken");
                return;
            }
            if (target == matching) {
                if (consecutiveMatches.incrementAndGet() >= STABLE_TICKS) {
                    helper.succeed();
                }
            } else {
                consecutiveMatches.set(0);
            }
        });
        helper.runAfterDelay(180L, () -> {
            LivingEntity target = lastTarget.get();
            String label = target == null
                ? "null"
                : target == matching ? "matching (but stability window never reached)"
                : target == offCategory ? "offCategory (would have already failed above)"
                : target.toString();
            helper.fail("NEAREST_ATTACKABLE never settled on matching for "
                + STABLE_TICKS + " consecutive ticks within 180-tick window; last observed target=" + label);
        });
    }

    /**
     * Place a matching-category frog and a variant-stamped slime; deal lethal
     * damage to the slime sourced from the frog (simulating the result of the
     * tongue eat); assert a {@code configurable_froglight} stamped with the
     * same variant drops at the frog's position.
     *
     * <p>V1.5: ResourceSlime always carries a variant (the category-only
     * intermediate was removed). Test seeds the slime with the prismarine
     * variant (Tide species) and the frog with TIDE category.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void matchingFrogKillDropsConfigurableFroglight(GameTestHelper helper) {
        Category cat = Category.TIDE;
        BlockPos frogPos = new BlockPos(2, 2, 2);
        BlockPos slimePos = new BlockPos(3, 2, 2);

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(cat);

        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), slimePos);
        slime.setSize(1, true);
        ResourceLocation prismarine = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "prismarine");
        slime.setVariant(prismarine);

        slime.hurt(helper.getLevel().damageSources().mobAttack(frog), 999.0F);

        helper.succeedWhen(() -> {
            boolean found = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .anyMatch(itemEntity -> {
                    ItemStack stack = itemEntity.getItem();
                    return stack.is(PFItems.CONFIGURABLE_FROGLIGHT.get())
                        && prismarine.equals(stack.get(com.flatts.productivefrogs.registry.PFDataComponents.SLIME_VARIANT.get()));
                });
            if (!found) {
                helper.fail("expected prismarine-stamped configurable_froglight to drop at frog position");
            }
        });
    }

    /**
     * Mismatched-category kill: a Bog Frog (BOG category) kills an
     * Infernal-stamped slime (INFERNAL category). Handler must skip its drop
     * because the categories disagree. Asserts no configurable_froglight
     * item entity appears.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void mismatchedFrogKillDropsNoFroglight(GameTestHelper helper) {
        BlockPos frogPos = new BlockPos(2, 2, 2);
        BlockPos slimePos = new BlockPos(3, 2, 2);

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(Category.BOG);

        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), slimePos);
        slime.setSize(1, true);
        // Blaze is an Infernal variant — categorically wrong for the Bog frog.
        slime.setVariant(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "blaze"));

        slime.hurt(helper.getLevel().damageSources().mobAttack(frog), 999.0F);

        // Wait then verify no configurable_froglight dropped.
        helper.runAfterDelay(20L, () -> {
            boolean found = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .anyMatch(itemEntity -> itemEntity.getItem().is(PFItems.CONFIGURABLE_FROGLIGHT.get()));
            if (found) {
                helper.fail("category mismatch should not drop configurable_froglight");
            }
            helper.succeed();
        });
    }

    /**
     * Exercise the full bucket pickup→release contract: spawn a CAVE slime,
     * write its state into a bucket via {@code saveToBucketTag}, then load
     * that bucket NBT into a fresh ResourceSlime via {@code loadFromBucketTag}.
     * Verifies (1) the bucket carries the category, (2) the released slime
     * decodes it correctly, and (3) the released slime is flagged
     * {@code fromBucket} so it doesn't despawn on chunk reload.
     *
     * <p>Cross-bucket detail: ResourceTadpoleBucketItem.readCategory works for
     * the slime bucket too because both bucket types write the same
     * {@code BUCKET_ENTITY_DATA → "Category" string} shape — same reader is
     * referenced by both bucket item models via the renamed
     * {@code BucketedCategoryTint} ItemTintSource.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void slimeBucketRoundTripPreservesCategory(GameTestHelper helper) {
        Category cat = Category.CAVE;
        BlockPos pos = new BlockPos(2, 2, 2);

        ResourceSlime source = helper.spawn(PFEntities.RESOURCE_SLIME.get(), pos);
        source.setSize(1, true);
        source.setCategory(cat);

        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        source.saveToBucketTag(bucket);

        // Step 1: NBT round-trip via the tint-source reader.
        Category readBack = ResourceTadpoleBucketItem.readCategory(bucket);
        if (readBack != cat) {
            helper.fail("slime bucket round-trip lost category: wrote " + cat + ", read " + readBack);
        }

        // Step 2: spawn a fresh slime and exercise loadFromBucketTag — same
        // path vanilla MobBucketItem walks on release.
        ResourceSlime released = helper.spawn(PFEntities.RESOURCE_SLIME.get(), pos.east());
        net.minecraft.world.item.component.CustomData data =
            bucket.get(net.minecraft.core.component.DataComponents.BUCKET_ENTITY_DATA);
        if (data == null) {
            helper.fail("bucket's BUCKET_ENTITY_DATA component is unexpectedly null after saveToBucketTag");
            return;
        }
        released.loadFromBucketTag(data.copyTag());

        if (released.getCategory() != cat) {
            helper.fail("released slime has category " + released.getCategory() + ", expected " + cat);
        }
        if (!released.fromBucket()) {
            helper.fail("released slime must be flagged fromBucket so it survives chunk reload");
        }
        helper.succeed();
    }

    /**
     * Bug fix (known_issues): a size-1 Resource Slime is captured with an
     * <b>empty</b> bucket, not a water bucket. Right-clicking with
     * {@code Items.BUCKET} fills a Slime Bucket and removes the slime; a water
     * bucket must NOT capture it (falls through to vanilla, water bucket
     * unconsumed). Pins {@code ResourceSlime.tryEmptyBucketCapture} against a
     * regression to vanilla {@code Bucketable.bucketMobPickup}, which keys on
     * the water bucket (the fish/axolotl/tadpole convention).
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void emptyBucketCapturesSlimeWaterBucketDoesNot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        net.minecraft.world.entity.player.Player player =
            helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);

        // 1. Water bucket must NOT capture (the old, wrong behavior).
        ResourceSlime water = helper.spawn(PFEntities.RESOURCE_SLIME.get(), pos);
        water.setSize(1, true);
        water.setCategory(Category.CAVE);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
            new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
        net.minecraft.world.InteractionResult waterResult =
            water.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        if (waterResult.consumesAction()) {
            helper.fail("water bucket must NOT be handled as a capture, got " + waterResult);
            return;
        }
        if (!player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)
                .is(net.minecraft.world.item.Items.WATER_BUCKET)) {
            helper.fail("water bucket must NOT capture a Resource Slime (it should remain a water bucket)");
            return;
        }
        if (!water.isAlive()) {
            helper.fail("water bucket must not consume/discard the slime");
            return;
        }
        water.discard();

        // 2. Empty bucket must NOT capture a size > 1 slime (size gate): larger
        //    slimes split and the player buckets the offspring.
        ResourceSlime big = helper.spawn(PFEntities.RESOURCE_SLIME.get(), pos);
        big.setSize(2, true);
        big.setCategory(Category.CAVE);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
            new ItemStack(net.minecraft.world.item.Items.BUCKET));
        net.minecraft.world.InteractionResult bigResult =
            big.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        if (bigResult.consumesAction()) {
            helper.fail("empty bucket must NOT capture a size-2 slime, got " + bigResult);
            return;
        }
        if (!player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)
                .is(net.minecraft.world.item.Items.BUCKET)) {
            helper.fail("empty bucket must remain empty when used on a size-2 slime");
            return;
        }
        big.discard();

        // 3. Empty bucket captures a size-1 slime: slime gone, player holds a slime bucket.
        ResourceSlime empty = helper.spawn(PFEntities.RESOURCE_SLIME.get(), pos);
        empty.setSize(1, true);
        empty.setCategory(Category.CAVE);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
            new ItemStack(net.minecraft.world.item.Items.BUCKET));
        net.minecraft.world.InteractionResult result =
            empty.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        if (!result.consumesAction()) {
            helper.fail("empty bucket must capture a size-1 Resource Slime, got " + result);
            return;
        }
        ItemStack held = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (!held.is(PFItems.SLIME_BUCKET.get())) {
            helper.fail("after capture the player must hold a slime bucket, got "
                + BuiltInRegistries.ITEM.getKey(held.getItem()));
            return;
        }
        if (empty.isAlive()) {
            helper.fail("captured slime must be discarded");
            return;
        }
        helper.succeed();
    }

    /**
     * Bucket a variant-stamped Resource Slime, then assert the
     * {@code BUCKET_ENTITY_DATA} payload carries the {@code Variant} NBT
     * (read via {@code ResourceTadpoleBucketItem.readVariant}) AND that
     * releasing the bucket restores the variant on the spawned slime.
     *
     * <p>This pins the precondition for the variant-aware Slime Bucket tint:
     * if the bucket doesn't carry the Variant id after capture, the
     * {@code BucketedCategoryTint} resolution-order would skip the
     * variant lookup and fall back to the broader category colour.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void slimeBucketRoundTripPreservesVariant(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        ResourceLocation variantId =
            ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "copper");

        ResourceSlime source = helper.spawn(PFEntities.RESOURCE_SLIME.get(), pos);
        source.setSize(1, true);
        source.setVariant(variantId);

        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        source.saveToBucketTag(bucket);

        ResourceLocation readBack =
            com.flatts.productivefrogs.content.item.ResourceTadpoleBucketItem.readVariant(bucket);
        if (readBack == null || !readBack.equals(variantId)) {
            helper.fail("slime bucket round-trip lost variant: wrote " + variantId
                + ", read " + readBack);
            return;
        }

        ResourceSlime released = helper.spawn(PFEntities.RESOURCE_SLIME.get(), pos.east());
        net.minecraft.world.item.component.CustomData data =
            bucket.get(net.minecraft.core.component.DataComponents.BUCKET_ENTITY_DATA);
        if (data == null) {
            helper.fail("bucket's BUCKET_ENTITY_DATA is unexpectedly null after saveToBucketTag");
            return;
        }
        released.loadFromBucketTag(data.copyTag());

        if (!variantId.equals(released.getVariantId())) {
            helper.fail("released slime variant was " + released.getVariantId()
                + ", expected " + variantId);
            return;
        }
        helper.succeed();
    }

    /**
     * V1.5 species-as-category: Bog Slime (the PF parent species for BOG)
     * splits convert to BOG ResourceSlimes at 100% discovery. Replaces the
     * pre-V1.5 vanillaSlimeSplitDiscoveryConvertsToMetallicResourceSlime test
     * — vanilla slime is no longer a parent (Q1=A).
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void bogSlimeSplitDiscoveryConvertsToBogResourceSlime(GameTestHelper helper) {
        runSplitDiscoveryTest(helper, PFEntities.BOG_SLIME.get(), Category.BOG);
    }

    /**
     * V1.5 species-as-category: Infernal Slime (the PF parent species for
     * INFERNAL) splits convert to INFERNAL ResourceSlimes at 100% discovery.
     * Replaces the pre-V1.5 vanillaMagmaCubeSplitDiscoveryConvertsToInfernalResourceSlime
     * test — vanilla magma cube is no longer a parent (Q1=A).
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void infernalSlimeSplitDiscoveryConvertsToInfernalResourceSlime(GameTestHelper helper) {
        runSplitDiscoveryTest(helper, PFEntities.INFERNAL_SLIME.get(), Category.INFERNAL);
    }

    /**
     * Cave Slime (V1.5: the CAVE parent species) splits with 100% discovery
     * convert to CAVE ResourceSlimes. Exercises the
     * {@code SlimeSplitDiscoveryHandler#categoryForParent} registry lookup
     * for the {@code productivefrogs:cave_slime} entry.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void caveSlimeSplitDiscoveryConvertsToCaveResourceSlime(GameTestHelper helper) {
        runSplitDiscoveryTest(helper, PFEntities.CAVE_SLIME.get(), Category.CAVE);
    }

    /**
     * Geode Slime (V1.5: the GEODE parent species) splits with 100% discovery
     * convert to GEODE ResourceSlimes.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void geodeSlimeSplitDiscoveryConvertsToGeodeResourceSlime(GameTestHelper helper) {
        runSplitDiscoveryTest(helper, PFEntities.GEODE_SLIME.get(), Category.GEODE);
    }

    /**
     * Tide Slime (V1.5: the TIDE parent species) splits with 100% discovery
     * convert to TIDE ResourceSlimes.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void tideSlimeSplitDiscoveryConvertsToTideResourceSlime(GameTestHelper helper) {
        runSplitDiscoveryTest(helper, PFEntities.TIDE_SLIME.get(), Category.TIDE);
    }

    /**
     * Void Slime (V1.5: the VOID parent species) splits with 100% discovery
     * convert to VOID ResourceSlimes. Closes the parent-species test set
     * (one per species — bog + cave + geode + tide + infernal + void).
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void voidSlimeSplitDiscoveryConvertsToVoidResourceSlime(GameTestHelper helper) {
        runSplitDiscoveryTest(helper, PFEntities.VOID_SLIME.get(), Category.VOID);
    }

    /**
     * Shared body for the discovery tests — force chance=1.0, split a size-3
     * parent, assert all children are ResourceSlimes of the expected category.
     * Restores chance via try/finally so subsequent tests aren't affected.
     */
    private static <T extends net.minecraft.world.entity.monster.Slime> void runSplitDiscoveryTest(
            GameTestHelper helper,
            net.minecraft.world.entity.EntityType<T> parentType,
            Category expectedCategory) {
        BlockPos pos = new BlockPos(2, 2, 2);
        Float originalOverride = SlimeSplitDiscoveryHandler.getTestOverride();
        SlimeSplitDiscoveryHandler.setTestOverride(1.0f);
        try {
            T parent = helper.spawn(parentType, pos);
            parent.setSize(3, true);
            // Force death + split. setHealth(0) flips isDeadOrDying, then
            // remove(KILLED) runs vanilla's split which fires MobSplitEvent.
            parent.setHealth(0.0F);
            parent.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);

            helper.succeedWhen(() -> {
                List<ResourceSlime> resources = helper.getEntities(PFEntities.RESOURCE_SLIME.get());
                if (resources.isEmpty()) {
                    helper.fail("expected at least one ResourceSlime from forced 100% discovery, got 0");
                }
                List<? extends net.minecraft.world.entity.monster.Slime> vanillaRemaining =
                    helper.getEntities(parentType);
                if (!vanillaRemaining.isEmpty()) {
                    helper.fail("expected zero vanilla " + parentType + " children after 100% conversion, "
                        + "got " + vanillaRemaining.size());
                }
                for (ResourceSlime slime : resources) {
                    if (slime.getCategory() != expectedCategory) {
                        helper.fail("discovered slime has category " + slime.getCategory()
                            + ", expected " + expectedCategory);
                    }
                }
            });
        } finally {
            SlimeSplitDiscoveryHandler.setTestOverride(originalOverride);
        }
    }

    /**
     * Spawn a size-3 ResourceSlime of one category, kill it, and assert the
     * split children are also ResourceSlimes of the same category. Verifies
     * the {@code Slime#remove} override propagates category through the
     * convertTo lambda.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void resourceSlimeSplitPreservesCategory(GameTestHelper helper) {
        Category cat = Category.INFERNAL;
        BlockPos spawnPos = new BlockPos(2, 2, 2);

        ResourceSlime parent = helper.spawn(PFEntities.RESOURCE_SLIME.get(), spawnPos);
        parent.setSize(3, true);
        parent.setCategory(cat);

        // Trigger death + split. Setting health to 0 makes isDeadOrDying() true,
        // and remove(KILLED) runs our override's split logic before delegating
        // to super.
        parent.setHealth(0.0F);
        parent.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);

        helper.succeedWhen(() -> {
            List<ResourceSlime> children = helper.getEntities(PFEntities.RESOURCE_SLIME.get());
            // The parent has been removed; remaining entities are split children.
            if (children.isEmpty()) {
                helper.fail("expected 2-4 ResourceSlime split children, got 0");
            }
            for (ResourceSlime child : children) {
                if (child.getCategory() != cat) {
                    helper.fail("split child has category " + child.getCategory() + ", expected " + cat);
                }
                if (child.getSize() != 1) {
                    helper.fail("split child has size " + child.getSize() + ", expected 1 (half of parent size 3 floor-divided)");
                }
            }
        });
    }

    /**
     * In-world integration check for the Slime Milker's variant pipeline.
     * Capture an iron-variant Resource Slime to a slime bucket, place the
     * milker, and verify the bucket's {@code BUCKET_ENTITY_DATA} carries the
     * variant identifier and {@code SlimeMilkerBlock.readBucketVariantId} parses
     * it back as the full id the milker stamps onto the single output bucket.
     * Parsing-edge cases (empty bucket, missing Variant tag, malformed id) are
     * covered by {@code SlimeMilkerBlockTest}; the full cook loop is covered by
     * {@code slimeMilkerBeCooksIronBucketToIronMilkAfter100Ticks}; this test
     * pins the server-side data flow that the JUnit test can't reach.
     *
     * <p>The Slime Milker block is also placed and asserted present, which
     * confirms the block is registered and its default state is valid for
     * world placement. Client-side asset resolution (blockstates, models,
     * textures) is NOT exercised — the dedicated GameTest server doesn't
     * load client resource packs, so missing or malformed asset JSON has
     * to be caught by running {@code ./gradlew runClient} manually.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void slimeMilkerConvertsIronSlimeBucketIntoIronMilkBucket(GameTestHelper helper) {
        BlockPos milkerPos = new BlockPos(2, 2, 2);
        BlockPos slimePos = new BlockPos(3, 2, 2);

        // Capture an iron-variant slime — saveToBucketTag is the same write
        // path mobInteract walks when a player right-clicks the slime with
        // an empty slime bucket. Mirrors the slime_bucket_round_trip test's
        // setup but with a registered Variant so the milker has work to do.
        ResourceSlime source = helper.spawn(PFEntities.RESOURCE_SLIME.get(), slimePos);
        source.setSize(1, true);
        ResourceLocation ironVariant = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");
        source.setVariant(ironVariant);
        if (source.getCategory() != Category.CAVE) {
            helper.fail("setVariant(iron) should sync category to CAVE (V1.5), got " + source.getCategory());
            return;
        }

        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        source.saveToBucketTag(bucket);

        // Pin the wire format the milker depends on: Variant is written as
        // the full ResourceLocation string. If saveToBucketTag is ever refactored
        // to write the bare path (or some struct shape), the milker silently
        // stops resolving variants — this assertion is the canary.
        net.minecraft.world.item.component.CustomData data =
            bucket.get(net.minecraft.core.component.DataComponents.BUCKET_ENTITY_DATA);
        if (data == null) {
            helper.fail("slime bucket BUCKET_ENTITY_DATA is null after saveToBucketTag");
            return;
        }
        String storedVariant = data.copyTag().getString("Variant");
        if (!ironVariant.toString().equals(storedVariant)) {
            helper.fail("expected Variant=" + ironVariant + " in bucket NBT, got " + storedVariant);
            return;
        }

        // The milker now stamps the input bucket's variant onto the single
        // slime_milk_bucket. Pin the lookup it walks: readBucketVariantId must
        // return the full variant id parsed from the bucket NBT.
        ResourceLocation parsed =
            com.flatts.productivefrogs.content.block.SlimeMilkerBlock.readBucketVariantId(bucket);
        if (!ironVariant.equals(parsed)) {
            helper.fail("expected readBucketVariantId=" + ironVariant + ", got " + parsed);
            return;
        }

        // Place the milker block in-world. Catches any registry boot failure
        // (e.g. the block somehow not being registered) and exercises the
        // setBlock → BlockState resolution path. We do NOT invoke useItemOn
        // here — that requires a Player, and the JUnit test covers the
        // parser surface that drives useItemOn's branching.
        helper.setBlock(milkerPos, PFBlocks.SLIME_MILKER.get());
        helper.assertBlockPresent(PFBlocks.SLIME_MILKER.get(), milkerPos);

        helper.succeed();
    }

    // ---------------------------------------------------------------------
    // Helpers for the collapsed single-fluid Slime Milk model
    // ---------------------------------------------------------------------

    /** The per-variant Slime Milk bucket for productivefrogs:&lt;variantPath&gt;. */
    private static ItemStack milkBucket(String variantPath) {
        return PFItems.slimeMilkBucket(
            ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variantPath));
    }

    /** True if {@code stack} is the per-variant Slime Milk bucket for productivefrogs:&lt;variantPath&gt;. */
    private static boolean isMilkBucket(ItemStack stack, String variantPath) {
        return stack.is(PFVariantMilk.bucket(
            ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variantPath)));
    }

    /**
     * Place the single Slime Milk source block at {@code pos} and stamp its
     * BlockEntity with productivefrogs:&lt;variantPath&gt; (what bucket placement
     * does in-world). Returns the block so tests can drive {@code tick} directly.
     */
    private static com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock placeMilkSource(
            GameTestHelper helper, BlockPos pos, String variantPath) {
        var block = PFVariantMilk.block(
            ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variantPath));
        // The per-variant block carries its variant baked in; onPlace seeds the BE
        // variant + spawn budget. We still stamp the BE defensively because some
        // callers read the BE variant synchronously right after setBlock.
        helper.setBlock(pos, block.defaultBlockState());
        stampMilkVariant(helper, pos, variantPath);
        return block;
    }

    /** Stamp an already-placed Slime Milk source block's BE with the variant. */
    private static void stampMilkVariant(GameTestHelper helper, BlockPos pos, String variantPath) {
        if (helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                instanceof com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity be) {
            be.setVariantId(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variantPath));
        }
    }

    /**
     * The source's spawn economy lives on its BlockEntity (v1.7; it used to be a
     * blockstate property). These two helpers read/write the remaining-spawn
     * counter for the depletion tests.
     */
    @org.jetbrains.annotations.Nullable
    private static com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity milkBE(
            GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                instanceof com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity be ? be : null;
    }

    private static void setMilkSpawns(GameTestHelper helper, BlockPos pos, int remaining) {
        var be = milkBE(helper, pos);
        if (be != null) {
            be.setSpawnsRemaining(remaining);
        }
    }

    private static int getMilkSpawns(GameTestHelper helper, BlockPos pos) {
        var be = milkBE(helper, pos);
        return be != null ? be.getSpawnsRemaining() : -1;
    }

    /**
     * Round-trips the variant through the single component-driven Slime Milk
     * plumbing: a stamped milk bucket's placement hook ({@code checkExtraContent})
     * writes the variant to the source block's BlockEntity, and re-bucketing
     * ({@code pickupBlock}) reads it back onto the bucket. This is the path that
     * lets a datapack variant get milk with no per-variant registration.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void slimeMilkBucketRoundTripsVariantThroughSourceBlock(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        ResourceLocation iron = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");

        // The per-variant source block is authoritative for the variant: placing
        // the iron block (its onPlace) seeds the BE with iron. checkExtraContent
        // no longer writes the variant, only catalyst/budget upgrades.
        helper.setBlock(pos, PFVariantMilk.block(iron).defaultBlockState());
        ItemStack bucket = milkBucket("iron");
        ((com.flatts.productivefrogs.content.item.SlimeMilkBucketItem) PFVariantMilk.bucket(iron))
            .checkExtraContent(null, level, bucket, abs);
        if (!(level.getBlockEntity(abs)
                instanceof com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity be)
            || !iron.equals(be.getVariantId())) {
            helper.fail("the iron source block should make the BE report variant iron");
            return;
        }

        // Re-bucket: pickupBlock returns the iron-variant filled bucket.
        ItemStack picked = PFVariantMilk.block(iron)
            .pickupBlock(null, level, abs, level.getBlockState(abs));
        if (!isMilkBucket(picked, "iron")) {
            helper.fail("pickupBlock should return an iron-stamped slime_milk_bucket, got " + picked);
            return;
        }
        helper.succeed();
    }

    /**
     * Re-bucketing a partially-depleted source preserves its spawns-remaining
     * counter through the world -> bucket -> world round-trip, so it can't be
     * refilled to full by re-bucketing (docs/known_issues.md). Place a variant
     * source at SPAWNS_REMAINING=5, re-bucket it (the bucket should carry 5),
     * then re-place via {@code checkExtraContent} and assert the restored source
     * reads 5 - not the default MAX.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void slimeMilkBucketRoundTripPreservesSpawnsRemaining(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        ResourceLocation iron = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");
        var block = PFVariantMilk.block(iron);

        // Partially-depleted iron source: 5 spawns left (counter lives on the BE).
        helper.setBlock(pos, block.defaultBlockState());
        stampMilkVariant(helper, pos, "iron");
        setMilkSpawns(helper, pos, 5);

        // Re-bucket: the filled bucket must carry the remaining count.
        ItemStack picked = block.pickupBlock(null, level, abs, level.getBlockState(abs));
        Integer carried = picked.get(com.flatts.productivefrogs.registry.PFDataComponents.SPAWNS_REMAINING.get());
        if (carried == null || carried != 5) {
            helper.fail("re-bucketing a source with 5 spawns left should stamp SPAWNS_REMAINING=5 on the bucket, got "
                + carried);
            return;
        }

        // Re-place a fresh (default) source, then run the placement hook with
        // the carried bucket: it must restore the count to 5, not leave it full.
        helper.setBlock(pos, block.defaultBlockState());
        ((com.flatts.productivefrogs.content.item.SlimeMilkBucketItem) PFVariantMilk.bucket(iron))
            .checkExtraContent(null, level, picked, abs);
        int restored = getMilkSpawns(helper, pos);
        if (restored != 5) {
            helper.fail("re-placing the carried bucket should restore the count to 5, got " + restored);
            return;
        }
        helper.succeed();
    }

    /**
     * A Primed Frog Egg schedules a <b>deterministic</b> hatch delay equal to the
     * config-exposed {@link com.flatts.productivefrogs.PFConfig#hatchTicks()}, not
     * vanilla's random {@code [3600, 12000)} window (docs/known_issues.md).
     * {@code onPlace} stamps the absolute hatch time on the BE, so we read it back
     * and assert the delay equals the fixed config value.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 20)
    public static void primedEggSchedulesDeterministicHatchDelay(GameTestHelper helper) {
        BlockPos eggPos = new BlockPos(2, 2, 2);
        helper.setBlock(eggPos.below(), Blocks.WATER);
        helper.setBlock(eggPos, PFBlocks.primedEgg(Category.CAVE));

        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(eggPos);
        if (!(level.getBlockEntity(abs)
                instanceof com.flatts.productivefrogs.content.block.entity.PrimedFrogEggBlockEntity egg)) {
            helper.fail("primed egg should have a PrimedFrogEggBlockEntity after placement");
            return;
        }
        long delay = egg.getHatchGameTime() - level.getGameTime();
        int expected = com.flatts.productivefrogs.PFConfig.hatchTicks();
        if (delay != expected) {
            helper.fail("hatch delay should be the fixed config value " + expected + " ticks, got " + delay);
            return;
        }
        helper.succeed();
    }

    /**
     * A frog matured from a non-bred (crafted / Spawnery) frogspawn starts at
     * <b>baseline</b> stats - all {@code FrogStats.STAT_MIN} (1/1/1) - rather than
     * a random starter roll. Breeding is the only path above baseline
     * (docs/known_issues.md). A tadpole with no pending stats ages up through
     * {@code finalizeSpawn -> applyBaselineStats}.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void nonBredFrogMaturesToBaselineStats(GameTestHelper helper) {
        Category cat = Category.TIDE;
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.WATER);

        ResourceTadpole tadpole = helper.spawn(PFEntities.RESOURCE_TADPOLE.get(), pos);
        tadpole.setCategory(cat);
        // No setPendingStats() call -> non-bred. ageUp() runs finalizeSpawn.
        tadpole.ageUp();

        helper.succeedWhen(() -> {
            List<ResourceFrog> frogs = helper.getEntities(PFEntities.RESOURCE_FROG.get());
            if (frogs.size() != 1) {
                helper.fail("expected 1 Resource Frog after maturation, got " + frogs.size());
                return;
            }
            ResourceFrog frog = frogs.get(0);
            int min = com.flatts.productivefrogs.content.entity.FrogStats.STAT_MIN;
            if (frog.getAppetite() != min || frog.getBounty() != min || frog.getReach() != min) {
                helper.fail("non-bred frog should be baseline " + min + "/" + min + "/" + min + ", got "
                    + frog.getAppetite() + "/" + frog.getBounty() + "/" + frog.getReach());
            }
        });
    }

    // ---------------------------------------------------------------------
    // J4 — Slime Milk source-block spawning
    // ---------------------------------------------------------------------

    /**
     * Happy path: an iron_slime_milk source block with a solid neighbour
     * directly east should spawn one size-1 iron ResourceSlime on top of
     * that neighbour when its tick fires.
     *
     * <p>Tick is invoked directly on the concrete block reference (same
     * pattern as {@code primedEggHatchesIntoMatchingCategoryTadpoles}) so we
     * don't sit through the 200–600-tick scheduled delay. The block widens
     * {@code tick} to public specifically to enable this.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void slimeMilkSourceSpawnsIronResourceSlimeOnSolidNeighbour(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        BlockPos neighbourPos = sourcePos.east();
        BlockPos expectedSpawnPos = neighbourPos.above();

        // Stone east of the source provides the solid landing pad. Other
        // neighbours stay air so the candidate iteration short-circuits on
        // the first hit and the spawn lands deterministically east-up.
        helper.setBlock(neighbourPos, Blocks.STONE);

        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            placeMilkSource(helper, sourcePos, "iron");
        ServerLevel level = helper.getLevel();
        BlockPos absSourcePos = helper.absolutePos(sourcePos);
        block.tick(level.getBlockState(absSourcePos), level, absSourcePos, level.getRandom());

        helper.succeedWhen(() -> {
            List<ResourceSlime> slimes = helper.getEntities(PFEntities.RESOURCE_SLIME.get());
            if (slimes.size() != 1) {
                helper.fail("expected exactly 1 ResourceSlime, got " + slimes.size());
                return;
            }
            ResourceSlime slime = slimes.get(0);
            if (slime.getSize() != 1) {
                helper.fail("spawned slime has size " + slime.getSize() + ", expected 1");
            }
            ResourceLocation expectedVariant = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");
            if (!expectedVariant.equals(slime.getVariantId())) {
                helper.fail("spawned slime has variant " + slime.getVariantId() + ", expected " + expectedVariant);
            }
            // Position check: the slime should land on top of the stone
            // neighbour. Floor X and Z only — we deliberately skip the Y
            // assertion because gravity + bobbing physics move the slime
            // down within the polling window, which would make Y flaky.
            BlockPos absExpected = helper.absolutePos(expectedSpawnPos);
            int sx = net.minecraft.util.Mth.floor(slime.getX());
            int sz = net.minecraft.util.Mth.floor(slime.getZ());
            if (sx != absExpected.getX() || sz != absExpected.getZ()) {
                helper.fail("spawned slime at (" + sx + ", " + sz + "), expected ("
                    + absExpected.getX() + ", " + absExpected.getZ() + ")");
            }
        });
    }

    /**
     * No solid neighbour anywhere in the 3×3×3 cube around the source —
     * spawn picker falls back to the source's own position so the slime
     * appears inside the milk fluid. The milk block has noCollision, so
     * spawning the entity there always succeeds.
     *
     * <p>This pins the fallback branch in {@code chooseSpawnPos}: every
     * candidate in {@code NEIGHBOUR_OFFSETS} fails the sturdy check, the
     * loop exits, and the source position is returned.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void slimeMilkSourceFallsBackToLiquidWhenNoSolidNeighbour(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);

        // No neighbour blocks set — the empty 5x5x5 test plot is pure air
        // everywhere except the source itself. Every NEIGHBOUR_OFFSETS entry
        // points to an air block (not sturdy), so the picker exhausts the
        // list and returns source.
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            placeMilkSource(helper, sourcePos, "copper");
        ServerLevel level = helper.getLevel();
        BlockPos absSourcePos = helper.absolutePos(sourcePos);
        block.tick(level.getBlockState(absSourcePos), level, absSourcePos, level.getRandom());

        helper.succeedWhen(() -> {
            List<ResourceSlime> slimes = helper.getEntities(PFEntities.RESOURCE_SLIME.get());
            if (slimes.size() != 1) {
                helper.fail("expected exactly 1 ResourceSlime from liquid fallback, got " + slimes.size());
                return;
            }
            ResourceSlime slime = slimes.get(0);
            ResourceLocation expectedVariant = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "copper");
            if (!expectedVariant.equals(slime.getVariantId())) {
                helper.fail("spawned slime has variant " + slime.getVariantId() + ", expected " + expectedVariant);
            }
            // Position assertion: the fallback puts the slime AT the source
            // position. Floor X and Z only — Y is deliberately not checked
            // because gravity may settle the slime within the polling
            // window before the assertion runs.
            int sx = net.minecraft.util.Mth.floor(slime.getX());
            int sz = net.minecraft.util.Mth.floor(slime.getZ());
            if (sx != absSourcePos.getX() || sz != absSourcePos.getZ()) {
                helper.fail("liquid-fallback spawn at (" + sx + ", " + sz + "), expected ("
                    + absSourcePos.getX() + ", " + absSourcePos.getZ() + ")");
            }
        });
    }

    // (removed in v1.8: vanilla/magma sentinel milk sources have no per-variant fluid; see docs/automated_milk_variants.md)

    /**
     * Block every horizontal neighbour at y=0 with non-sturdy blocks (no
     * rim candidate) but put a solid block directly below the source. The
     * picker should iterate past the y=0 plane and pick the y=-1 center
     * neighbour, returning {@code source} as the spawn pos (i.e. the slime
     * spawns at the source's coordinates, inside the milk).
     *
     * <p>This exercises the deeper iteration order — same-y plane fails
     * first, then y=-1 plane gets considered. Pins the algorithm's
     * "rim-first" priority without conflating it with the no-solid-anywhere
     * fallback (which is the previous test).
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void slimeMilkSourcePicksSolidNeighbourBelowWhenNoHorizontalNeighbour(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);

        // Solid floor directly beneath the source. The y=-1 center neighbour
        // is sturdy; its .above() = source itself is non-blocking (milk has
        // noCollision). Picker returns source.
        helper.setBlock(sourcePos.below(), Blocks.STONE);

        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            placeMilkSource(helper, sourcePos, "gold");
        ServerLevel level = helper.getLevel();
        BlockPos absSourcePos = helper.absolutePos(sourcePos);
        block.tick(level.getBlockState(absSourcePos), level, absSourcePos, level.getRandom());

        helper.succeedWhen(() -> {
            List<ResourceSlime> slimes = helper.getEntities(PFEntities.RESOURCE_SLIME.get());
            if (slimes.size() != 1) {
                helper.fail("expected exactly 1 ResourceSlime from below-floor pick, got " + slimes.size());
                return;
            }
            // Slime should land at source XZ (because the picked neighbour
            // is directly below the source).
            ResourceSlime slime = slimes.get(0);
            int sx = net.minecraft.util.Mth.floor(slime.getX());
            int sz = net.minecraft.util.Mth.floor(slime.getZ());
            if (sx != absSourcePos.getX() || sz != absSourcePos.getZ()) {
                helper.fail("expected spawn at source XZ (" + absSourcePos.getX() + ", " + absSourcePos.getZ()
                    + "), got (" + sx + ", " + sz + ")");
            }
        });
    }

    // ---------------------------------------------------------------------
    // J5 — Depletion counter
    // ---------------------------------------------------------------------

    /**
     * Place an iron milk source with the depletion counter explicitly set to
     * 5 (mid-life). Tick once and assert the resulting state's counter is
     * 4 — confirms the decrement edge of the tick loop runs exactly once
     * per successful spawn.
     *
     * <p>Forces {@code depletionEnabledOverride=true} so the test result is
     * stable regardless of whether the developer has flipped
     * {@code depletionEnabled} off in their local
     * {@code productivefrogs-common.toml}.
     */
    /**
     * Slime Bucket release: emptying a Slime Bucket must NOT place a water source
     * (a captured slime is a land mob, not a fish), and the released slime is always
     * size 1 (capture is gated to size 1; MobBucketItem#spawn would otherwise let
     * Slime#finalizeSpawn randomize the size to 1/2/4).
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 40)
    public static void slimeBucketReleaseHasNoWaterAndIsSizeOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 2, 2);
        BlockPos abs = helper.absolutePos(pos);
        helper.setBlock(pos, Blocks.AIR);

        // 1) Emptying the bucket places no fluid.
        var bucketItem = (com.flatts.productivefrogs.content.item.SlimeBucketItem) PFItems.SLIME_BUCKET.get();
        bucketItem.emptyContents(null, level, abs, null);
        if (!level.getFluidState(abs).isEmpty()) {
            helper.fail("Slime Bucket release placed a fluid at " + pos + " (expected none)");
            return;
        }
        if (!level.getBlockState(abs).isAir()) {
            helper.fail("Slime Bucket release changed the block to "
                + level.getBlockState(abs) + " (expected air)");
            return;
        }

        // 2) loadFromBucketTag forces size 1 even after a larger finalizeSpawn size.
        var slime = PFEntities.RESOURCE_SLIME.get().create(level);
        if (slime == null) {
            helper.fail("could not create ResourceSlime");
            return;
        }
        slime.setSize(4, true);
        slime.loadFromBucketTag(new net.minecraft.nbt.CompoundTag());
        int size = slime.getSize();
        slime.discard();
        if (size != 1) {
            helper.fail("released slime size expected 1, got " + size);
            return;
        }
        helper.succeed();
    }

    /**
     * A dispenser loaded with a Slime Bucket releases the slime (size 1, no water)
     * into the block it faces, rather than just ejecting the bucket. Powers a
     * dispenser facing up with a redstone block and checks the air block above.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 60)
    public static void slimeBucketDispenserReleasesSlimeNoWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos disp = new BlockPos(2, 2, 2);
        BlockPos front = disp.above();
        helper.setBlock(disp, net.minecraft.world.level.block.Blocks.DISPENSER.defaultBlockState()
            .setValue(net.minecraft.world.level.block.DispenserBlock.FACING, net.minecraft.core.Direction.UP));
        if (!(helper.getBlockEntity(disp)
                instanceof net.minecraft.world.level.block.entity.DispenserBlockEntity dbe)) {
            helper.fail("dispenser BE missing");
            return;
        }
        dbe.setItem(0, PFItems.variantSlimeBucket(
            ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"),
            com.flatts.productivefrogs.data.Category.CAVE));
        // Rising redstone edge triggers the dispense (fires ~4 ticks later).
        helper.setBlock(disp.east(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(15, () -> {
            if (!level.getFluidState(helper.absolutePos(front)).isEmpty()) {
                helper.fail("dispenser release placed water in front of the dispenser");
                return;
            }
            var slimes = helper.getEntities(PFEntities.RESOURCE_SLIME.get());
            if (slimes.size() != 1) {
                helper.fail("expected 1 slime dispensed, got " + slimes.size());
                return;
            }
            if (slimes.get(0).getSize() != 1) {
                helper.fail("dispensed slime not size 1, got " + slimes.get(0).getSize());
                return;
            }
            helper.succeed();
        });
    }

    /**
     * A Coal Froglight burns as furnace fuel like vanilla coal: place a furnace,
     * load a coal-variant Froglight into the fuel slot and cobblestone into the
     * input, and the furnace lights and smelts the cobblestone to stone. Proves
     * the per-stack {@code getBurnTime} override is wired into vanilla smelting
     * end-to-end (only the Froglight could have fueled the burn - the fuel slot
     * held nothing else). Smelting one item takes 200 ticks; allow headroom.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 300)
    public static void coalFroglightFuelsFurnace(GameTestHelper helper) {
        BlockPos furnacePos = new BlockPos(2, 2, 2);
        helper.setBlock(furnacePos, Blocks.FURNACE);
        if (!(helper.getBlockEntity(furnacePos)
                instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity furnace)) {
            helper.fail("furnace BE missing");
            return;
        }
        ItemStack coalFroglight = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
        coalFroglight.set(com.flatts.productivefrogs.registry.PFDataComponents.SLIME_VARIANT.get(),
            ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "coal"));
        furnace.setItem(0, new ItemStack(Items.COBBLESTONE)); // input
        furnace.setItem(1, coalFroglight);                   // fuel

        helper.succeedWhen(() -> helper.assertTrue(furnace.getItem(2).is(Items.STONE),
            "furnace should smelt cobblestone to stone using the Coal Froglight as fuel, got "
                + furnace.getItem(2)));
    }

    /**
     * Density cap (v1.8): a source pauses spawning when its own species already
     * crowds the area, and crucially does NOT spend its remaining-spawn budget
     * while paused. Uses {@code spawnCapOverride=2} so the test needs only 2 slimes
     * instead of the default 30.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void slimeMilkSourcePausesWhenAreaIsCrowded(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        helper.setBlock(sourcePos.east(), Blocks.STONE);
        var block = PFVariantMilk.block(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        helper.setBlock(sourcePos, block);
        stampMilkVariant(helper, sourcePos, "iron");
        setMilkSpawns(helper, sourcePos, 5);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(sourcePos);

        Boolean depOrig = com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride;
        Integer capOrig = com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.spawnCapOverride;
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = true;
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.spawnCapOverride = 2;
        try {
            // Seed the area with 2 iron slimes (the overridden cap) so the next tick is over-cap.
            for (int i = 0; i < 2; i++) {
                var slime = PFEntities.RESOURCE_SLIME.get().create(level);
                if (slime == null) {
                    helper.fail("could not create ResourceSlime for the cap test");
                    return;
                }
                slime.setVariant(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
                slime.setSize(1, true);
                slime.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0F, 0F);
                level.addFreshEntity(slime);
            }
            int before = getMilkSpawns(helper, sourcePos);
            block.tick(level.getBlockState(abs), level, abs, level.getRandom());

            int after = getMilkSpawns(helper, sourcePos);
            if (after != before) {
                helper.fail("capped source must NOT spend its budget while paused; before="
                    + before + " after=" + after);
                return;
            }
            int count = helper.getEntities(PFEntities.RESOURCE_SLIME.get()).size();
            if (count != 2) {
                helper.fail("capped source must not spawn beyond the cap; expected 2 slimes, got " + count);
                return;
            }
            helper.succeed();
        } finally {
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = depOrig;
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.spawnCapOverride = capOrig;
        }
    }

    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void slimeMilkSourceDecrementsSpawnsRemainingEachSpawn(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        helper.setBlock(sourcePos.east(), Blocks.STONE);

        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            PFVariantMilk.block(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        helper.setBlock(sourcePos, block);
        stampMilkVariant(helper, sourcePos, "iron");
        setMilkSpawns(helper, sourcePos, 5);
        ServerLevel level = helper.getLevel();
        BlockPos absSourcePos = helper.absolutePos(sourcePos);

        Boolean originalOverride =
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride;
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = true;
        try {
            block.tick(level.getBlockState(absSourcePos), level, absSourcePos, level.getRandom());

            int afterCount = getMilkSpawns(helper, sourcePos);
            if (afterCount != 4) {
                helper.fail("expected SPAWNS_REMAINING=4 after one tick (started at 5), got " + afterCount);
                return;
            }
            // Sanity: the spawn itself still happened.
            if (helper.getEntities(PFEntities.RESOURCE_SLIME.get()).size() != 1) {
                helper.fail("expected 1 ResourceSlime to spawn during the decrementing tick");
                return;
            }
            helper.succeed();
        } finally {
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = originalOverride;
        }
    }

    /**
     * Place an iron milk source with SPAWNS_REMAINING=0 (counter exhausted)
     * and tick it. The tick must replace the block with air rather than
     * spawning a slime — this is the drain path. Also asserts no slime
     * appears, since the drain branch returns before {@code spawn()}.
     *
     * <p>Forces {@code depletionEnabledOverride=true} so a developer who
     * has flipped {@code depletionEnabled} off in their local config can
     * still run this suite.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void slimeMilkSourceDrainsWhenSpawnsRemainingReachesZero(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        helper.setBlock(sourcePos.east(), Blocks.STONE);

        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            PFVariantMilk.block(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        helper.setBlock(sourcePos, block);
        stampMilkVariant(helper, sourcePos, "iron");
        setMilkSpawns(helper, sourcePos, 0);
        ServerLevel level = helper.getLevel();
        BlockPos absSourcePos = helper.absolutePos(sourcePos);

        Boolean originalOverride =
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride;
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = true;
        try {
            block.tick(level.getBlockState(absSourcePos), level, absSourcePos, level.getRandom());

            // Block should be gone — the drain branch calls
            // level.setBlock(pos, AIR) explicitly (not removeBlock, which
            // would round-trip the fluid back to a default-state source).
            BlockState after = level.getBlockState(absSourcePos);
            if (!after.isAir()) {
                helper.fail("expected drained source pos to be air, got " + after);
                return;
            }
            // Drain branch returns early — no slime should have spawned.
            if (!helper.getEntities(PFEntities.RESOURCE_SLIME.get()).isEmpty()) {
                helper.fail("drain tick must NOT produce a slime, but one appeared");
                return;
            }
            helper.succeed();
        } finally {
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = originalOverride;
        }
    }

    /**
     * Regression: the final spawn and the drain happen in the SAME tick, so the
     * source never lingers at {@code SPAWNS_REMAINING=0}. Set the counter to 1 and
     * tick once: the source must spawn its one slime AND drain to air in that tick
     * (not reschedule and drain a full interval later). Before the fix the counter
     * hit 0 while the block stayed standing for one more spawn interval, which read
     * as an off-by-one (Jade said 0 yet the source was still there about to drain).
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void slimeMilkSourceDrainsSameTickAsFinalSpawn(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        helper.setBlock(sourcePos.east(), Blocks.STONE);

        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            PFVariantMilk.block(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        helper.setBlock(sourcePos, block);
        stampMilkVariant(helper, sourcePos, "iron");
        setMilkSpawns(helper, sourcePos, 1);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(sourcePos);

        Boolean originalOverride =
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride;
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = true;
        try {
            block.tick(level.getBlockState(abs), level, abs, level.getRandom());

            // One slime spawned this tick.
            if (helper.getEntities(PFEntities.RESOURCE_SLIME.get()).size() != 1) {
                helper.fail("expected exactly 1 ResourceSlime from the final spawn");
                return;
            }
            // ...and the source drained in the SAME tick, not a later one.
            BlockState after = level.getBlockState(abs);
            if (!after.isAir()) {
                helper.fail("source with 1 spawn left must drain in the same tick as its "
                    + "final spawn, but the block still stands: " + after);
                return;
            }
            helper.succeed();
        } finally {
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = originalOverride;
        }
    }

    /**
     * Sanity check: a freshly-placed source, once its variant is set, seeds the
     * BlockEntity's remaining-spawn counter to the configured default
     * ({@code DEPLETION_COUNT}, default 16). The counter moved from a blockstate
     * property to the BE in v1.7 (so Count catalysts can raise it without bound);
     * this pins the {@code setVariantId -> seedIfUnset} path so a fresh placement
     * starts with a full budget rather than 0 (which would drain on first tick).
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void slimeMilkSourceSeedsDefaultSpawnCountOnPlacement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            PFVariantMilk.block(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        helper.setBlock(pos, block);
        stampMilkVariant(helper, pos, "iron");
        int expected = com.flatts.productivefrogs.PFConfig.SPEC.isLoaded()
            ? com.flatts.productivefrogs.PFConfig.DEPLETION_COUNT.get()
            : com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.MAX_SPAWNS_REMAINING;
        int seeded = getMilkSpawns(helper, pos);
        if (seeded != expected) {
            helper.fail("a fresh variant-stamped source should seed " + expected + " spawns, got " + seeded);
            return;
        }
        helper.succeed();
    }

    /**
     * Pin the "BlockEntity survives a same-block state change" invariant across
     * multiple depletion ticks. The decrement path uses {@code setBlock} with the
     * same block + a lower SPAWNS_REMAINING, which must keep the BE (and its
     * variant) alive. Set the counter to 2, tick three times, and assert the
     * source spawned exactly 2 iron slimes (variant intact each tick) then
     * drained to air. A regression that dropped the BE on state change would
     * make the 2nd spawn lose its variant (or fall back to a vanilla slime).
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void slimeMilkSourceKeepsVariantAcrossDepletionTicks(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        helper.setBlock(sourcePos.east(), Blocks.STONE);
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            PFVariantMilk.block(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        helper.setBlock(sourcePos, block);
        stampMilkVariant(helper, sourcePos, "iron");
        setMilkSpawns(helper, sourcePos, 2);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(sourcePos);
        ResourceLocation iron = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");

        Boolean original =
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride;
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = true;
        try {
            // Ticks are invoked manually (scheduled reschedules do not auto-fire
            // here): counter 2 -> 1 (tick 1 spawns), then 1 -> 0 (tick 2 spawns
            // AND drains in the same tick), so the block is air by tick 3. Exactly
            // 2 spawns either way; the 3rd tick is a no-op on the drained air block.
            block.tick(level.getBlockState(abs), level, abs, level.getRandom());
            block.tick(level.getBlockState(abs), level, abs, level.getRandom());
            block.tick(level.getBlockState(abs), level, abs, level.getRandom());

            if (!level.getBlockState(abs).isAir()) {
                helper.fail("source should drain to air after its 2 spawns, got " + level.getBlockState(abs));
                return;
            }
            List<ResourceSlime> slimes = helper.getEntities(PFEntities.RESOURCE_SLIME.get());
            if (slimes.size() != 2) {
                helper.fail("expected 2 ResourceSlimes from 2 depletion ticks, got " + slimes.size());
                return;
            }
            for (ResourceSlime s : slimes) {
                if (!iron.equals(s.getVariantId())) {
                    helper.fail("a depletion-spawned slime lost its variant (BE not preserved): "
                        + s.getVariantId());
                    return;
                }
            }
            helper.succeed();
        } finally {
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = original;
        }
    }

    // ---------------------------------------------------------------------
    // Slime Milk catalysts (docs/slime_milk_catalysts.md)
    // ---------------------------------------------------------------------

    /**
     * De-risk the {@code entityInside} hook: a Count catalyst dropped INTO a real
     * milk source pool is consumed and raises the source's remaining-spawn count,
     * with no manual call - the item ticks naturally while overlapping the fluid
     * block. If {@code entityInside} does not fire for a pooled item this test
     * fails, flagging the need for the per-tick AABB-scan fallback.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 60)
    public static void catalystDroppedInPoolIsConsumed(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            PFVariantMilk.block(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        helper.setBlock(sourcePos, block);
        stampMilkVariant(helper, sourcePos, "iron");
        setMilkSpawns(helper, sourcePos, 4);
        int baseline = getMilkSpawns(helper, sourcePos);

        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(sourcePos);
        net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(
            level, abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5,
            new ItemStack(PFItems.COUNT_CATALYST.get()));
        item.setNoGravity(true);
        item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        level.addFreshEntity(item);

        helper.succeedWhen(() -> helper.assertTrue(getMilkSpawns(helper, sourcePos) > baseline,
            "a Count catalyst in the pool should raise remaining spawns (entityInside must fire for pooled items)"));
    }

    /**
     * An infinite source (Infinite Count catalyst) never drains: even with the
     * remaining counter set to 1 and depletion forced on, repeated ticks keep
     * spawning without draining the block to air.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void catalystInfiniteSourceNeverDrains(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        helper.setBlock(sourcePos.east(), Blocks.STONE);
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            PFVariantMilk.block(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        helper.setBlock(sourcePos, block);
        stampMilkVariant(helper, sourcePos, "iron");
        var be = milkBE(helper, sourcePos);
        if (be == null) {
            helper.fail("source BE missing after placement");
            return;
        }
        be.applyCatalyst(com.flatts.productivefrogs.content.item.MilkCatalyst.INFINITE);
        be.setSpawnsRemaining(1);

        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(sourcePos);
        Boolean original =
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride;
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = true;
        try {
            for (int i = 0; i < 5; i++) {
                block.tick(level.getBlockState(abs), level, abs, level.getRandom());
            }
            if (level.getBlockState(abs).isAir()) {
                helper.fail("an infinite source must not drain to air");
                return;
            }
            var beAfter = milkBE(helper, sourcePos);
            if (beAfter == null || beAfter.getSpawnsRemaining() != 1) {
                helper.fail("an infinite source must not decrement its counter, got "
                    + (beAfter == null ? "no BE" : beAfter.getSpawnsRemaining()));
                return;
            }
            helper.succeed();
        } finally {
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = original;
        }
    }

    /**
     * A catalyst that would no-op (here: a Speed catalyst on an already-maxed
     * source) is left unconsumed - the item floats for the player to retrieve
     * rather than being silently eaten. Pins the {@code applyCatalyst -> false ->
     * don't shrink} contract through the real {@code entityInside} path.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 60)
    public static void catalystAtCapIsNotConsumed(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            PFVariantMilk.block(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        helper.setBlock(sourcePos, block);
        stampMilkVariant(helper, sourcePos, "iron");
        var be = milkBE(helper, sourcePos);
        if (be == null) {
            helper.fail("source BE missing after placement");
            return;
        }
        // Max out speed so a further Speed catalyst is a no-op.
        while (be.applyCatalyst(com.flatts.productivefrogs.content.item.MilkCatalyst.SPEED)) {
            // saturate
        }
        int maxedSpeed = be.getSpeedLevel();

        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(sourcePos);
        net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(
            level, abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5,
            new ItemStack(PFItems.SPEED_CATALYST.get()));
        item.setNoGravity(true);
        item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        level.addFreshEntity(item);

        helper.runAfterDelay(20L, () -> {
            if (item.isRemoved() || item.getItem().getCount() != 1) {
                helper.fail("a maxed-out Speed catalyst must not be consumed");
                return;
            }
            var beNow = milkBE(helper, sourcePos);
            if (beNow == null || beNow.getSpeedLevel() != maxedSpeed) {
                helper.fail("speed level should stay at the cap");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * The full upgrade set (speed + quantity + infinite + remaining count) survives
     * the world -> bucket -> world round-trip: re-bucketing a buffed source stamps
     * the components, and re-placing the bucket restores them onto the new source.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void catalystUpgradesSurviveBucketRoundTrip(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            PFVariantMilk.block(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        helper.setBlock(pos, block);
        stampMilkVariant(helper, pos, "iron");
        var be = milkBE(helper, pos);
        if (be == null) {
            helper.fail("source BE missing after placement");
            return;
        }
        be.applyCatalyst(com.flatts.productivefrogs.content.item.MilkCatalyst.SPEED);
        be.applyCatalyst(com.flatts.productivefrogs.content.item.MilkCatalyst.QUANTITY);
        be.applyCatalyst(com.flatts.productivefrogs.content.item.MilkCatalyst.QUANTITY);
        be.applyCatalyst(com.flatts.productivefrogs.content.item.MilkCatalyst.INFINITE);
        int speed = be.getSpeedLevel();
        int quantity = be.getQuantityLevel();

        ItemStack picked = block.pickupBlock(null, level, abs, level.getBlockState(abs));
        Integer cSpeed = picked.get(com.flatts.productivefrogs.registry.PFDataComponents.MILK_SPEED.get());
        Integer cQuantity = picked.get(com.flatts.productivefrogs.registry.PFDataComponents.MILK_QUANTITY.get());
        Boolean cInfinite = picked.get(com.flatts.productivefrogs.registry.PFDataComponents.MILK_INFINITE.get());
        if (cSpeed == null || cSpeed != speed || cQuantity == null || cQuantity != quantity
                || !Boolean.TRUE.equals(cInfinite)) {
            helper.fail("re-bucketing must stamp speed=" + speed + " quantity=" + quantity
                + " infinite=true, got speed=" + cSpeed + " quantity=" + cQuantity + " infinite=" + cInfinite);
            return;
        }

        // Re-place a fresh source, run the placement hook, and confirm restore.
        helper.setBlock(pos, block);
        ((com.flatts.productivefrogs.content.item.SlimeMilkBucketItem)
                PFVariantMilk.bucket(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron")))
            .checkExtraContent(null, level, picked, abs);
        var be2 = milkBE(helper, pos);
        if (be2 == null || be2.getSpeedLevel() != speed || be2.getQuantityLevel() != quantity || !be2.isInfinite()) {
            helper.fail("re-placing the bucket must restore the upgrades, got "
                + (be2 == null ? "no BE"
                    : "speed=" + be2.getSpeedLevel() + " quantity=" + be2.getQuantityLevel()
                        + " infinite=" + be2.isInfinite()));
            return;
        }
        helper.succeed();
    }

    /**
     * Regression pin for the 1.21.11 slime sizing migration. Vanilla
     * {@code EntityType.SLIME} uses {@code sized(0.52F, 0.52F)} +
     * {@code Slime#getDefaultDimensions(...).scale(getSize())} — the older
     * {@code sized(2.04F, 2.04F)} base with the historical {@code 0.255*size}
     * internal multiplier no longer holds, so reusing the old base value
     * produces hitboxes ~4× too large at every size. Caught in playtest;
     * this test stops it from happening again.
     *
     * <p>Spawn one of every custom slime species at size 1, snapshot
     * {@code getBbWidth()}, and assert it matches vanilla {@code Slime} at
     * size 1 within a tight tolerance (floating-point arithmetic on the
     * scale chain). If any custom slime diverges, the size of the base
     * dimensions in {@code PFEntities} drifted from vanilla.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void customSlimesSize1HitboxMatchesVanillaSlime(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);

        net.minecraft.world.entity.monster.Slime vanilla =
            helper.spawn(net.minecraft.world.entity.EntityType.SLIME, pos);
        vanilla.setSize(1, true);
        float expectedWidth = vanilla.getBbWidth();
        float expectedHeight = vanilla.getBbHeight();

        assertSize1HitboxMatches(helper, PFEntities.RESOURCE_SLIME.get(), "ResourceSlime", expectedWidth, expectedHeight);
        assertSize1HitboxMatches(helper, PFEntities.CAVE_SLIME.get(), "CaveSlime", expectedWidth, expectedHeight);
        assertSize1HitboxMatches(helper, PFEntities.GEODE_SLIME.get(), "GeodeSlime", expectedWidth, expectedHeight);
        assertSize1HitboxMatches(helper, PFEntities.TIDE_SLIME.get(), "TideSlime", expectedWidth, expectedHeight);
        assertSize1HitboxMatches(helper, PFEntities.VOID_SLIME.get(), "VoidSlime", expectedWidth, expectedHeight);

        helper.succeed();
    }

    private static <T extends net.minecraft.world.entity.monster.Slime> void assertSize1HitboxMatches(
            GameTestHelper helper,
            net.minecraft.world.entity.EntityType<T> type,
            String name,
            float expectedWidth,
            float expectedHeight) {
        BlockPos pos = new BlockPos(3, 2, 3);
        T slime = helper.spawn(type, pos);
        slime.setSize(1, true);
        float w = slime.getBbWidth();
        float h = slime.getBbHeight();
        // Tolerance handles the float-scale chain (base × size). 0.001 is
        // tight enough to catch any 4× regression while staying ahead of
        // legitimate float rounding.
        if (Math.abs(w - expectedWidth) > 0.001f) {
            helper.fail(name + " size-1 width " + w + " != vanilla " + expectedWidth);
        }
        if (Math.abs(h - expectedHeight) > 0.001f) {
            helper.fail(name + " size-1 height " + h + " != vanilla " + expectedHeight);
        }
    }

    // ---------------------------------------------------------------------
    // Smelting recipes
    // ---------------------------------------------------------------------

    /**
     * configurable_froglight stamped with a {@code slime_variant} component
     * should smelt to the variant's canonical resource. Pins the 12
     * component-ingredient smelting recipes against the
     * {@code neoforge:components} ingredient codec — if NeoForge ever
     * renames the discriminator (e.g. {@code neoforge:data_components}),
     * the recipes silently stop matching and this test catches it.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void variantConfigurableFroglightSmeltRecipesResolvePerVariant(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.item.crafting.RecipeManager rm = level.getServer().getRecipeManager();

        // Full coverage — all 12 variants. The component-ingredient pipeline
        // is the load-bearing piece here (one bad codec name and every
        // variant silently stops matching), so it's cheap insurance to
        // exercise the whole roster rather than rely on a sample.
        assertVariantSmelts(helper, rm, level, "iron", net.minecraft.world.item.Items.IRON_INGOT);
        assertVariantSmelts(helper, rm, level, "copper", net.minecraft.world.item.Items.COPPER_INGOT);
        assertVariantSmelts(helper, rm, level, "gold", net.minecraft.world.item.Items.GOLD_INGOT);
        assertVariantSmelts(helper, rm, level, "redstone", net.minecraft.world.item.Items.REDSTONE);
        assertVariantSmelts(helper, rm, level, "lapis", net.minecraft.world.item.Items.LAPIS_LAZULI);
        assertVariantSmelts(helper, rm, level, "coal", net.minecraft.world.item.Items.COAL);
        assertVariantSmelts(helper, rm, level, "diamond", net.minecraft.world.item.Items.DIAMOND);
        assertVariantSmelts(helper, rm, level, "emerald", net.minecraft.world.item.Items.EMERALD);
        assertVariantSmelts(helper, rm, level, "prismarine", net.minecraft.world.item.Items.PRISMARINE_SHARD);
        assertVariantSmelts(helper, rm, level, "sponge", net.minecraft.world.item.Items.SPONGE);
        assertVariantSmelts(helper, rm, level, "ender_pearl", net.minecraft.world.item.Items.ENDER_PEARL);
        // blaze's resource moved powder -> rod in #148 (primer == smelt-output
        // holds; the rod is the actual mob drop and the v1.8.3 fuel equivalent).
        assertVariantSmelts(helper, rm, level, "blaze", net.minecraft.world.item.Items.BLAZE_ROD);

        helper.succeed();
    }

    /**
     * Negative: a configurable_froglight stack with no slime_variant
     * component should not match any of the 12 variant smelting recipes —
     * each recipe's ingredient is strict on the component. This pins the
     * "fail closed" semantic: no variant → no recipe → no spurious smelt
     * output. Without this, a refactor that loosens the ingredient's
     * strict flag would silently let bare configurable_froglights smelt
     * to whatever variant happens to sort first.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void configurableFroglightWithoutVariantDoesNotSmelt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.item.crafting.RecipeManager rm = level.getServer().getRecipeManager();

        ItemStack stack = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
        net.minecraft.world.item.crafting.SingleRecipeInput input =
            new net.minecraft.world.item.crafting.SingleRecipeInput(stack);
        java.util.Optional<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.SmeltingRecipe>> match =
            rm.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, input, level);
        if (match.isPresent()) {
            helper.fail("configurable_froglight without slime_variant component must NOT match any smelt recipe, but matched "
                + match.get().id() + " → " + match.get().value().assemble(input, level.registryAccess()));
            return;
        }
        helper.succeed();
    }

    /**
     * Round-trip: a variant-stamped configurable_froglight ItemStack placed
     * as a block, then broken, must produce a dropped ItemStack carrying the
     * same {@code SLIME_VARIANT} component. Pins three load-bearing pieces
     * of the placement/break loop together:
     *
     * <ol>
     *   <li>{@link com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity#setVariantId}
     *       persists the variant onto the BE.</li>
     *   <li>{@link com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity#collectImplicitComponents}
     *       exposes that variant as an implicit data component on the BE.</li>
     *   <li>The loot table at
     *       {@code data/productivefrogs/loot_table/blocks/configurable_froglight.json}
     *       uses {@code minecraft:copy_components} with source
     *       {@code block_entity} to copy that component onto the dropped item.</li>
     * </ol>
     *
     * <p>If any of those three pieces silently breaks, a variant-stamped
     * Iron Froglight would survive the placement but the broken-block drop
     * would lose the iron variant — i.e. the player would pick up an
     * unstamped configurable_froglight, lose smelt-recipe matching, and
     * regress to exactly the bug this PR fixes. This test fails closed in
     * that scenario.
     *
     * <p>The test exercises the post-placement path directly (set block +
     * call BE setter) rather than the BlockItem.useOn path. The two are
     * equivalent for our purposes — {@code ConfigurableFroglightItem.updateCustomBlockEntityTag}
     * is a thin wrapper that resolves the BE and calls the same setter —
     * and avoiding the UseOnContext / mock-player ceremony keeps the test
     * focused on the placement→drop invariant.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void variantFroglightRoundTripPreservesVariantThroughPlaceAndBreak(GameTestHelper helper) {
        BlockPos blockPos = new BlockPos(2, 2, 2);
        ResourceLocation ironVariant = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");

        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(blockPos);

        // Mirror the placement code path: set the block, then write the
        // variant onto the BE (this is what ConfigurableFroglightItem's
        // updateCustomBlockEntityTag override does after vanilla seats the BE).
        level.setBlock(absPos,
            PFBlocks.CONFIGURABLE_FROGLIGHT.get().defaultBlockState(), 3);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(absPos);
        if (!(be instanceof com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity froglightBe)) {
            helper.fail("expected ConfigurableFroglightBlockEntity at " + blockPos
                + ", got " + (be == null ? "null" : be.getClass().getSimpleName()));
            return;
        }
        froglightBe.setVariantId(ironVariant);

        // Run the loot table. The 4-arg dropResources overload passes the
        // BE through LootContextParams.BLOCK_ENTITY, which copy_components
        // (source=block_entity) reads via BlockEntity.collectComponents().
        BlockState state = level.getBlockState(absPos);
        net.minecraft.world.level.block.Block.dropResources(state, level, absPos, froglightBe);

        helper.succeedWhen(() -> {
            net.minecraft.world.item.Item expected = PFItems.CONFIGURABLE_FROGLIGHT.get();
            net.minecraft.world.entity.item.ItemEntity match =
                helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                    .filter(e -> e.getItem().is(expected))
                    .findFirst()
                    .orElse(null);
            if (match == null) {
                helper.fail("expected configurable_froglight to drop after break");
                return;
            }
            ResourceLocation droppedVariant = match.getItem().get(
                com.flatts.productivefrogs.registry.PFDataComponents.SLIME_VARIANT.get());
            if (!ironVariant.equals(droppedVariant)) {
                helper.fail("dropped variant=" + droppedVariant + ", expected " + ironVariant);
            }
        });
    }

    private static void assertSmelts(
            GameTestHelper helper,
            net.minecraft.world.item.crafting.RecipeManager rm,
            ServerLevel level,
            net.minecraft.world.item.Item input,
            net.minecraft.world.item.Item expectedOutput) {
        ItemStack stack = new ItemStack(input);
        net.minecraft.world.item.crafting.SingleRecipeInput recipeInput =
            new net.minecraft.world.item.crafting.SingleRecipeInput(stack);
        java.util.Optional<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.SmeltingRecipe>> match =
            rm.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, recipeInput, level);
        if (match.isEmpty()) {
            helper.fail("no smelting recipe matches " + BuiltInRegistries.ITEM.getKey(input));
            return;
        }
        ItemStack output = match.get().value().assemble(recipeInput, level.registryAccess());
        if (!output.is(expectedOutput)) {
            helper.fail("smelt(" + BuiltInRegistries.ITEM.getKey(input) + ") = "
                + BuiltInRegistries.ITEM.getKey(output.getItem())
                + ", expected " + BuiltInRegistries.ITEM.getKey(expectedOutput));
        }
    }

    // ===================================================================
    // Froglight Crucible (v1.12 wave 1) — heat-driven Froglight -> fluid
    // ===================================================================

    private static ItemStack stampedFroglight(String variant) {
        ItemStack stack = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
        stack.set(com.flatts.productivefrogs.registry.PFDataComponents.SLIME_VARIANT.get(),
            ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variant));
        return stack;
    }

    /**
     * End-to-end melt loop: a torch first pins the heat-1 data-map read, then
     * the heat source swaps to soul fire (heat 5, permanent on soul sand) so
     * the 1,000 mB lava Froglight melts in CI-friendly time (~1,340 ticks at
     * 0.15 mB/tick/heat - over a torch the bulk fluids now take minutes by
     * design). Once full, three invariants are checked in the same world
     * state: (1) a water Froglight is rejected (single-fluid tank: lava !=
     * water), (2) the FluidHandler.BLOCK capability refuses fill() (input is
     * items, not fluid), and (3) the capability drains the full 1,000 mB of
     * lava back out (the pipe/bucket path).
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 1600)
    public static void crucibleMeltsLavaFroglightOverTorchHeat(GameTestHelper helper) {
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(base.above(), net.minecraft.world.level.block.Blocks.TORCH);
        helper.setBlock(base.above(2), PFBlocks.CRUCIBLE.get());
        if (!(helper.getBlockEntity(base.above(2))
                instanceof com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity crucible)) {
            helper.fail("crucible block did not create a CrucibleBlockEntity");
            return;
        }
        if (crucible.heatBelow() != 1) {
            helper.fail("torch below should read heat 1 from the crucible_heat data map, got " + crucible.heatBelow());
            return;
        }
        // Swap the torch for soul fire so the bulk melt finishes in CI time.
        helper.setBlock(base, net.minecraft.world.level.block.Blocks.SOUL_SAND);
        helper.setBlock(base.above(), net.minecraft.world.level.block.Blocks.SOUL_FIRE);
        if (crucible.heatBelow() != 5) {
            helper.fail("soul fire below should read heat 5 from the crucible_heat data map, got " + crucible.heatBelow());
            return;
        }
        if (!crucible.acceptFroglight(stampedFroglight("lava"))) {
            helper.fail("lava Froglight should queue into an idle, empty crucible");
            return;
        }
        if (crucible.solids() != 1000) {
            helper.fail("queued solids should be 1000 mB after one lava Froglight, got " + crucible.solids());
            return;
        }
        helper.succeedWhen(() -> {
            net.neoforged.neoforge.fluids.FluidStack fluid = crucible.fluid();
            helper.assertTrue(fluid.getFluid() == net.minecraft.world.level.material.Fluids.LAVA
                    && fluid.getAmount() == 1000,
                "tank should hold 1000 mB lava after the melt, holds "
                    + fluid.getAmount() + " of " + fluid.getFluid());
            helper.assertTrue(crucible.insertCheck(stampedFroglight("water"))
                    == com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity.InsertCheck.REJECT,
                "water Froglight must be rejected while the tank holds lava (single-fluid rule)");
            net.neoforged.neoforge.fluids.capability.IFluidHandler cap = helper.getLevel().getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(base.above(2)), null);
            helper.assertTrue(cap != null, "crucible exposes no FluidHandler.BLOCK capability");
            helper.assertTrue(cap.fill(new net.neoforged.neoforge.fluids.FluidStack(
                    net.minecraft.world.level.material.Fluids.WATER, 1000),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) == 0,
                "capability must be extract-only (fill must return 0)");
            net.neoforged.neoforge.fluids.FluidStack drained = cap.drain(1000,
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(drained.getFluid() == net.minecraft.world.level.material.Fluids.LAVA
                    && drained.getAmount() == 1000,
                "capability drain should yield the full 1000 mB lava, got "
                    + drained.getAmount() + " of " + drained.getFluid());
        });
    }

    /**
     * No heat below = no melt progress. Loading is deliberately allowed
     * (stage the Froglight, light the fire after), but with stone below the
     * loop must not advance: after a generous delay the tank stays empty,
     * the Froglight stays loaded, and progress stays 0.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 200)
    public static void crucibleDoesNotMeltWithoutHeat(GameTestHelper helper) {
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(base.above(), PFBlocks.CRUCIBLE.get());
        if (!(helper.getBlockEntity(base.above())
                instanceof com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity crucible)) {
            helper.fail("crucible block did not create a CrucibleBlockEntity");
            return;
        }
        if (!crucible.acceptFroglight(stampedFroglight("lava"))) {
            helper.fail("queueing a Froglight must not require heat (stage first, light after)");
            return;
        }
        helper.runAfterDelay(80L, () -> {
            if (!crucible.fluid().isEmpty()) {
                helper.fail("tank filled despite no heat source below");
                return;
            }
            if (crucible.solids() != 1000) {
                helper.fail("solids should stay fully queued without heat, got " + crucible.solids());
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Placed Froglights as heat sources via the variant-keyed
     * {@code froglight_heat} data map: a Blaze Froglight under a Crucible
     * reads heat 6 (the above-fire rung), while an unmapped variant (iron)
     * contributes nothing. Pins the BE-variant lookup path that the
     * block-keyed {@code crucible_heat} map can't express.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void crucibleReadsFroglightHeatFromVariantDataMap(GameTestHelper helper) {
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, PFBlocks.CONFIGURABLE_FROGLIGHT.get());
        helper.setBlock(base.above(), PFBlocks.CRUCIBLE.get());
        if (!(helper.getBlockEntity(base)
                instanceof com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity froglight)) {
            helper.fail("configurable froglight did not create its BlockEntity");
            return;
        }
        if (!(helper.getBlockEntity(base.above())
                instanceof com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity crucible)) {
            helper.fail("crucible block did not create a CrucibleBlockEntity");
            return;
        }
        froglight.setVariantId(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "blaze"));
        if (crucible.heatBelow() != 6) {
            helper.fail("Blaze Froglight below should read heat 6 from froglight_heat, got " + crucible.heatBelow());
            return;
        }
        froglight.setVariantId(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        if (crucible.heatBelow() != 0) {
            helper.fail("an unmapped Froglight variant must contribute no heat, got " + crucible.heatBelow());
            return;
        }
        helper.succeed();
    }

    /**
     * Wave-2 molten lane, ATM-interop direction included: an iron Froglight
     * melts to 180 mB (2 ingots' worth, Tinkers ore-doubling) of molten iron -
     * and WHICH molten iron is environment-derived, mirroring the
     * mod_loaded conditions on the generated recipes: AllTheOres loaded (the
     * dev run/mods env) -> {@code alltheores:molten_iron}; lean env (CI) ->
     * the PF-minted {@code productivefrogs:molten_iron} fallback. One test
     * therefore pins the ATO-deferral path locally and the PF-mint path in CI.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 1400)
    public static void crucibleMeltsMetalFroglightToMoltenFluid(GameTestHelper helper) {
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(base.above(), net.minecraft.world.level.block.Blocks.TORCH);
        helper.setBlock(base.above(2), PFBlocks.CRUCIBLE.get());
        if (!(helper.getBlockEntity(base.above(2))
                instanceof com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity crucible)) {
            helper.fail("crucible block did not create a CrucibleBlockEntity");
            return;
        }
        if (!crucible.acceptFroglight(stampedFroglight("iron"))) {
            helper.fail("iron Froglight should queue (every metal has a molten mapping in wave 2)");
            return;
        }
        ResourceLocation expected = net.neoforged.fml.ModList.get().isLoaded("alltheores")
            ? ResourceLocation.parse("alltheores:molten_iron")
            : ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "molten_iron");
        helper.succeedWhen(() -> {
            net.neoforged.neoforge.fluids.FluidStack fluid = crucible.fluid();
            ResourceLocation actual = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
            helper.assertTrue(expected.equals(actual) && fluid.getAmount() == 180,
                "iron Froglight should melt to 180 mB of " + expected + ", got "
                    + fluid.getAmount() + " of " + actual);
        });
    }

    /**
     * Insert gating on the solids model: (1) a Froglight with no
     * {@code crucible_melting} recipe (bone) classifies REJECT and
     * {@code acceptFroglight} refuses it; (2) the hopper-facing item
     * capability accepts lava Froglights one at a time until the solids
     * queue is full (4 x 1,000 mB), then bounces the fifth - pinning both
     * the automation path and the MAX_SOLIDS cap.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void crucibleInsertGatingAndHopperQueueCap(GameTestHelper helper) {
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(base.above(), PFBlocks.CRUCIBLE.get());
        if (!(helper.getBlockEntity(base.above())
                instanceof com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity crucible)) {
            helper.fail("crucible block did not create a CrucibleBlockEntity");
            return;
        }
        // Bone has no melt recipe (and never will - the melt roster is the
        // metal lane). Iron stopped being the reject example when wave 2 gave
        // every metal a molten mapping.
        ItemStack bone = stampedFroglight("bone");
        if (crucible.insertCheck(bone)
                != com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity.InsertCheck.REJECT) {
            helper.fail("bone Froglight has no melt recipe and must classify REJECT");
            return;
        }
        if (crucible.acceptFroglight(bone)) {
            helper.fail("acceptFroglight must refuse a REJECT-classified stack");
            return;
        }
        net.neoforged.neoforge.items.IItemHandler items = helper.getLevel().getCapability(
            net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
            helper.absolutePos(base.above()), null);
        if (items == null) {
            helper.fail("crucible exposes no ItemHandler.BLOCK capability");
            return;
        }
        // Hopper path: one Froglight consumed per insert call (one recipe
        // evaluation each), four fit, the fifth bounces off the full queue.
        ItemStack rest = stampedFroglight("lava");
        rest.setCount(4);
        for (int i = 0; i < 4 && !rest.isEmpty(); i++) {
            rest = items.insertItem(0, rest, false);
        }
        if (!rest.isEmpty()) {
            helper.fail("four lava Froglights should hopper-insert while the queue has room, "
                + rest.getCount() + " left over");
            return;
        }
        if (crucible.solids() != 4000) {
            helper.fail("solids queue should hold 4000 mB after four Froglights, got " + crucible.solids());
            return;
        }
        ItemStack fifth = stampedFroglight("lava");
        ItemStack bounced = items.insertItem(0, fifth, false);
        if (bounced.isEmpty() || bounced.getCount() != 1) {
            helper.fail("fifth Froglight must bounce off the full solids queue");
            return;
        }
        helper.succeed();
    }

    // ===================================================================
    // Casting Mold (v1.12 wave 2 part B) — molten -> ingot
    // ===================================================================

    /** The molten-iron fluid this environment mints (mirrors the recipe conditions). */
    private static net.minecraft.world.level.material.Fluid envMoltenIron() {
        ResourceLocation id = net.neoforged.fml.ModList.get().isLoaded("alltheores")
            ? ResourceLocation.parse("alltheores:molten_iron")
            : ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "molten_iron");
        return BuiltInRegistries.FLUID.get(id);
    }

    /**
     * The full three-block tower, end to end: torch (heat 1) under a Crucible
     * under a Casting Mold. An iron Froglight melts to 180 mB molten iron,
     * the Mold tower-pulls it (no pipes), and two 90 mB casts land 2 iron
     * ingots in the output slot - the Tinkers-convention ore-doubling yield
     * surfacing as items. Environment-aware like the melt test: the molten
     * fluid is ATO's or PF's, but the cast output is vanilla iron either way
     * (the recipe matches by {@code c:molten_iron} TAG).
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 1600)
    public static void castingMoldTowerCastsIronFroglightToTwoIngots(GameTestHelper helper) {
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(base.above(), net.minecraft.world.level.block.Blocks.TORCH);
        helper.setBlock(base.above(2), PFBlocks.CRUCIBLE.get());
        helper.setBlock(base.above(3), PFBlocks.CASTING_MOLD.get());
        if (!(helper.getBlockEntity(base.above(2))
                instanceof com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity crucible)) {
            helper.fail("crucible block did not create a CrucibleBlockEntity");
            return;
        }
        if (!(helper.getBlockEntity(base.above(3))
                instanceof com.flatts.productivefrogs.content.block.entity.CastingMoldBlockEntity mold)) {
            helper.fail("casting mold block did not create a CastingMoldBlockEntity");
            return;
        }
        if (!crucible.acceptFroglight(stampedFroglight("iron"))) {
            helper.fail("iron Froglight should queue into the crucible");
            return;
        }
        helper.succeedWhen(() -> {
            ItemStack out = mold.output().getStackInSlot(
                com.flatts.productivefrogs.content.block.entity.CastingMoldBlockEntity.OUTPUT_SLOT);
            helper.assertTrue(out.is(net.minecraft.world.item.Items.IRON_INGOT) && out.getCount() == 2,
                "tower should cast 2 iron ingots from one iron Froglight (180 mB / 90 mB), got "
                    + out.getCount() + " x " + BuiltInRegistries.ITEM.getKey(out.getItem()));
            helper.assertTrue(mold.fluid().isEmpty() && crucible.fluid().isEmpty(),
                "both tanks should be empty after the full melt + double cast");
        });
    }

    /**
     * Free-standing Mold fed through the fill-enabled FluidHandler.BLOCK
     * capability (the pipe path): 90 mB of this environment's molten iron
     * fills, and one cast lands 1 iron ingot with the buffer drained.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 200)
    public static void castingMoldCapabilityFillCastsOneIngot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, PFBlocks.CASTING_MOLD.get());
        if (!(helper.getBlockEntity(pos)
                instanceof com.flatts.productivefrogs.content.block.entity.CastingMoldBlockEntity mold)) {
            helper.fail("casting mold block did not create a CastingMoldBlockEntity");
            return;
        }
        net.neoforged.neoforge.fluids.capability.IFluidHandler cap = helper.getLevel().getCapability(
            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
            helper.absolutePos(pos), null);
        if (cap == null) {
            helper.fail("casting mold exposes no FluidHandler.BLOCK capability");
            return;
        }
        int filled = cap.fill(new net.neoforged.neoforge.fluids.FluidStack(envMoltenIron(), 90),
            net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        if (filled != 90) {
            helper.fail("capability should accept the full 90 mB of molten iron, took " + filled);
            return;
        }
        // Fill-only handler: committed molten must not be pipeable back out -
        // it leaves as a cast item or not at all.
        if (!cap.drain(90, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE).isEmpty()) {
            helper.fail("mold capability must refuse drain (fill-only; molten leaves as an item)");
            return;
        }
        helper.succeedWhen(() -> {
            ItemStack out = mold.output().getStackInSlot(
                com.flatts.productivefrogs.content.block.entity.CastingMoldBlockEntity.OUTPUT_SLOT);
            helper.assertTrue(out.is(net.minecraft.world.item.Items.IRON_INGOT) && out.getCount() == 1,
                "90 mB molten iron should cast 1 iron ingot, got "
                    + out.getCount() + " x " + BuiltInRegistries.ITEM.getKey(out.getItem()));
            helper.assertTrue(mold.fluid().isEmpty(), "buffer should be drained after the cast");
        });
    }

    /**
     * Fill gating: the Mold accepts only fluids some {@code mold_casting}
     * recipe consumes. Water and lava (no cast recipes) bounce off the
     * capability with 0 accepted, and once molten iron is buffered a
     * different castable molten is refused too (single-fluid buffer).
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void castingMoldRejectsNonCastableFluids(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, PFBlocks.CASTING_MOLD.get());
        if (!(helper.getBlockEntity(pos)
                instanceof com.flatts.productivefrogs.content.block.entity.CastingMoldBlockEntity mold)) {
            helper.fail("casting mold block did not create a CastingMoldBlockEntity");
            return;
        }
        net.neoforged.neoforge.fluids.capability.IFluidHandler cap = helper.getLevel().getCapability(
            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
            helper.absolutePos(pos), null);
        if (cap == null) {
            helper.fail("casting mold exposes no FluidHandler.BLOCK capability");
            return;
        }
        if (cap.fill(new net.neoforged.neoforge.fluids.FluidStack(
                net.minecraft.world.level.material.Fluids.WATER, 1000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) != 0) {
            helper.fail("water has no mold_casting recipe and must be refused");
            return;
        }
        if (cap.fill(new net.neoforged.neoforge.fluids.FluidStack(
                net.minecraft.world.level.material.Fluids.LAVA, 1000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) != 0) {
            helper.fail("lava has no mold_casting recipe and must be refused");
            return;
        }
        // Buffer 45 mB molten iron (a partial tower pull's worth)...
        if (cap.fill(new net.neoforged.neoforge.fluids.FluidStack(envMoltenIron(), 45),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) != 45) {
            helper.fail("a partial 45 mB of molten iron should still be accepted (amount is the solidify loop's concern)");
            return;
        }
        // ...then a different castable molten must bounce (single-fluid buffer).
        net.minecraft.world.level.material.Fluid copper = BuiltInRegistries.FLUID.get(
            net.neoforged.fml.ModList.get().isLoaded("alltheores")
                ? ResourceLocation.parse("alltheores:molten_copper")
                : ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "molten_copper"));
        if (cap.fill(new net.neoforged.neoforge.fluids.FluidStack(copper, 45),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) != 0) {
            helper.fail("molten copper must be refused while the buffer holds molten iron (single-fluid rule)");
            return;
        }
        helper.succeed();
    }

    private static void assertVariantSmelts(
            GameTestHelper helper,
            net.minecraft.world.item.crafting.RecipeManager rm,
            ServerLevel level,
            String variant,
            net.minecraft.world.item.Item expectedOutput) {
        ItemStack stack = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
        stack.set(com.flatts.productivefrogs.registry.PFDataComponents.SLIME_VARIANT.get(),
            ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variant));
        net.minecraft.world.item.crafting.SingleRecipeInput recipeInput =
            new net.minecraft.world.item.crafting.SingleRecipeInput(stack);
        java.util.Optional<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.SmeltingRecipe>> match =
            rm.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, recipeInput, level);
        if (match.isEmpty()) {
            helper.fail("no smelting recipe matches configurable_froglight[variant=" + variant + "]");
            return;
        }
        ItemStack output = match.get().value().assemble(recipeInput, level.registryAccess());
        if (!output.is(expectedOutput)) {
            helper.fail("smelt(configurable_froglight[variant=" + variant + "]) = "
                + BuiltInRegistries.ITEM.getKey(output.getItem())
                + ", expected " + BuiltInRegistries.ITEM.getKey(expectedOutput));
        }
    }

    // ---------------------------------------------------------------------
    // Q9 — Player direct-feeding
    // ---------------------------------------------------------------------

    /**
     * Category-only Slime Bucket (no variant tag, e.g. a Bucket of Cave Slime)
     * fed to a matching Resource Frog. V1.5 = PASS, bucket NOT consumed:
     * category-only slimes are intermediates only; frogs only "produce" from
     * variant-stamped slimes. Direct-feeding an unstamped bucket is a no-op.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void directFeedCategoryOnlyBucketIsNoOp(GameTestHelper helper) {
        BlockPos frogPos = new BlockPos(2, 2, 2);

        // Build the bucket from a parent species (Cave Slime). The bucket
        // ends up with a Category but no Variant.
        com.flatts.productivefrogs.content.entity.CaveSlime source =
            helper.spawn(PFEntities.CAVE_SLIME.get(), new BlockPos(4, 2, 4));
        source.setSize(1, true);
        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        // CaveSlime doesn't have a saveToBucketTag override; manually write
        // the category tag the way the bucket flow would.
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("Category", Category.CAVE.name());
        bucket.set(net.minecraft.core.component.DataComponents.BUCKET_ENTITY_DATA,
            net.minecraft.world.item.component.CustomData.of(tag));
        source.discard();

        com.flatts.productivefrogs.content.entity.ResourceFrog frog =
            helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(Category.CAVE);

        net.minecraft.world.entity.player.Player player =
            helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack bucketCopy = bucket.copy();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bucketCopy);

        net.minecraft.world.InteractionResult result =
            frog.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        // PASS = "we didn't handle it, vanilla fallthrough". Bucket must NOT
        // be consumed; no drops.
        if (result != net.minecraft.world.InteractionResult.PASS) {
            helper.fail("expected PASS for category-only direct-feed (V1.5 no-op), got " + result);
            return;
        }
        ItemStack heldAfter = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (!heldAfter.is(PFItems.SLIME_BUCKET.get())) {
            helper.fail("category-only direct-feed must NOT consume the bucket; held now: "
                + BuiltInRegistries.ITEM.getKey(heldAfter.getItem()));
            return;
        }
        helper.runAfterDelay(10L, () -> {
            boolean anyDrop = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .anyMatch(itemEntity -> itemEntity.getItem().is(PFItems.CONFIGURABLE_FROGLIGHT.get()));
            if (anyDrop) {
                helper.fail("no configurable_froglight should drop from category-only direct-feed");
            }
            helper.succeed();
        });
    }

    /**
     * Variant path: an iron-variant Slime Bucket fed to a CAVE Resource
     * Frog drops a {@code configurable_froglight} stamped with the iron
     * variant id (NOT a broad-strokes category Froglight). Mirrors
     * the variant_slime_kill_drops_configurable_froglight test but for the
     * player-driven path instead of the tongue-kill path.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void directFeedVariantSlimeDropsConfigurableFroglight(GameTestHelper helper) {
        BlockPos frogPos = new BlockPos(2, 2, 2);

        ResourceSlime source = helper.spawn(PFEntities.RESOURCE_SLIME.get(), new BlockPos(4, 2, 4));
        source.setSize(1, true);
        ResourceLocation ironVariant = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");
        source.setVariant(ironVariant);
        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        source.saveToBucketTag(bucket);
        source.discard();

        com.flatts.productivefrogs.content.entity.ResourceFrog frog =
            helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        // V1.5: iron is a Cave variant — frog category must match the
        // bucket's variant category for direct-feed to fire.
        frog.setCategory(Category.CAVE);

        net.minecraft.world.entity.player.Player player =
            helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bucket);

        net.minecraft.world.InteractionResult result =
            frog.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        if (result != net.minecraft.world.InteractionResult.SUCCESS) {
            helper.fail("expected SUCCESS interaction for variant direct-feed, got " + result);
            return;
        }

        helper.succeedWhen(() -> {
            net.minecraft.world.item.Item expected = PFItems.CONFIGURABLE_FROGLIGHT.get();
            boolean found = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .anyMatch(itemEntity -> {
                    ItemStack stack = itemEntity.getItem();
                    if (!stack.is(expected)) return false;
                    ResourceLocation variant = stack.get(
                        com.flatts.productivefrogs.registry.PFDataComponents.SLIME_VARIANT.get());
                    return ironVariant.equals(variant);
                });
            if (!found) {
                helper.fail("expected configurable_froglight stamped with iron variant to drop");
            }
        });
    }

    /**
     * Mismatch path: a BOG slime bucket fed to a TIDE Resource
     * Frog must be a no-op. The bucket is NOT consumed, no froglight drops,
     * and the result returns PASS (so vanilla Animal#mobInteract continues
     * — slimeballs and name-tag still work as before).
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void directFeedMismatchedCategoryIsANoOp(GameTestHelper helper) {
        BlockPos frogPos = new BlockPos(2, 2, 2);

        ResourceSlime source = helper.spawn(PFEntities.RESOURCE_SLIME.get(), new BlockPos(4, 2, 4));
        source.setSize(1, true);
        source.setCategory(Category.BOG);
        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        source.saveToBucketTag(bucket);
        source.discard();

        com.flatts.productivefrogs.content.entity.ResourceFrog frog =
            helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(Category.TIDE);

        net.minecraft.world.entity.player.Player player =
            helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bucket);

        net.minecraft.world.InteractionResult mismatchResult =
            frog.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);

        // Pin the mismatch contract: result must NOT be SUCCESS, otherwise
        // a refactor could silently swallow the interaction (treating it as
        // handled) without actually consuming the bucket or producing a
        // drop. The fall-through to super.mobInteract → Animal.mobInteract
        // with a non-breeding item returns PASS in vanilla 1.21.x, but
        // accept TRY_WITH_EMPTY_HAND too — both encode "not handled here".
        if (mismatchResult == net.minecraft.world.InteractionResult.SUCCESS
            || mismatchResult == net.minecraft.world.InteractionResult.CONSUME) {
            helper.fail("mismatched direct-feed returned " + mismatchResult
                + " — expected PASS or TRY_WITH_EMPTY_HAND from the super.mobInteract fallthrough");
            return;
        }

        // Allow a short window for any erroneous spawns to appear, then
        // assert nothing happened.
        helper.runAfterDelay(5L, () -> {
            // Bucket should still be a Slime Bucket — not consumed.
            ItemStack heldNow = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (!heldNow.is(PFItems.SLIME_BUCKET.get())) {
                helper.fail("mismatched direct-feed consumed the bucket — expected SLIME_BUCKET retained, got "
                    + BuiltInRegistries.ITEM.getKey(heldNow.getItem()));
                return;
            }
            // No configurable_froglight should have dropped — V1.5: that's the
            // only froglight type. Species-level Froglight blocks were removed.
            boolean configurableDropped = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .anyMatch(itemEntity -> itemEntity.getItem().is(PFItems.CONFIGURABLE_FROGLIGHT.get()));
            if (configurableDropped) {
                helper.fail("mismatched direct-feed dropped a configurable_froglight — should have been a no-op");
                return;
            }
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------------
    // Tank-mod compatibility (IFluidHandler via NeoForge capability)
    // ---------------------------------------------------------------------

    /**
     * NeoForge auto-registers {@link
     * net.neoforged.neoforge.capabilities.Capabilities.Fluid#ITEM} on every
     * vanilla {@link net.minecraft.world.item.BucketItem} subclass (see
     * {@code CapabilityHooks.registerVanillaProviders}). Our Slime Milk
     * buckets are vanilla BucketItems with our source fluid attached, so
     * tank-mod compatibility for the bucket form should work out of the box.
     *
     * <p>This test verifies it: spot-check two representative variants
     * (iron, blaze) and assert each exposes a non-null
     * {@code ResourceHandler<FluidResource>} whose contents match the
     * variant's source fluid. If NeoForge ever drops the auto-registration
     * or our buckets stop being recognized as BucketItem subclasses, this
     * test catches it before a downstream modpack ships broken.
     *
     * <p>The block-level fluid capability ({@code Capabilities.Fluid.BLOCK})
     * is NOT registered by us — Productive Bees ships the same way for its
     * honey LiquidBlock. Tank mods that want to pump from a milk source
     * block use vanilla bucket-pickup mechanics on {@link
     * net.minecraft.world.level.block.LiquidBlock}, which our
     * {@link com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock}
     * inherits unchanged.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void milkBucketExposesFluidCapabilityForTankMods(GameTestHelper helper) {
        assertBucketExposesFluid(helper, "iron");
        assertBucketExposesFluid(helper, "blaze");
        helper.succeed();
    }

    private static void assertBucketExposesFluid(GameTestHelper helper, String variant) {
        ItemStack stack = milkBucket(variant);
        // 1.21.1: Capabilities.FluidHandler.ITEM returns IFluidHandlerItem (the
        // transfer.* / ResourceHandler<FluidResource> rewrite only landed in 1.21.4+).
        net.neoforged.neoforge.fluids.capability.IFluidHandlerItem handler =
            stack.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM);
        if (handler == null) {
            helper.fail(variant + "_slime_milk_bucket exposes no FluidHandler.ITEM capability — "
                + "NeoForge's auto-registration on BucketItem broke");
            return;
        }
        if (handler.getTanks() < 1) {
            helper.fail(variant + " bucket handler reports " + handler.getTanks() + " tanks, expected >= 1");
            return;
        }
        net.neoforged.neoforge.fluids.FluidStack contents = handler.getFluidInTank(0);
        net.minecraft.world.level.material.Fluid expectedFluid =
            PFVariantMilk.sourceFluid(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variant));
        if (contents.getFluid() != expectedFluid) {
            helper.fail(variant + " bucket handler reports fluid "
                + BuiltInRegistries.FLUID.getKey(contents.getFluid())
                + ", expected " + BuiltInRegistries.FLUID.getKey(expectedFluid));
        }
    }

    // ---------------------------------------------------------------------
    // Slime Milker furnace-style BE (Q9b — known_issues.md redesign)
    // ---------------------------------------------------------------------

    /**
     * Happy path: place a Slime Milker, drop an iron-variant Slime Bucket
     * into its input slot, drive the server-tick by hand 100 times, and
     * assert the output slot holds an iron Slime Milk bucket and the
     * input slot is empty.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 200)
    public static void slimeMilkerBeCooksIronBucketToIronMilkAfter100Ticks(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.SLIME_MILKER.get());

        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pos);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(absPos);
        if (!(be instanceof com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity milker)) {
            helper.fail("expected SlimeMilkerBlockEntity at " + absPos + ", got "
                + (be == null ? "null" : be.getClass().getSimpleName()));
            return;
        }

        // Build the iron-variant Slime Bucket via the real saveToBucketTag
        // path so the test pins the same NBT shape the in-world capture
        // flow produces.
        ResourceSlime source = helper.spawn(PFEntities.RESOURCE_SLIME.get(), new BlockPos(4, 2, 4));
        source.setSize(1, true);
        source.setVariant(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        source.saveToBucketTag(bucket);
        source.discard();

        milker.getInventory().setStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.INPUT_SLOT, bucket);

        // Drive the cook loop. serverTick is a no-op on client; on server
        // it advances cookProgress by one per call. After exactly
        // COOK_TIME_TOTAL invocations the input should be consumed and
        // the output should be set.
        int total = com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.COOK_TIME_TOTAL;
        for (int i = 0; i < total; i++) {
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.serverTick(
                level, absPos, level.getBlockState(absPos), milker);
        }

        ItemStack input = milker.getInventory().getStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.INPUT_SLOT);
        ItemStack output = milker.getInventory().getStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.OUTPUT_SLOT);
        if (!input.isEmpty()) {
            helper.fail("expected input slot empty after cook, got "
                + BuiltInRegistries.ITEM.getKey(input.getItem()));
            return;
        }
        if (!isMilkBucket(output, "iron")) {
            helper.fail("expected output to be iron-stamped slime_milk_bucket, got "
                + (output.isEmpty() ? "EMPTY" : BuiltInRegistries.ITEM.getKey(output.getItem())));
            return;
        }
        helper.succeed();
    }

    /**
     * Negative case: an empty Slime Bucket (no Variant component) sitting
     * in the input slot should NOT advance cook progress. Pins the
     * "fail-closed" semantic — a category-only or vanilla bucket can sit
     * in the input forever without producing a default-milk output.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void slimeMilkerBeResetsProgressWhenInputLacksVariant(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.SLIME_MILKER.get());

        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pos);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(absPos);
        if (!(be instanceof com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity milker)) {
            helper.fail("expected SlimeMilkerBlockEntity at " + absPos);
            return;
        }

        // Empty Slime Bucket — no BUCKET_ENTITY_DATA at all.
        milker.getInventory().setStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.INPUT_SLOT,
            new ItemStack(PFItems.SLIME_BUCKET.get()));

        // Tick 20 times — should never advance progress.
        for (int i = 0; i < 20; i++) {
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.serverTick(
                level, absPos, level.getBlockState(absPos), milker);
        }

        if (milker.getCookProgress() != 0) {
            helper.fail("expected cookProgress=0 with empty bucket input, got " + milker.getCookProgress());
            return;
        }
        ItemStack output = milker.getInventory().getStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.OUTPUT_SLOT);
        if (!output.isEmpty()) {
            helper.fail("output slot must remain empty when input has no variant, got "
                + BuiltInRegistries.ITEM.getKey(output.getItem()));
            return;
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------
    // Spawnery (skyblock bootstrap appliance)
    // ---------------------------------------------------------------------

    /**
     * Vanilla path: a glass bottle + a slime-ball fuel + a slime-ball PRIMER produce
     * one plain vanilla frogspawn bottle (a Frog Egg with no contained_category). A
     * primer is required and a slime ball is the vanilla primer; bottle, fuel, and
     * primer are all consumed. Drives serverTick by hand (mirrors the Milker test).
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void spawnerySlimeBallPrimerProducesVanillaFrogspawn(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.SPAWNERY.get());
        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pos);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(absPos);
        if (!(be instanceof SpawneryBlockEntity spawnery)) {
            helper.fail("expected SpawneryBlockEntity at " + absPos + ", got "
                + (be == null ? "null" : be.getClass().getSimpleName()));
            return;
        }
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.BOTTLE_SLOT, new ItemStack(Items.GLASS_BOTTLE));
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.FUEL_SLOT, new ItemStack(Items.SLIME_BALL));
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.PRIMER_SLOT, new ItemStack(Items.SLIME_BALL));

        driveSpawnery(level, absPos, spawnery);

        ItemStack output = spawnery.getInventory().getStackInSlot(SpawneryBlockEntity.OUTPUT_SLOT);
        if (!output.is(PFItems.FROG_EGG.get())) {
            helper.fail("expected a frog egg output, got "
                + (output.isEmpty() ? "EMPTY" : BuiltInRegistries.ITEM.getKey(output.getItem())));
            return;
        }
        if (output.get(com.flatts.productivefrogs.registry.PFDataComponents.CONTAINED_CATEGORY.get()) != null) {
            helper.fail("a slime-ball primer must yield plain vanilla frogspawn (no contained_category)");
            return;
        }
        if (!spawnery.getInventory().getStackInSlot(SpawneryBlockEntity.BOTTLE_SLOT).isEmpty()) {
            helper.fail("the glass bottle should have been consumed");
            return;
        }
        if (!spawnery.getInventory().getStackInSlot(SpawneryBlockEntity.FUEL_SLOT).isEmpty()) {
            helper.fail("the fuel slime ball should have been consumed");
            return;
        }
        if (!spawnery.getInventory().getStackInSlot(SpawneryBlockEntity.PRIMER_SLOT).isEmpty()) {
            helper.fail("the primer slime ball should have been consumed");
            return;
        }
        helper.succeed();
    }

    /**
     * Primed path: a glass bottle + a slime ball + an iron-ingot primer (which is
     * in spawnery_primer/cave) produce a Frog Egg stamped CAVE, with the iron
     * ingot consumed.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void spawneryIronPrimerProducesCaveEgg(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.SPAWNERY.get());
        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pos);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(absPos);
        if (!(be instanceof SpawneryBlockEntity spawnery)) {
            helper.fail("expected SpawneryBlockEntity at " + absPos);
            return;
        }
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.BOTTLE_SLOT, new ItemStack(Items.GLASS_BOTTLE));
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.FUEL_SLOT, new ItemStack(Items.SLIME_BALL));
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.PRIMER_SLOT, new ItemStack(Items.IRON_INGOT));

        driveSpawnery(level, absPos, spawnery);

        ItemStack output = spawnery.getInventory().getStackInSlot(SpawneryBlockEntity.OUTPUT_SLOT);
        if (!output.is(PFItems.FROG_EGG.get())) {
            helper.fail("expected a frog egg output, got "
                + (output.isEmpty() ? "EMPTY" : BuiltInRegistries.ITEM.getKey(output.getItem())));
            return;
        }
        Category stamped = output.get(com.flatts.productivefrogs.registry.PFDataComponents.CONTAINED_CATEGORY.get());
        if (stamped != Category.CAVE) {
            helper.fail("iron ingot primer must stamp CAVE, got " + stamped);
            return;
        }
        if (!spawnery.getInventory().getStackInSlot(SpawneryBlockEntity.PRIMER_SLOT).isEmpty()) {
            helper.fail("the iron ingot primer should have been consumed");
            return;
        }
        helper.succeed();
    }

    /**
     * No fuel: a glass bottle + a primer but an empty fuel slot never ignites, so
     * cook progress stays at 0 and no egg is produced. The primer is present so this
     * isolates the missing-fuel stall (not the missing-primer one).
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void spawneryWithoutFuelDoesNotProduce(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.SPAWNERY.get());
        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pos);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(absPos);
        if (!(be instanceof SpawneryBlockEntity spawnery)) {
            helper.fail("expected SpawneryBlockEntity at " + absPos);
            return;
        }
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.BOTTLE_SLOT, new ItemStack(Items.GLASS_BOTTLE));
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.PRIMER_SLOT, new ItemStack(Items.IRON_INGOT));

        driveSpawnery(level, absPos, spawnery);

        if (spawnery.getCookProgress() != 0) {
            helper.fail("cook progress must stay 0 without fuel, got " + spawnery.getCookProgress());
            return;
        }
        ItemStack output = spawnery.getInventory().getStackInSlot(SpawneryBlockEntity.OUTPUT_SLOT);
        if (!output.isEmpty()) {
            helper.fail("no egg should be produced without fuel, got "
                + BuiltInRegistries.ITEM.getKey(output.getItem()));
            return;
        }
        helper.succeed();
    }

    /**
     * No bottle: a slime-ball fuel + a primer but an empty bottle slot must not be
     * consumed and must not produce anything (no container to fill). The primer is
     * present so this isolates the missing-bottle stall.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void spawneryWithoutBottleDoesNotConsumeFuel(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.SPAWNERY.get());
        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pos);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(absPos);
        if (!(be instanceof SpawneryBlockEntity spawnery)) {
            helper.fail("expected SpawneryBlockEntity at " + absPos);
            return;
        }
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.FUEL_SLOT, new ItemStack(Items.SLIME_BALL));
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.PRIMER_SLOT, new ItemStack(Items.IRON_INGOT));

        driveSpawnery(level, absPos, spawnery);

        ItemStack fuel = spawnery.getInventory().getStackInSlot(SpawneryBlockEntity.FUEL_SLOT);
        if (fuel.isEmpty() || !fuel.is(Items.SLIME_BALL)) {
            helper.fail("the slime ball must NOT be consumed without a bottle to fill");
            return;
        }
        ItemStack output = spawnery.getInventory().getStackInSlot(SpawneryBlockEntity.OUTPUT_SLOT);
        if (!output.isEmpty()) {
            helper.fail("no egg should be produced without a bottle, got "
                + BuiltInRegistries.ITEM.getKey(output.getItem()));
            return;
        }
        helper.succeed();
    }

    /**
     * Primer required: a glass bottle + a slime-ball fuel but NO primer must produce
     * nothing - and must not even consume fuel (it never ignites). Empty primer does
     * nothing, ever.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void spawneryWithoutPrimerDoesNotProduce(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.SPAWNERY.get());
        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pos);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(absPos);
        if (!(be instanceof SpawneryBlockEntity spawnery)) {
            helper.fail("expected SpawneryBlockEntity at " + absPos);
            return;
        }
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.BOTTLE_SLOT, new ItemStack(Items.GLASS_BOTTLE));
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.FUEL_SLOT, new ItemStack(Items.SLIME_BALL));

        driveSpawnery(level, absPos, spawnery);

        if (spawnery.getCookProgress() != 0) {
            helper.fail("cook progress must stay 0 with no primer, got " + spawnery.getCookProgress());
            return;
        }
        ItemStack fuel = spawnery.getInventory().getStackInSlot(SpawneryBlockEntity.FUEL_SLOT);
        if (fuel.isEmpty() || !fuel.is(Items.SLIME_BALL)) {
            helper.fail("fuel must NOT be consumed without a primer (it never ignites)");
            return;
        }
        ItemStack output = spawnery.getInventory().getStackInSlot(SpawneryBlockEntity.OUTPUT_SLOT);
        if (!output.isEmpty()) {
            helper.fail("no egg should be produced without a primer, got "
                + BuiltInRegistries.ITEM.getKey(output.getItem()));
            return;
        }
        helper.succeed();
    }

    /**
     * Drive the Spawnery's serverTick by hand for one full production cycle plus
     * a margin (mirrors the Slime Milker cook-loop test). serverTick advances
     * burn + cook by one per call; after the configured production ticks the cycle
     * completes once and then stalls on the stacksTo(1) output.
     */
    private static void driveSpawnery(ServerLevel level, BlockPos absPos, SpawneryBlockEntity spawnery) {
        int total = Math.max(1, com.flatts.productivefrogs.PFConfig.SPAWNERY_PRODUCTION_TICKS.get());
        for (int i = 0; i < total + 5; i++) {
            SpawneryBlockEntity.serverTick(level, absPos, level.getBlockState(absPos), spawnery);
        }
    }

    // ---------------------------------------------------------------------
    // Slime Milker hopper compat (Capabilities.Item.BLOCK migration)
    // ---------------------------------------------------------------------

    /**
     * Sanity-check the side-aware {@code Capabilities.Item.BLOCK} provider:
     * querying with {@link net.minecraft.core.Direction#DOWN} returns the
     * output view (sees OUTPUT_SLOT contents, refuses inserts); querying
     * with any other side returns the input view (sees INPUT_SLOT contents,
     * accepts only SLIME_BUCKET inserts). Pins the routing in
     * {@code PFModBusEvents#onRegisterCapabilities} without spinning up a
     * real hopper.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void slimeMilkerCapabilityRoutesInputViewToTopAndOutputViewToBottom(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.SLIME_MILKER.get());
        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pos);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(absPos);
        if (!(be instanceof com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity milker)) {
            helper.fail("expected SlimeMilkerBlockEntity at " + absPos);
            return;
        }

        // Seed: a primed Slime Bucket in INPUT, a finished iron milk bucket in OUTPUT.
        ResourceSlime source = helper.spawn(PFEntities.RESOURCE_SLIME.get(), new BlockPos(4, 2, 4));
        source.setSize(1, true);
        source.setVariant(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        ItemStack primedBucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        source.saveToBucketTag(primedBucket);
        source.discard();
        milker.getInventory().setStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.INPUT_SLOT, primedBucket);
        ItemStack ironMilk = milkBucket("iron");
        milker.getInventory().setStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.OUTPUT_SLOT, ironMilk);

        // 1.21.1: Capabilities.ItemHandler.BLOCK returns IItemHandler (the
        // ResourceHandler<ItemResource> / transfer.* rewrite only landed in 1.21.4+).
        net.neoforged.neoforge.items.IItemHandler downView =
            level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, absPos, net.minecraft.core.Direction.DOWN);
        net.neoforged.neoforge.items.IItemHandler upView =
            level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, absPos, net.minecraft.core.Direction.UP);
        if (downView == null || upView == null) {
            helper.fail("capability not registered for SLIME_MILKER (downView="
                + downView + ", upView=" + upView + ")");
            return;
        }
        if (downView.getSlots() != 1 || upView.getSlots() != 1) {
            helper.fail("expected single-slot views, got downView.slots=" + downView.getSlots()
                + ", upView.slots=" + upView.getSlots());
            return;
        }
        // DOWN view sees the OUTPUT slot's iron milk bucket.
        if (!isMilkBucket(downView.getStackInSlot(0), "iron")) {
            helper.fail("down view should see OUTPUT slot's iron milk bucket, got "
                + downView.getStackInSlot(0));
            return;
        }
        // UP view sees the INPUT slot's primed Slime Bucket.
        if (!upView.getStackInSlot(0).is(PFItems.SLIME_BUCKET.get())) {
            helper.fail("up view should see INPUT slot's slime bucket, got "
                + upView.getStackInSlot(0));
            return;
        }
        // Predicate checks via isItemValid — the slot may be full (we pre-seeded
        // both INPUT and OUTPUT), so insertItem would conflate "slot has no room"
        // with "slot rejects this item". isItemValid is the pure-predicate check
        // that mirrors the old 1.21.11 ResourceHandler.isValid semantics.
        ItemStack probe = new ItemStack(PFItems.SLIME_BUCKET.get());
        if (downView.isItemValid(0, probe)) {
            helper.fail("down view must reject inserts (extract-only)");
            return;
        }
        if (!upView.isItemValid(0, probe)) {
            helper.fail("up view must accept SLIME_BUCKET inserts");
            return;
        }
        // UP view's underlying validator restricts to SLIME_BUCKET only.
        if (upView.isItemValid(0, new ItemStack(Items.IRON_INGOT))) {
            helper.fail("up view must reject non-SLIME_BUCKET items");
            return;
        }
        helper.succeed();
    }

    /**
     * Hopper directly above the milker pushes an iron-variant Slime Bucket
     * into INPUT_SLOT via the capability. Verifies the side=UP routing in
     * {@code PFModBusEvents} actually works against a real vanilla
     * {@code HopperBlockEntity} — not just a synthetic capability query.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void hopperAboveSlimeMilkerPushesSlimeBucketIntoInputSlot(GameTestHelper helper) {
        BlockPos milkerPos = new BlockPos(2, 2, 2);
        BlockPos hopperPos = new BlockPos(2, 3, 2);
        helper.setBlock(milkerPos, PFBlocks.SLIME_MILKER.get());
        // Default hopper state faces DOWN — the orientation we want, since
        // the hopper at (2,3,2) needs to push down into the milker below.
        helper.setBlock(hopperPos, Blocks.HOPPER.defaultBlockState());

        ServerLevel level = helper.getLevel();
        BlockPos absHopper = helper.absolutePos(hopperPos);
        net.minecraft.world.level.block.entity.BlockEntity hopperBe = level.getBlockEntity(absHopper);
        if (!(hopperBe instanceof net.minecraft.world.level.block.entity.HopperBlockEntity hopper)) {
            helper.fail("expected HopperBlockEntity at " + absHopper);
            return;
        }

        ResourceSlime source = helper.spawn(PFEntities.RESOURCE_SLIME.get(), new BlockPos(4, 2, 4));
        source.setSize(1, true);
        source.setVariant(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        source.saveToBucketTag(bucket);
        source.discard();
        hopper.setItem(0, bucket);

        // Hopper transfer cooldown is 8 ticks by default; 30 ticks is
        // safely past one transfer cycle. succeedWhen retries every tick.
        helper.succeedWhen(() -> {
            net.minecraft.world.level.block.entity.BlockEntity be =
                level.getBlockEntity(helper.absolutePos(milkerPos));
            if (!(be instanceof com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity milker)) {
                helper.fail("milker BE went missing at " + helper.absolutePos(milkerPos));
                return;
            }
            ItemStack input = milker.getInventory().getStackInSlot(
                com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.INPUT_SLOT);
            if (input.isEmpty() || !input.is(PFItems.SLIME_BUCKET.get())) {
                helper.fail("hopper has not yet pushed Slime Bucket into INPUT_SLOT (input="
                    + (input.isEmpty() ? "EMPTY" : BuiltInRegistries.ITEM.getKey(input.getItem())) + ")");
                return;
            }
            // Re-resolve the hopper BE (it can be unloaded/reloaded
            // mid-test in some test rigs; safe to re-fetch each retry).
            if (level.getBlockEntity(absHopper) instanceof net.minecraft.world.level.block.entity.HopperBlockEntity h
                && !h.getItem(0).isEmpty()) {
                helper.fail("hopper slot 0 should be drained after the push, still has "
                    + BuiltInRegistries.ITEM.getKey(h.getItem(0).getItem()));
            }
        });
    }

    /**
     * Hopper directly below the milker pulls the finished iron milk bucket
     * out of OUTPUT_SLOT via the side=DOWN capability route. Pre-populates
     * the OUTPUT slot directly (the full cook is covered by the existing
     * BE tick test); this scenario isolates the extract path.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void hopperBelowSlimeMilkerPullsMilkBucketFromOutputSlot(GameTestHelper helper) {
        BlockPos milkerPos = new BlockPos(2, 3, 2);
        BlockPos hopperPos = new BlockPos(2, 2, 2);
        helper.setBlock(milkerPos, PFBlocks.SLIME_MILKER.get());
        // Hopper below the milker; default-facing DOWN means it'll try to
        // push into air below it — fine, the relevant behavior is the pull
        // from the milker above, which happens regardless of facing.
        helper.setBlock(hopperPos, Blocks.HOPPER.defaultBlockState());

        ServerLevel level = helper.getLevel();
        BlockPos absMilker = helper.absolutePos(milkerPos);
        BlockPos absHopper = helper.absolutePos(hopperPos);
        net.minecraft.world.level.block.entity.BlockEntity milkerBe = level.getBlockEntity(absMilker);
        if (!(milkerBe instanceof com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity milker)) {
            helper.fail("expected SlimeMilkerBlockEntity at " + absMilker);
            return;
        }
        ItemStack ironMilk = milkBucket("iron");
        milker.getInventory().setStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.OUTPUT_SLOT, ironMilk);

        helper.succeedWhen(() -> {
            net.minecraft.world.level.block.entity.BlockEntity hopperBe = level.getBlockEntity(absHopper);
            if (!(hopperBe instanceof net.minecraft.world.level.block.entity.HopperBlockEntity hopper)) {
                helper.fail("hopper BE went missing at " + absHopper);
                return;
            }
            ItemStack pulled = hopper.getItem(0);
            if (pulled.isEmpty() || !isMilkBucket(pulled, "iron")) {
                helper.fail("hopper has not yet pulled iron milk bucket (slot0="
                    + (pulled.isEmpty() ? "EMPTY" : BuiltInRegistries.ITEM.getKey(pulled.getItem())) + ")");
                return;
            }
            // Milker OUTPUT must have been drained by the same transfer.
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(absMilker);
            if (be instanceof com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity m) {
                ItemStack output = m.getInventory().getStackInSlot(
                    com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.OUTPUT_SLOT);
                if (!output.isEmpty()) {
                    helper.fail("milker OUTPUT should be drained after pull, still has "
                        + BuiltInRegistries.ITEM.getKey(output.getItem()));
                }
            }
        });
    }

    // =================================================================
    // Frog stat breeding (docs/frog_breeding.md)
    // =================================================================

    /**
     * The same-species breeding gate (D4): two in-love Resource Frogs of the
     * same {@link Category} can mate, but a cross-species pair cannot (with the
     * default {@code breeding.sameSpeciesOnly}). The full breed -> lay -> hatch
     * cycle takes thousands of ticks and is verified manually in {@code runClient};
     * this pins the gate that {@link ResourceFrog#canMate} enforces.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 20)
    public static void sameSpeciesFrogsMateButCrossSpeciesDoNot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        ResourceFrog a = helper.spawn(PFEntities.RESOURCE_FROG.get(), pos);
        ResourceFrog b = helper.spawn(PFEntities.RESOURCE_FROG.get(), pos.east());
        a.setCategory(Category.CAVE);
        b.setCategory(Category.CAVE);
        // Both must be in love for vanilla Animal#canMate to even consider them.
        a.setInLove(null);
        b.setInLove(null);
        if (!a.canMate(b)) {
            helper.fail("two in-love same-species (CAVE) frogs should be able to mate");
            return;
        }
        // Flip one to a different species: the gate must now reject the pair.
        b.setCategory(Category.BOG);
        if (a.canMate(b)) {
            helper.fail("a CAVE frog must not mate a BOG frog with sameSpeciesOnly on");
            return;
        }
        helper.succeed();
    }

    /**
     * A Resource Frog is persistent (never despawns) so a bred-up stat line is
     * not lost (D10). {@code finalizeSpawn} applies the config-gated persistence;
     * {@code helper.spawn} alone does not call it, so we invoke it like the real
     * conversion/spawn paths do.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 20)
    public static void resourceFrogIsPersistentAfterFinalizeSpawn(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), pos);
        frog.finalizeSpawn(
            helper.getLevel(),
            helper.getLevel().getCurrentDifficultyAt(frog.blockPosition()),
            net.minecraft.world.entity.MobSpawnType.NATURAL,
            null);
        if (!frog.isPersistenceRequired()) {
            helper.fail("Resource Frog should be persistence-required (frogs.persistent default true)");
            return;
        }
        helper.succeed();
    }

    /**
     * Bounty multiplies the Froglight yield: a frog at the stat cap drops
     * {@code bountyMaxDrops} Froglights for one slime (the top band always
     * reaches the cap). Counts the summed stack size so item merging doesn't
     * skew the assertion.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void highBountyFrogDropsMaxFroglights(GameTestHelper helper) {
        BlockPos frogPos = new BlockPos(2, 2, 2);
        BlockPos slimePos = new BlockPos(3, 2, 2);

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(Category.CAVE);
        frog.setBounty(frog.getStatCap());

        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), slimePos);
        slime.setSize(1, true);
        // iron is a CAVE variant; setVariant syncs the slime's category to match.
        slime.setVariant(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));

        int expected = com.flatts.productivefrogs.PFConfig.STATS_BOUNTY_MAX_DROPS.get();
        slime.hurt(helper.getLevel().damageSources().mobAttack(frog), 999.0F);

        helper.succeedWhen(() -> {
            int total = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .map(itemEntity -> itemEntity.getItem())
                .filter(stack -> stack.is(PFItems.CONFIGURABLE_FROGLIGHT.get()))
                .mapToInt(ItemStack::getCount)
                .sum();
            if (total != expected) {
                helper.fail("Bounty-capped frog should drop " + expected + " Froglights, counted " + total);
            }
        });
    }

    /**
     * Inheritance bound, in-world: two 1/1/1 parents can never produce an
     * offspring stat above 2 (the better parent {@code hi=1}, plus at most the
     * +1 improvement). Drives the real {@link ResourceFrog#spawnChildFromBreeding}
     * capture on live entities, looped many times to exercise the RNG branches.
     * Regression pin for "first breed jumped a stat from 1 to 3" reports - if the
     * capture ever inflates a stat past hi+1, this fails.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 40)
    public static void breedingOffspringNeverExceedsBetterParentPlusOne(GameTestHelper helper) {
        ResourceFrog a = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(2, 2, 1));
        ResourceFrog b = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(2, 2, 3));
        a.setCategory(Category.CAVE);
        b.setCategory(Category.CAVE);
        a.setStats(1, 1, 1);
        b.setStats(1, 1, 1);

        for (int i = 0; i < 50; i++) {
            a.spawnChildFromBreeding(helper.getLevel(), b);
            int app = a.getPendingOffspringAppetite();
            int bou = a.getPendingOffspringBounty();
            int rea = a.getPendingOffspringReach();
            if (app > 2 || bou > 2 || rea > 2) {
                helper.fail("offspring of two 1/1/1 parents exceeded hi+1: A" + app + "/B" + bou + "/R" + rea
                    + " (max possible is 2)");
                return;
            }
            if (app < 1 || bou < 1 || rea < 1) {
                helper.fail("offspring stat fell below the floor: A" + app + "/B" + bou + "/R" + rea);
                return;
            }
        }
        helper.succeed();
    }

    /**
     * Stat carry, in-world: the offspring stats captured at breeding survive the
     * egg BlockEntity and the hatch onto each tadpole unchanged. Replicates the
     * lay step the way {@link com.flatts.productivefrogs.content.entity.ai.LayCategoryFrogspawn}
     * does (stamp the egg BE from the pregnant frog's pending stats), then forces
     * the hatch via the block's {@code tick}. Catches any inflation or field
     * mix-up in the capture -> egg -> tadpole chain that a "1 to 3" report would
     * imply.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void bredStatsCarryFromEggToTadpoleUnchanged(GameTestHelper helper) {
        Category cat = Category.CAVE;
        BlockPos eggPos = new BlockPos(2, 2, 2);
        helper.setBlock(eggPos.below(), Blocks.WATER);

        ResourceFrog a = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(2, 2, 1));
        ResourceFrog b = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(2, 2, 3));
        a.setCategory(cat);
        b.setCategory(cat);
        a.setStats(1, 1, 1);
        b.setStats(1, 1, 1);
        a.spawnChildFromBreeding(helper.getLevel(), b);
        int pa = a.getPendingOffspringAppetite();
        int pb = a.getPendingOffspringBounty();
        int pr = a.getPendingOffspringReach();

        // Lay: place the egg and stamp its BE exactly as LayCategoryFrogspawn does.
        PrimedFrogEggBlock eggBlock = PFBlocks.primedEgg(cat);
        helper.setBlock(eggPos, eggBlock);
        ServerLevel level = helper.getLevel();
        BlockPos absEgg = helper.absolutePos(eggPos);
        if (!(level.getBlockEntity(absEgg)
                instanceof com.flatts.productivefrogs.content.block.entity.PrimedFrogEggBlockEntity eggBe)) {
            helper.fail("Primed Frog Egg BlockEntity missing after placement");
            return;
        }
        eggBe.setPendingStats(pa, pb, pr);

        // Hatch immediately (bypassing the 3600..12000-tick schedule).
        eggBlock.tick(level.getBlockState(absEgg), level, absEgg, level.getRandom());

        List<ResourceTadpole> tadpoles = helper.getEntities(PFEntities.RESOURCE_TADPOLE.get());
        if (tadpoles.isEmpty()) {
            helper.fail("no tadpoles hatched from the bred egg");
            return;
        }
        for (ResourceTadpole t : tadpoles) {
            if (!t.hasPendingStats()) {
                helper.fail("hatched tadpole lost its bred stats");
                return;
            }
            if (t.getPendingAppetite() != pa || t.getPendingBounty() != pb || t.getPendingReach() != pr) {
                helper.fail("tadpole stats A" + t.getPendingAppetite() + "/B" + t.getPendingBounty()
                    + "/R" + t.getPendingReach() + " != bred stats A" + pa + "/B" + pb + "/R" + pr);
                return;
            }
            if (t.getPendingAppetite() > 2 || t.getPendingBounty() > 2 || t.getPendingReach() > 2) {
                helper.fail("carried stat exceeded the (1,1)-parent ceiling of 2: A" + t.getPendingAppetite()
                    + "/B" + t.getPendingBounty() + "/R" + t.getPendingReach());
                return;
            }
        }
        helper.succeed();
    }

    // =================================================================
    // Frog stat EFFECTS (docs/frog_breeding.md) - the gameplay payoff of
    // the three stats, verified in-world (curve math is in FrogStatsTest).
    // =================================================================

    /**
     * Appetite effect: after an eat the frog enters a hunting cooldown that
     * gates how soon it can target the next slime. Verifies the wiring
     * end-to-end - {@code startEatCooldown} sets {@code HAS_HUNTING_COOLDOWN}
     * (which silently no-ops unless our brain registers it), and while that
     * memory is present the sensor refuses to surface an otherwise-valid,
     * in-range, matching slime. A low Appetite (1) gives the long cooldown, so
     * the gate clearly outlasts the assertion window.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 80)
    public static void appetiteCooldownGatesTongueTargeting(GameTestHelper helper) {
        BlockPos frogPos = new BlockPos(2, 2, 2);
        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(Category.CAVE);
        frog.setStats(1, 1, 5); // Appetite 1 -> longest cooldown; Reach 5 keeps the adjacent slime in range.

        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), frogPos.east());
        slime.setSize(1, true);
        slime.setVariant(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron")); // CAVE

        frog.startEatCooldown();
        // Wiring: if HAS_HUNTING_COOLDOWN weren't registered on the brain,
        // setMemoryWithExpiry would be a no-op and Appetite would do nothing.
        if (!frog.getBrain().hasMemoryValue(
                net.minecraft.world.entity.ai.memory.MemoryModuleType.HAS_HUNTING_COOLDOWN)) {
            helper.fail("startEatCooldown did not set HAS_HUNTING_COOLDOWN (memory not registered on the brain)");
            return;
        }
        // While the cooldown is active, the matching in-range slime must never be targeted.
        helper.onEachTick(() -> {
            if (frog.getBrain().getMemory(
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_ATTACKABLE).orElse(null) == slime) {
                helper.fail("frog targeted prey while its eat cooldown was active - the Appetite gate is broken");
            }
        });
        helper.runAfterDelay(40L, () -> {
            if (!frog.getBrain().hasMemoryValue(
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.HAS_HUNTING_COOLDOWN)) {
                helper.fail("Appetite-1 eat cooldown expired before 40 ticks (expected ~100)");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Bounty effect, low end: a Bounty-1 frog drops exactly one Froglight per
     * slime (complements {@link #highBountyFrogDropsMaxFroglights}, which pins
     * the cap end). Together they prove the Bounty curve is wired to the actual
     * drop loop, not just unit-tested in isolation.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 100)
    public static void lowBountyFrogDropsOneFroglight(GameTestHelper helper) {
        BlockPos frogPos = new BlockPos(2, 2, 2);
        BlockPos slimePos = new BlockPos(3, 2, 2);

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(Category.CAVE);
        frog.setBounty(1);

        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), slimePos);
        slime.setSize(1, true);
        slime.setVariant(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));

        slime.hurt(helper.getLevel().damageSources().mobAttack(frog), 999.0F);

        helper.succeedWhen(() -> {
            int total = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .map(itemEntity -> itemEntity.getItem())
                .filter(stack -> stack.is(PFItems.CONFIGURABLE_FROGLIGHT.get()))
                .mapToInt(ItemStack::getCount)
                .sum();
            if (total != 1) {
                helper.fail("Bounty-1 frog should drop exactly 1 Froglight, counted " + total);
            }
        });
    }

    /**
     * Reach effect, upper end: a max-Reach frog (radius 16) targets a matching
     * slime 12 blocks away - beyond vanilla {@code FrogAttackablesSensor}'s
     * hard-coded 10-block detection distance. Proves our sensor swapped that
     * constant for the Reach radius. Uses the longer {@code empty_5x5x21} plot
     * since 12 blocks does not fit the 5x5x5 one.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x21", timeoutTicks = 200)
    public static void highReachFrogTargetsSlimeBeyondVanillaRange(GameTestHelper helper) {
        floorPlot(helper, 21);
        BlockPos frogPos = new BlockPos(2, 2, 2);
        BlockPos slimePos = new BlockPos(2, 2, 14); // 12 blocks away (> vanilla's 10 cap, < radius 16)

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(Category.CAVE);
        frog.setReach(frog.getStatCap()); // radius 16 at the cap

        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), slimePos);
        slime.setSize(1, true);
        slime.setVariant(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));

        java.util.concurrent.atomic.AtomicInteger stable = new java.util.concurrent.atomic.AtomicInteger(0);
        helper.onEachTick(() -> {
            if (frog.getBrain().getMemory(
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_ATTACKABLE).orElse(null) == slime) {
                if (stable.incrementAndGet() >= 3) {
                    helper.succeed();
                }
            } else {
                stable.set(0);
            }
        });
        helper.runAfterDelay(160L, () ->
            helper.fail("max-Reach frog never targeted a slime 12 blocks away - Reach radius not applied"));
    }

    /**
     * Reach effect, lower end: a Reach-1 frog (radius 8) must NOT target a
     * matching slime 16 blocks away, even though the slime is in the frog's
     * candidate pool (FOLLOW_RANGE 32). Proves the radius actually narrows
     * detection rather than defaulting to the candidate-pool size.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x21", timeoutTicks = 80)
    public static void lowReachFrogIgnoresDistantSlime(GameTestHelper helper) {
        floorPlot(helper, 21);
        BlockPos frogPos = new BlockPos(2, 2, 2);
        BlockPos slimePos = new BlockPos(2, 2, 18); // 16 blocks away, far outside radius 8

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(Category.CAVE);
        frog.setReach(1); // radius 8

        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), slimePos);
        slime.setSize(1, true);
        slime.setVariant(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));

        helper.onEachTick(() -> {
            if (frog.getBrain().getMemory(
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_ATTACKABLE).orElse(null) == slime) {
                helper.fail("Reach-1 frog targeted a slime 16 blocks away - radius 8 should exclude it");
            }
        });
        helper.runAfterDelay(40L, helper::succeed);
    }

    /**
     * Brain activity-gating regression: a Resource Frog on land must settle into
     * the IDLE activity, not get stuck in SWIM. makeBrain adds the same-species
     * AnimalMakeLove with vanilla's exact IDLE/SWIM requirement sets; the bare
     * {@code addActivity} overload would PUT an empty requirement set, wiping the
     * land/water gating - and since FrogAi.updateActivity checks SWIM before IDLE,
     * a land frog would be permanently locked in SWIM. This pins the fix.
     */
    @GameTest(templateNamespace = ProductiveFrogs.MOD_ID, template = "empty_5x5x5", timeoutTicks = 40)
    public static void landFrogEntersIdleNotStuckInSwim(GameTestHelper helper) {
        floorPlot(helper, 5); // solid floor, no water -> unambiguously on land
        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(2, 2, 2));
        frog.setCategory(Category.CAVE);
        // By tick 20 the spawn long-jump cooldown is still active (so LONG_JUMP is
        // ineligible) and there's no prey/pregnancy, so the frog should be in IDLE.
        helper.runAfterDelay(20L, () -> {
            if (frog.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.SWIM)) {
                helper.fail("Resource Frog on land is stuck in SWIM - IDLE/SWIM activity requirements were clobbered");
                return;
            }
            if (!frog.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.IDLE)) {
                helper.fail("Resource Frog on land did not settle into the IDLE activity");
                return;
            }
            helper.succeed();
        });
    }

    /** Lay a stone floor across the full {@code 5 x length} plot base so test entities don't fall. */
    private static void floorPlot(GameTestHelper helper, int length) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < length; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }
}
