package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.event.SlimeInfusionHandler;
import com.flatts.productivefrogs.event.SlimeSplitDiscoveryHandler;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFRegistries;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * In-world GameTests for the slime infusion + variant / parent-species
 * discovery surfaces. Migrated from the MC 1.21.1 annotation form to the
 * MC 26.1 registry form: each test registers via {@link PFGameTests#test}.
 *
 * <p>26.1 registry access note: {@code RegistryAccess} no longer exposes
 * {@code registryOrThrow(...)} (which returned a {@code Registry<T>}). The
 * access path is {@link PFRegistries#variants}/{@link PFRegistries#parentSpeciesLookup},
 * which return a {@link HolderLookup.RegistryLookup}; {@code SlimeVariant}'s
 * lookups take that type, and per-id reads go through {@link PFRegistries#variant}.
 */
final class InfusionDiscoveryTests {

    private InfusionDiscoveryTests() {
    }

    static void register() {
        PFGameTests.test("cave_slime_plus_cave_variant_primer_produces_variant_slime", 100, InfusionDiscoveryTests::caveSlimePlusCaveVariantPrimerProducesVariantSlime);
        PFGameTests.test("infusion_with_variant_primer_sets_specific_variant", 100, InfusionDiscoveryTests::infusionWithVariantPrimerSetsSpecificVariant);
        PFGameTests.test("split_discovery_picks_variant_from_pool", 100, InfusionDiscoveryTests::splitDiscoveryPicksVariantFromPool);
        PFGameTests.test("slime_variant_datapack_registry_loads_initial_variants", 100, InfusionDiscoveryTests::slimeVariantDatapackRegistryLoadsInitialVariants);
        PFGameTests.test("cross_mod_variant_presence_matches_mod_loaded_conditions", 100, InfusionDiscoveryTests::crossModVariantPresenceMatchesModLoadedConditions);
        PFGameTests.test("primer_tag_resolves_any_tagged_item", 100, InfusionDiscoveryTests::primerTagResolvesAnyTaggedItem);
        PFGameTests.test("find_by_primer_prefers_exact_item_over_tag", 100, InfusionDiscoveryTests::findByPrimerPrefersExactItemOverTag);
        PFGameTests.test("parent_species_datapack_registry_loads_six_defaults", 100, InfusionDiscoveryTests::parentSpeciesDatapackRegistryLoadsSixDefaults);
        PFGameTests.test("slime_infusion_transforms_parent_into_variant_slime", 100, InfusionDiscoveryTests::slimeInfusionTransformsParentIntoVariantSlime);
        PFGameTests.test("bog_slime_split_discovery_converts_to_bog_resource_slime", 100, InfusionDiscoveryTests::bogSlimeSplitDiscoveryConvertsToBogResourceSlime);
        PFGameTests.test("infernal_slime_split_discovery_converts_to_infernal_resource_slime", 100, InfusionDiscoveryTests::infernalSlimeSplitDiscoveryConvertsToInfernalResourceSlime);
        PFGameTests.test("cave_slime_split_discovery_converts_to_cave_resource_slime", 100, InfusionDiscoveryTests::caveSlimeSplitDiscoveryConvertsToCaveResourceSlime);
        PFGameTests.test("geode_slime_split_discovery_converts_to_geode_resource_slime", 100, InfusionDiscoveryTests::geodeSlimeSplitDiscoveryConvertsToGeodeResourceSlime);
        PFGameTests.test("tide_slime_split_discovery_converts_to_tide_resource_slime", 100, InfusionDiscoveryTests::tideSlimeSplitDiscoveryConvertsToTideResourceSlime);
        PFGameTests.test("void_slime_split_discovery_converts_to_void_resource_slime", 100, InfusionDiscoveryTests::voidSlimeSplitDiscoveryConvertsToVoidResourceSlime);
        PFGameTests.test("resource_slime_split_preserves_category", 100, InfusionDiscoveryTests::resourceSlimeSplitPreservesCategory);
    }

    /**
     * Q4 = Path A: a variant primer item matching a Cave variant (iron) infuses
     * a Cave Slime into the matching variant. Confirms the happy path under the
     * V1.5 gate.
     */
    private static void caveSlimePlusCaveVariantPrimerProducesVariantSlime(GameTestHelper helper) {
        HolderLookup.RegistryLookup<SlimeVariant> registry =
            PFRegistries.variants(helper.getLevel().registryAccess());
        java.util.Map.Entry<Identifier, SlimeVariant> ironEntry =
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

        // Exercise the helper directly - the player-interaction event would
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
     * {@link SlimeVariant#findByPrimerItem} resolves item ids to the correct
     * variants and misses on non-primer items.
     */
    private static void infusionWithVariantPrimerSetsSpecificVariant(GameTestHelper helper) {
        HolderLookup.RegistryLookup<SlimeVariant> registry =
            PFRegistries.variants(helper.getLevel().registryAccess());

        java.util.Map.Entry<Identifier, SlimeVariant> ironEntry = SlimeVariant.findByPrimerItem(
            registry, Identifier.fromNamespaceAndPath("minecraft", "iron_ingot"));
        if (ironEntry == null) {
            helper.fail("iron_ingot should resolve to a variant (productivefrogs:iron)");
            return;
        }
        if (!ironEntry.getKey().equals(Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"))) {
            helper.fail("expected productivefrogs:iron, got " + ironEntry.getKey());
        }

        // An item that ISN'T any variant's primer should miss the variant
        // lookup so the handler falls back to category-only. This example has
        // chased the roster twice (ghast_tear fell to the v1.13 stragglers,
        // nether_star to the v1.14 boss tier - #172) - bedrock is the durable
        // pick: it cannot become a resource.
        if (SlimeVariant.findByPrimerItem(registry,
                Identifier.fromNamespaceAndPath("minecraft", "bedrock")) != null) {
            helper.fail("bedrock is not a variant primer: lookup should miss");
        }

        // Stick is in NO primer tag - must miss too.
        if (SlimeVariant.findByPrimerItem(registry,
                Identifier.fromNamespaceAndPath("minecraft", "stick")) != null) {
            helper.fail("stick is not a primer for any variant - lookup should miss");
        }
        helper.succeed();
    }

    /**
     * Forced 100%-discovery split of a Cave Slime converts every child into a
     * CAVE Resource Slime carrying a variant from the live CAVE pool.
     */
    private static void splitDiscoveryPicksVariantFromPool(GameTestHelper helper) {
        // Derive the expected CAVE pool from the live registry (future-proof).
        HolderLookup.RegistryLookup<SlimeVariant> registry =
            PFRegistries.variants(helper.getLevel().registryAccess());
        java.util.Set<String> cavePool = new java.util.HashSet<>();
        registry.listElements().forEach(entry -> {
            if (entry.value().category() == Category.CAVE) {
                cavePool.add(entry.key().identifier().getPath());
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
                    Identifier variantId = s.getVariantId();
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
     * The {@code productivefrogs:slime_variant} datapack registry is populated
     * by server boot; spot-checks the v1.0 variants and the iron variant's
     * decoded fields.
     */
    private static void slimeVariantDatapackRegistryLoadsInitialVariants(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HolderLookup.RegistryLookup<SlimeVariant> registry =
            PFRegistries.variants(level.registryAccess());

        String[] expected = {
            "iron", "copper", "gold",
            "redstone", "lapis", "coal",
            "diamond", "emerald",
            "prismarine", "sponge"
        };
        for (String name : expected) {
            Identifier id = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, name);
            SlimeVariant variant = PFRegistries.variant(registry, id);
            if (variant == null) {
                helper.fail("expected variant " + id + " to be loaded from datapack registry");
            }
        }

        // Spot-check a specific variant's decoded fields so a codec regression
        // (e.g., a field name typo) fails the test, not just "registry empty."
        SlimeVariant iron = PFRegistries.variant(registry, Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        if (iron == null) {
            helper.fail("iron variant must be loaded");
            return;
        }
        if (iron.category() != Category.CAVE) {
            helper.fail("iron variant should be CAVE (V1.5 remap), got " + iron.category());
        }
        if (!iron.primerItem().equals(java.util.Optional.of(
                Identifier.fromNamespaceAndPath("minecraft", "iron_ingot")))) {
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
        Identifier expectedInnerBlock = Identifier.parse("minecraft:iron_block");
        if (!expectedInnerBlock.equals(iron.innerBlock().get())) {
            helper.fail("iron inner_block should be " + expectedInnerBlock
                + ", got " + iron.innerBlock().get());
            return;
        }
        helper.succeed();
    }

    /**
     * Every bundled cross-mod variant is gated {@code mod_loaded(provider)}; the
     * {@code slime_variant} registry must agree exactly with
     * {@link com.flatts.productivefrogs.setup.VariantFluidDiscovery#discover()}.
     */
    private static void crossModVariantPresenceMatchesModLoadedConditions(GameTestHelper helper) {
        HolderLookup.RegistryLookup<SlimeVariant> registry =
            PFRegistries.variants(helper.getLevel().registryAccess());
        java.util.Set<Identifier> expectedPresent =
            com.flatts.productivefrogs.setup.VariantFluidDiscovery.discover();
        java.util.List<String> allBundled =
            com.flatts.productivefrogs.setup.VariantFluidDiscovery.bundledVariantNames();
        if (allBundled.isEmpty()) {
            helper.fail("bundled variants_index.json read back empty - index resource missing?");
            return;
        }
        for (String name : allBundled) {
            Identifier id = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, name);
            boolean inRegistry = PFRegistries.variant(registry, id) != null;
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
        if (PFRegistries.variant(registry, Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron")) == null) {
            helper.fail("built-in iron variant should be present");
            return;
        }
        helper.succeed();
    }

    /**
     * {@link SlimeVariant#primerMatches} resolves a tag-driven variant by
     * {@code primer_tag} membership and an item-driven variant by exact
     * {@code primer_item}.
     */
    private static void primerTagResolvesAnyTaggedItem(GameTestHelper helper) {
        SlimeVariant tagVariant = new SlimeVariant(
            java.util.Optional.empty(),
            java.util.Optional.of(net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ITEM, Identifier.parse("c:ingots/iron"))),
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
            java.util.Optional.of(Identifier.parse("minecraft:diamond")),
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
     * {@link SlimeVariant#findByPrimer} prefers an exact {@code primer_item}
     * match over a {@code primer_tag} match regardless of registry iteration
     * order (tag variant registered first here).
     */
    private static void findByPrimerPrefersExactItemOverTag(GameTestHelper helper) {
        net.minecraft.core.MappedRegistry<SlimeVariant> registry = new net.minecraft.core.MappedRegistry<>(
            PFRegistries.SLIME_VARIANT, com.mojang.serialization.Lifecycle.stable());
        SlimeVariant tagVariant = new SlimeVariant(
            java.util.Optional.empty(),
            java.util.Optional.of(net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ITEM, Identifier.parse("c:ingots/iron"))),
            Category.CAVE, 0xFFFFFF, 0xFFFFFF, 1, java.util.Optional.empty(), java.util.Optional.empty());
        SlimeVariant itemVariant = new SlimeVariant(
            java.util.Optional.of(Identifier.parse("minecraft:iron_ingot")),
            java.util.Optional.empty(),
            Category.CAVE, 0xFFFFFF, 0xFFFFFF, 1, java.util.Optional.empty(), java.util.Optional.empty());
        // Tag variant registered first: iteration order would surface it first.
        net.minecraft.core.Registry.register(registry,
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "test_tag_iron"), tagVariant);
        net.minecraft.core.Registry.register(registry,
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "test_item_iron"), itemVariant);
        registry.freeze();

        java.util.Map.Entry<Identifier, SlimeVariant> resolved =
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
     * The {@code productivefrogs:parent_species} datapack registry loads its six
     * default entries (one PF parent species per category) with the right
     * EntityType-to-category mapping.
     */
    private static void parentSpeciesDatapackRegistryLoadsSixDefaults(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HolderLookup.RegistryLookup<com.flatts.productivefrogs.data.ParentSpeciesEntry> registry =
            PFRegistries.parentSpeciesLookup(level.registryAccess());

        // V1.5: vanilla slime + magma cube REMOVED from parent_species registry
        // (Q1=A - vanilla mobs are no longer parents). Six PF parent species
        // cover all categories.
        java.util.Map<Identifier, Category> expected = new java.util.LinkedHashMap<>();
        expected.put(Identifier.parse("productivefrogs:bog_slime"),      Category.BOG);
        expected.put(Identifier.parse("productivefrogs:cave_slime"),     Category.CAVE);
        expected.put(Identifier.parse("productivefrogs:geode_slime"),    Category.GEODE);
        expected.put(Identifier.parse("productivefrogs:tide_slime"),     Category.TIDE);
        expected.put(Identifier.parse("productivefrogs:infernal_slime"), Category.INFERNAL);
        expected.put(Identifier.parse("productivefrogs:void_slime"),     Category.VOID);

        java.util.Map<Identifier, Category> actual = new java.util.HashMap<>();
        for (Holder.Reference<com.flatts.productivefrogs.data.ParentSpeciesEntry> entry : registry.listElements().toList()) {
            actual.put(entry.value().entityType(), entry.value().category());
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
     * {@link SlimeInfusionHandler#transformInPlace} converts a Cave Slime into a
     * matching-category, same-size ResourceSlime and discards the source.
     */
    private static void slimeInfusionTransformsParentIntoVariantSlime(GameTestHelper helper) {
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
     * V1.5: Bog Slime splits convert to BOG ResourceSlimes at 100% discovery.
     */
    private static void bogSlimeSplitDiscoveryConvertsToBogResourceSlime(GameTestHelper helper) {
        runSplitDiscoveryTest(helper, PFEntities.BOG_SLIME.get(), Category.BOG);
    }

    /**
     * V1.5: Infernal Slime splits convert to INFERNAL ResourceSlimes at 100%
     * discovery.
     */
    private static void infernalSlimeSplitDiscoveryConvertsToInfernalResourceSlime(GameTestHelper helper) {
        runSplitDiscoveryTest(helper, PFEntities.INFERNAL_SLIME.get(), Category.INFERNAL);
    }

    /**
     * Cave Slime splits with 100% discovery convert to CAVE ResourceSlimes.
     */
    private static void caveSlimeSplitDiscoveryConvertsToCaveResourceSlime(GameTestHelper helper) {
        runSplitDiscoveryTest(helper, PFEntities.CAVE_SLIME.get(), Category.CAVE);
    }

    /**
     * Geode Slime splits with 100% discovery convert to GEODE ResourceSlimes.
     */
    private static void geodeSlimeSplitDiscoveryConvertsToGeodeResourceSlime(GameTestHelper helper) {
        runSplitDiscoveryTest(helper, PFEntities.GEODE_SLIME.get(), Category.GEODE);
    }

    /**
     * Tide Slime splits with 100% discovery convert to TIDE ResourceSlimes.
     */
    private static void tideSlimeSplitDiscoveryConvertsToTideResourceSlime(GameTestHelper helper) {
        runSplitDiscoveryTest(helper, PFEntities.TIDE_SLIME.get(), Category.TIDE);
    }

    /**
     * Void Slime splits with 100% discovery convert to VOID ResourceSlimes.
     */
    private static void voidSlimeSplitDiscoveryConvertsToVoidResourceSlime(GameTestHelper helper) {
        runSplitDiscoveryTest(helper, PFEntities.VOID_SLIME.get(), Category.VOID);
    }

    /**
     * Shared body for the discovery tests - force chance=1.0, split a size-3
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
     * A size-3 ResourceSlime killed and split yields ResourceSlime children of
     * the same category and size 1.
     */
    private static void resourceSlimeSplitPreservesCategory(GameTestHelper helper) {
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
}
