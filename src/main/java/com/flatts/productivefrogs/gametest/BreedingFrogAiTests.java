package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.PrimedFrogEggBlock;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.content.entity.ResourceTadpole;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * In-world breeding, frog-stat, lay-geometry, AI-targeting, and lily-pad-perch
 * regression pins (ported from the 1.21.1 {@code PFGameTests} monolith to the
 * 26.1 split-file registrar). Each body is registered through
 * {@link PFGameTests#test(String, int, java.util.function.Consumer)}.
 */
final class BreedingFrogAiTests {

    private BreedingFrogAiTests() {
    }

    static void register() {
        PFGameTests.test("custom_slimes_size_1_hitbox_matches_vanilla_slime", 100,
            BreedingFrogAiTests::customSlimesSize1HitboxMatchesVanillaSlime);
        PFGameTests.test("same_species_frogs_mate_but_cross_species_do_not", 20,
            BreedingFrogAiTests::sameSpeciesFrogsMateButCrossSpeciesDoNot);
        PFGameTests.test("resource_frog_is_persistent_after_finalize_spawn", 20,
            BreedingFrogAiTests::resourceFrogIsPersistentAfterFinalizeSpawn);
        PFGameTests.test("high_bounty_frog_drops_max_froglights", 100,
            BreedingFrogAiTests::highBountyFrogDropsMaxFroglights);
        PFGameTests.test("breeding_offspring_never_exceeds_better_parent_plus_one", 40,
            BreedingFrogAiTests::breedingOffspringNeverExceedsBetterParentPlusOne);
        PFGameTests.test("bred_stats_carry_from_egg_to_tadpole_unchanged", 100,
            BreedingFrogAiTests::bredStatsCarryFromEggToTadpoleUnchanged);
        PFGameTests.test("frog_lay_surface_resolves_from_mud_bank", 40,
            BreedingFrogAiTests::frogLaySurfaceResolvesFromMudBank);
        PFGameTests.test("frog_lay_surface_resolves_from_full_block_bank", 40,
            BreedingFrogAiTests::frogLaySurfaceResolvesFromFullBlockBank);
        PFGameTests.test("frog_lay_surface_resolves_when_submerged", 40,
            BreedingFrogAiTests::frogLaySurfaceResolvesWhenSubmerged);
        PFGameTests.test("frog_lays_egg_on_mud_bank_end_to_end", 40,
            BreedingFrogAiTests::frogLaysEggOnMudBankEndToEnd);
        PFGameTests.test("appetite_cooldown_gates_tongue_targeting", 80,
            BreedingFrogAiTests::appetiteCooldownGatesTongueTargeting);
        PFGameTests.test("low_bounty_frog_drops_one_froglight", 100,
            BreedingFrogAiTests::lowBountyFrogDropsOneFroglight);
        PFGameTests.test("high_reach_frog_targets_slime_beyond_vanilla_range", "empty_5x5x21", 200,
            BreedingFrogAiTests::highReachFrogTargetsSlimeBeyondVanillaRange);
        PFGameTests.test("low_reach_frog_ignores_distant_slime", "empty_5x5x21", 80,
            BreedingFrogAiTests::lowReachFrogIgnoresDistantSlime);
        PFGameTests.test("land_frog_enters_idle_not_stuck_in_swim", 40,
            BreedingFrogAiTests::landFrogEntersIdleNotStuckInSwim);
        PFGameTests.test("sweetslimed_lily_pad_claims_nearby_frog", 100,
            BreedingFrogAiTests::sweetslimedLilyPadClaimsNearbyFrog);
        PFGameTests.test("sweetslimed_lily_pad_pins_frog_to_centre_on_top", 100,
            BreedingFrogAiTests::sweetslimedLilyPadPinsFrogToCentreOnTop);
        PFGameTests.test("sweetslimed_lily_pad_claims_only_one_frog", 100,
            BreedingFrogAiTests::sweetslimedLilyPadClaimsOnlyOneFrog);
        PFGameTests.test("sweetslimed_lily_pad_releases_frog_when_broken", 160,
            BreedingFrogAiTests::sweetslimedLilyPadReleasesFrogWhenBroken);
    }

    // ---------------------------------------------------------------------
    // Hitbox parity
    // ---------------------------------------------------------------------

    private static void customSlimesSize1HitboxMatchesVanillaSlime(GameTestHelper helper) {
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
        // Tolerance handles the float-scale chain (base x size). 0.001 is
        // tight enough to catch any 4x regression while staying ahead of
        // legitimate float rounding.
        if (Math.abs(w - expectedWidth) > 0.001f) {
            helper.fail(name + " size-1 width " + w + " != vanilla " + expectedWidth);
        }
        if (Math.abs(h - expectedHeight) > 0.001f) {
            helper.fail(name + " size-1 height " + h + " != vanilla " + expectedHeight);
        }
    }

    // ---------------------------------------------------------------------
    // Frog stat breeding (docs/frog_breeding.md)
    // ---------------------------------------------------------------------

    /**
     * The same-species breeding gate (D4): two in-love Resource Frogs of the
     * same {@link Category} can mate, but a cross-species pair cannot (with the
     * default {@code breeding.sameSpeciesOnly}). The full breed -> lay -> hatch
     * cycle takes thousands of ticks and is verified manually in {@code runClient};
     * this pins the gate that {@link ResourceFrog#canMate} enforces.
     */
    private static void sameSpeciesFrogsMateButCrossSpeciesDoNot(GameTestHelper helper) {
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
    private static void resourceFrogIsPersistentAfterFinalizeSpawn(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), pos);
        frog.finalizeSpawn(
            helper.getLevel(),
            helper.getLevel().getCurrentDifficultyAt(frog.blockPosition()),
            net.minecraft.world.entity.EntitySpawnReason.NATURAL,
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
    private static void highBountyFrogDropsMaxFroglights(GameTestHelper helper) {
        BlockPos frogPos = new BlockPos(2, 2, 2);
        BlockPos slimePos = new BlockPos(3, 2, 2);

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(Category.CAVE);
        frog.setBounty(frog.getStatCap());

        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), slimePos);
        slime.setSize(1, true);
        // iron is a CAVE variant; setVariant syncs the slime's category to match.
        slime.setVariant(Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));

        int expected = com.flatts.productivefrogs.PFConfig.STATS_BOUNTY_MAX_DROPS.get();
        slime.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(frog), 999.0F);

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
     * offspring stat above 2 ({@code blend(1,1)=1}, plus at most the +1 climb).
     * Drives the real {@link ResourceFrog#spawnChildFromBreeding} capture on live
     * entities, looped many times to exercise the RNG branches. Regression pin for
     * "first breed jumped a stat from 1 to 3" reports - if the capture ever inflates
     * a stat past blend+1, this fails.
     */
    private static void breedingOffspringNeverExceedsBetterParentPlusOne(GameTestHelper helper) {
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
                helper.fail("offspring of two 1/1/1 parents exceeded blend+1: A" + app + "/B" + bou + "/R" + rea
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
    private static void bredStatsCarryFromEggToTadpoleUnchanged(GameTestHelper helper) {
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
    // Frogspawn lay geometry (issue #270) - LayCategoryFrogspawn.findLaySurface.
    // The lay target must resolve the adjacent/own water surface regardless of
    // sub-full footing (mud/slab/snow) or submersion, where vanilla's
    // blockPosition().below() math fails. Asserted on the lay-geometry seam
    // rather than by driving the full brain (which is fragile in-world).
    // =================================================================

    /**
     * The block position just above {@code support}'s collision top, in absolute
     * coords - where a frog resting on {@code support} actually sits. For mud
     * (collision top 0.875) this is below the next whole block, which is exactly
     * the sub-full footing that broke vanilla's lay search (#270).
     */
    private static void seatFrogOn(GameTestHelper helper, ResourceFrog frog, BlockPos supportRel, double collisionTop) {
        BlockPos support = helper.absolutePos(supportRel);
        frog.setPos(support.getX() + 0.5, support.getY() + collisionTop, support.getZ() + 0.5);
    }

    /**
     * #270: a frog on a MUD bank beside a pool must still find the water surface.
     * Mud's 0.875 collision top sinks the frog so {@code blockPosition()} floors a
     * whole block low; the footing-aware search must compensate and resolve the
     * adjacent source's surface.
     */
    private static void frogLaySurfaceResolvesFromMudBank(GameTestHelper helper) {
        BlockPos mud = new BlockPos(2, 1, 2);
        BlockPos water = new BlockPos(3, 1, 2);   // beside the mud, same level
        helper.setBlock(mud, Blocks.MUD);
        helper.setBlock(water, Blocks.WATER);     // source

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(2, 2, 2));
        frog.setCategory(Category.CAVE);
        seatFrogOn(helper, frog, mud, 0.875);     // mud collision top

        BlockPos got = com.flatts.productivefrogs.content.entity.ai.LayCategoryFrogspawn
            .findLaySurface(helper.getLevel(), frog);
        BlockPos expected = helper.absolutePos(water.above());
        if (got == null) {
            helper.fail("no lay target found from a mud bank (issue #270 regression)");
            return;
        }
        if (!got.equals(expected)) {
            helper.fail("mud-bank lay target " + got + " != expected surface " + expected);
            return;
        }
        helper.succeed();
    }

    /**
     * Control for #270: a frog on a full solid block beside a pool resolves the
     * adjacent surface (the case that always worked). Guards against the footing
     * rewrite breaking the common bank lay.
     */
    private static void frogLaySurfaceResolvesFromFullBlockBank(GameTestHelper helper) {
        BlockPos bank = new BlockPos(2, 1, 2);
        BlockPos water = new BlockPos(3, 1, 2);
        helper.setBlock(bank, Blocks.STONE);
        helper.setBlock(water, Blocks.WATER);

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(2, 2, 2));
        frog.setCategory(Category.CAVE);
        seatFrogOn(helper, frog, bank, 1.0);      // full block top

        BlockPos got = com.flatts.productivefrogs.content.entity.ai.LayCategoryFrogspawn
            .findLaySurface(helper.getLevel(), frog);
        BlockPos expected = helper.absolutePos(water.above());
        if (got == null) {
            helper.fail("no lay target found from a full-block bank (control should always work)");
            return;
        }
        if (!got.equals(expected)) {
            helper.fail("full-block-bank lay target " + got + " != expected surface " + expected);
            return;
        }
        helper.succeed();
    }

    /**
     * Beyond vanilla (#270): a SUBMERGED frog lays at the surface of its own
     * column. Vanilla bails on {@code isInWater()}; PF climbs to the source whose
     * block above is air. Two-deep pool, frog in the lower water block.
     */
    private static void frogLaySurfaceResolvesWhenSubmerged(GameTestHelper helper) {
        BlockPos floor = new BlockPos(2, 1, 2);
        BlockPos lower = new BlockPos(2, 2, 2);   // lower water block
        BlockPos upper = new BlockPos(2, 3, 2);   // surface source
        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(lower, Blocks.WATER);
        helper.setBlock(upper, Blocks.WATER);     // air at (2,4,2) is the surface

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), lower);
        frog.setCategory(Category.CAVE);
        // Seat the frog inside the lower water block (submerged).
        BlockPos abs = helper.absolutePos(lower);
        frog.setPos(abs.getX() + 0.5, abs.getY() + 0.1, abs.getZ() + 0.5);

        BlockPos got = com.flatts.productivefrogs.content.entity.ai.LayCategoryFrogspawn
            .findLaySurface(helper.getLevel(), frog);
        BlockPos expected = helper.absolutePos(upper.above());   // air above the surface source
        if (got == null) {
            helper.fail("submerged frog found no surface lay target (should climb its own column)");
            return;
        }
        if (!got.equals(expected)) {
            helper.fail("submerged lay target " + got + " != expected surface " + expected);
            return;
        }
        helper.succeed();
    }

    /**
     * End-to-end (#270): the full lay path - footing gate, surface search, and
     * the actual egg placement - drops a Primed Frog Egg on the pool surface for
     * a frog standing on a mud bank. Complements the geometry-seam tests above by
     * exercising {@code tryLayOnWaterSurface} (the gate + {@code level.setBlock})
     * rather than just the search, so a regression in the {@code onGround} gate or
     * the placement is caught too.
     */
    private static void frogLaysEggOnMudBankEndToEnd(GameTestHelper helper) {
        BlockPos mud = new BlockPos(2, 1, 2);
        BlockPos water = new BlockPos(3, 1, 2);
        helper.setBlock(mud, Blocks.MUD);
        helper.setBlock(water, Blocks.WATER);

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(2, 2, 2));
        frog.setCategory(Category.CAVE);
        seatFrogOn(helper, frog, mud, 0.875);
        // setPos alone leaves onGround false; the lay gate needs it true (physics
        // would set it once settled), so assert it for this in-place geometry.
        frog.setOnGround(true);

        boolean laid = com.flatts.productivefrogs.content.entity.ai.LayCategoryFrogspawn
            .tryLayOnWaterSurface(helper.getLevel(), frog);
        if (!laid) {
            helper.fail("frog on a mud bank did not lay (end-to-end, issue #270)");
            return;
        }
        BlockPos eggPos = helper.absolutePos(water.above());
        if (!(helper.getLevel().getBlockState(eggPos).getBlock()
                instanceof com.flatts.productivefrogs.content.block.PrimedFrogEggBlock)) {
            helper.fail("no Primed Frog Egg placed on the pool surface at " + water.above());
            return;
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
    private static void appetiteCooldownGatesTongueTargeting(GameTestHelper helper) {
        BlockPos frogPos = new BlockPos(2, 2, 2);
        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(Category.CAVE);
        frog.setStats(1, 1, 5); // Appetite 1 -> longest cooldown; Reach 5 keeps the adjacent slime in range.

        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), frogPos.east());
        slime.setSize(1, true);
        slime.setVariant(Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron")); // CAVE

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
    private static void lowBountyFrogDropsOneFroglight(GameTestHelper helper) {
        BlockPos frogPos = new BlockPos(2, 2, 2);
        BlockPos slimePos = new BlockPos(3, 2, 2);

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(Category.CAVE);
        frog.setBounty(1);

        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), slimePos);
        slime.setSize(1, true);
        slime.setVariant(Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));

        slime.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(frog), 999.0F);

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
    private static void highReachFrogTargetsSlimeBeyondVanillaRange(GameTestHelper helper) {
        floorPlot(helper, 21);
        BlockPos frogPos = new BlockPos(2, 2, 2);
        BlockPos slimePos = new BlockPos(2, 2, 14); // 12 blocks away (> vanilla's 10 cap, < radius 16)

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(Category.CAVE);
        frog.setReach(frog.getStatCap()); // radius 16 at the cap

        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), slimePos);
        slime.setSize(1, true);
        slime.setVariant(Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));

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
    private static void lowReachFrogIgnoresDistantSlime(GameTestHelper helper) {
        floorPlot(helper, 21);
        BlockPos frogPos = new BlockPos(2, 2, 2);
        BlockPos slimePos = new BlockPos(2, 2, 18); // 16 blocks away, far outside radius 8

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(Category.CAVE);
        frog.setReach(1); // radius 8

        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), slimePos);
        slime.setSize(1, true);
        slime.setVariant(Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));

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
    private static void landFrogEntersIdleNotStuckInSwim(GameTestHelper helper) {
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

    // =================================================================
    // Sweetslimed lily pad perch (#214, docs/lily_pad_perch.md)
    // =================================================================

    /**
     * Sweetslimed lily pad perch (#214, docs/lily_pad_perch.md): a placed pad claims
     * the nearest Resource Frog and pins it (the frog's {@code getActivePerch()} points
     * at the pad). The pad's BlockEntity ticker does the claiming on its scan interval,
     * so we let the world tick and assert the claim lands.
     */
    private static void sweetslimedLilyPadClaimsNearbyFrog(GameTestHelper helper) {
        BlockPos padPos = new BlockPos(2, 2, 2);
        helper.setBlock(padPos.below(), Blocks.WATER); // WaterlilyBlock needs water below to survive
        helper.setBlock(padPos, PFBlocks.SWEETSLIMED_LILY_PAD.get());

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(3, 2, 3));
        frog.setCategory(Category.CAVE);

        BlockPos absPad = helper.absolutePos(padPos);
        helper.succeedWhen(() ->
            helper.assertTrue(absPad.equals(frog.getActivePerch()),
                "frog should be claimed by the pad at " + absPad + ", perch=" + frog.getActivePerch()));
    }

    /**
     * The pad teleport-pins the frog's centre to the pad centre, on top (not below /
     * inside). The frog spawns on the pad so it intersects immediately; once claimed
     * it is held at the exact centre + pad-top Y.
     */
    private static void sweetslimedLilyPadPinsFrogToCentreOnTop(GameTestHelper helper) {
        BlockPos padPos = new BlockPos(2, 2, 2);
        helper.setBlock(padPos.below(), Blocks.WATER);
        helper.setBlock(padPos, PFBlocks.SWEETSLIMED_LILY_PAD.get());
        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), padPos);
        frog.setCategory(Category.CAVE);

        BlockPos absPad = helper.absolutePos(padPos);
        double cx = absPad.getX() + 0.5;
        double cz = absPad.getZ() + 0.5;
        double py = absPad.getY() + 0.09375; // pad top (matches PAD_TOP)
        helper.succeedWhen(() -> {
            helper.assertTrue(absPad.equals(frog.getActivePerch()), "frog not yet claimed");
            helper.assertTrue(
                Math.abs(frog.getX() - cx) < 0.2 && Math.abs(frog.getZ() - cz) < 0.2
                    && Math.abs(frog.getY() - py) < 0.25,
                "frog should be pinned to the pad centre/top (" + cx + ", " + py + ", " + cz
                    + "), was " + frog.position());
        });
    }

    /** One frog per pad: with two frogs near a single pad, exactly one is ever claimed. */
    private static void sweetslimedLilyPadClaimsOnlyOneFrog(GameTestHelper helper) {
        BlockPos padPos = new BlockPos(2, 2, 2);
        helper.setBlock(padPos.below(), Blocks.WATER);
        helper.setBlock(padPos, PFBlocks.SWEETSLIMED_LILY_PAD.get());
        helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(1, 2, 1)).setCategory(Category.CAVE);
        helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(3, 2, 3)).setCategory(Category.CAVE);

        BlockPos absPad = helper.absolutePos(padPos);
        helper.succeedWhen(() -> {
            long claimed = helper.getEntities(PFEntities.RESOURCE_FROG.get()).stream()
                .filter(f -> absPad.equals(f.getActivePerch())).count();
            helper.assertTrue(claimed == 1, "exactly one frog should be claimed by the pad, got " + claimed);
        });
    }

    /** Breaking the pad releases the frog: with nothing re-asserting the claim, it lapses. */
    private static void sweetslimedLilyPadReleasesFrogWhenBroken(GameTestHelper helper) {
        BlockPos padPos = new BlockPos(2, 2, 2);
        helper.setBlock(padPos.below(), Blocks.WATER);
        helper.setBlock(padPos, PFBlocks.SWEETSLIMED_LILY_PAD.get());
        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), padPos);
        frog.setCategory(Category.CAVE);

        BlockPos absPad = helper.absolutePos(padPos);
        // The frog is claimed within a scan interval; break the pad so nothing re-asserts.
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(absPad.equals(frog.getActivePerch()), "frog should be claimed before the pad is broken");
            helper.setBlock(padPos, Blocks.AIR);
        });
        // Claim TTL is 40 ticks; well after that with no pad, it must have lapsed.
        helper.runAfterDelay(130, () -> {
            helper.assertTrue(frog.getActivePerch() == null,
                "frog claim should lapse after the pad is broken, perch=" + frog.getActivePerch());
            helper.succeed();
        });
    }
}
