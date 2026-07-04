package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.PrimedFrogEggBlock;
import com.flatts.productivefrogs.content.block.entity.PrimedFrogEggBlockEntity;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ResourceTadpole;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.FrogKind;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Predator-tier breeding pins (#281, Phase 1): the four designated
 * resource-species crosses conceive their predator, predators breed true with
 * their own kind only, undesignated pairings refuse, the conceived kind
 * survives the egg -> tadpole -> frog pipeline (and the tadpole bucket), and
 * the {@code predators.enabled} config gate shuts the crosses off.
 */
final class PredatorBreedingTests {

    private PredatorBreedingTests() {
    }

    static void register() {
        PFGameTests.test("designated_cross_pairs_mate_and_conceive_their_predator", 40,
            PredatorBreedingTests::designatedCrossPairsMateAndConceiveTheirPredator);
        PFGameTests.test("predators_breed_true_with_own_kind_only", 40,
            PredatorBreedingTests::predatorsBreedTrueWithOwnKindOnly);
        PFGameTests.test("cross_conceived_predator_kind_survives_egg_to_frog", 100,
            PredatorBreedingTests::crossConceivedPredatorKindSurvivesEggToFrog);
        PFGameTests.test("predator_tadpole_bucket_round_trip_preserves_kind", 40,
            PredatorBreedingTests::predatorTadpoleBucketRoundTripPreservesKind);
        PFGameTests.test("predators_disabled_config_blocks_crosses_and_breed_true", 40,
            PredatorBreedingTests::predatorsDisabledConfigBlocksCrossesAndBreedTrue);
        PFGameTests.test("apex_crosses_conceive_and_breed_true", 40,
            PredatorBreedingTests::apexCrossesConceiveAndBreedTrue);
    }

    /**
     * Phase 4: the four designated cross-environment predator pairs conceive
     * their Apex (both directions); an Apex breeds true with its own kind only
     * and never back down the ladder (predator / species / Midas all refuse).
     */
    private static void apexCrossesConceiveAndBreedTrue(GameTestHelper helper) {
        for (FrogKind.Apex apex : FrogKind.Apex.values()) {
            ResourceFrog a = frogOf(helper, new BlockPos(2, 2, 1), apex.anchor());
            ResourceFrog b = frogOf(helper, new BlockPos(2, 2, 3), apex.partner());
            if (!a.canMate(b) || !b.canMate(a)) {
                helper.fail(apex.anchor().id() + " x " + apex.partner().id()
                    + " must mate in both directions (the " + apex.key() + " cross)");
                return;
            }
            a.spawnChildFromBreeding(helper.getLevel(), b);
            if (a.getPendingOffspringKind() != apex) {
                helper.fail("the " + apex.anchor().id() + " x " + apex.partner().id()
                    + " cross must conceive " + apex.id() + ", got " + a.getPendingOffspringKind().id());
                return;
            }
            a.discard();
            b.discard();
        }
        // Breed true + ladder gating.
        ResourceFrog w1 = frogOf(helper, new BlockPos(1, 2, 1), FrogKind.Apex.WITHER);
        ResourceFrog w2 = frogOf(helper, new BlockPos(1, 2, 3), FrogKind.Apex.WITHER);
        if (!w1.canMate(w2)) {
            helper.fail("two Wither Apexes must breed true");
            return;
        }
        w1.spawnChildFromBreeding(helper.getLevel(), w2);
        if (w1.getPendingOffspringKind() != FrogKind.Apex.WITHER) {
            helper.fail("Wither Apex x Wither Apex must conceive a Wither Apex");
            return;
        }
        ResourceFrog dragon = frogOf(helper, new BlockPos(3, 2, 1), FrogKind.Apex.DRAGON);
        if (w1.canMate(dragon)) {
            helper.fail("cross-kind Apex pairs must refuse");
            return;
        }
        ResourceFrog prowler = frogOf(helper, new BlockPos(3, 2, 3), FrogKind.Predator.PROWLER);
        if (w1.canMate(prowler) || prowler.canMate(w1)) {
            helper.fail("an Apex must never mate back down the ladder with a predator");
            return;
        }
        ResourceFrog species = frogOf(helper, new BlockPos(2, 2, 2), FrogKind.resource(Category.BOG));
        if (w1.canMate(species) || species.canMate(w1)) {
            helper.fail("an Apex must never mate a species frog");
            return;
        }
        helper.succeed();
    }

    private static ResourceFrog frogOf(GameTestHelper helper, BlockPos pos, FrogKind kind) {
        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), pos);
        frog.setKind(kind);
        frog.setInLove(null);
        return frog;
    }

    /**
     * All four settled pairs (Bog x Cave -> Prowler, Infernal x Geode -> Cinder,
     * Tide x Bog -> Gulper, Void x Geode -> Rift) can mate in both directions,
     * and the real {@link ResourceFrog#spawnChildFromBreeding} conception
     * captures the mapped predator as the pending offspring kind.
     */
    private static void designatedCrossPairsMateAndConceiveTheirPredator(GameTestHelper helper) {
        for (FrogKind.Predator predator : FrogKind.Predator.values()) {
            ResourceFrog a = frogOf(helper, new BlockPos(2, 2, 1), FrogKind.resource(predator.anchor()));
            ResourceFrog b = frogOf(helper, new BlockPos(2, 2, 3), FrogKind.resource(predator.partner()));
            if (!a.canMate(b) || !b.canMate(a)) {
                helper.fail(predator.anchor() + " x " + predator.partner()
                    + " must mate in both directions (the " + predator.key() + " cross)");
                return;
            }
            a.spawnChildFromBreeding(helper.getLevel(), b);
            if (a.getPendingOffspringKind() != predator) {
                helper.fail("the " + predator.anchor() + " x " + predator.partner()
                    + " cross must conceive " + predator.id() + ", got " + a.getPendingOffspringKind().id());
                return;
            }
            a.discard();
            b.discard();
        }
        // An undesignated resource pair still refuses (the pre-2.0 gate holds).
        ResourceFrog c = frogOf(helper, new BlockPos(2, 2, 1), FrogKind.resource(Category.CAVE));
        ResourceFrog d = frogOf(helper, new BlockPos(2, 2, 3), FrogKind.resource(Category.TIDE));
        if (c.canMate(d)) {
            helper.fail("CAVE x TIDE is not a designated cross and must refuse");
            return;
        }
        helper.succeed();
    }

    /**
     * Predator breeding: same kind mates and conceives breed-true offspring;
     * a different predator kind, the anchor species, and Midas all refuse.
     */
    private static void predatorsBreedTrueWithOwnKindOnly(GameTestHelper helper) {
        ResourceFrog a = frogOf(helper, new BlockPos(2, 2, 1), FrogKind.Predator.PROWLER);
        ResourceFrog b = frogOf(helper, new BlockPos(2, 2, 3), FrogKind.Predator.PROWLER);
        if (!a.canMate(b)) {
            helper.fail("two Prowlers must breed true");
            return;
        }
        a.spawnChildFromBreeding(helper.getLevel(), b);
        if (a.getPendingOffspringKind() != FrogKind.Predator.PROWLER) {
            helper.fail("Prowler x Prowler must conceive a Prowler, got " + a.getPendingOffspringKind().id());
            return;
        }
        ResourceFrog cinder = frogOf(helper, new BlockPos(3, 2, 2), FrogKind.Predator.CINDER);
        if (a.canMate(cinder)) {
            helper.fail("Prowler x Cinder (cross-kind predators) must refuse");
            return;
        }
        ResourceFrog bogSpecies = frogOf(helper, new BlockPos(1, 2, 2), FrogKind.resource(Category.BOG));
        if (a.canMate(bogSpecies) || bogSpecies.canMate(a)) {
            helper.fail("a predator must never mate back down the ladder with a species frog");
            return;
        }
        ResourceFrog midas = frogOf(helper, new BlockPos(2, 2, 2), FrogKind.MIDAS);
        if (a.canMate(midas) || midas.canMate(a)) {
            helper.fail("a predator must never mate a Midas frog");
            return;
        }
        helper.succeed();
    }

    /**
     * The conceived predator kind survives the whole pipeline: the lay-equivalent
     * egg (anchor-species carrier + BE kind stamp, exactly what
     * {@code LayCategoryFrogspawn} places for a cross) hatches predator tadpoles
     * carrying the bred stats, and maturation produces a predator frog with them.
     */
    private static void crossConceivedPredatorKindSurvivesEggToFrog(GameTestHelper helper) {
        BlockPos eggPos = new BlockPos(2, 2, 2);
        helper.setBlock(eggPos.below(), Blocks.WATER);
        // The carrier a Prowler offspring rides: its anchor species' egg block.
        PrimedFrogEggBlock eggBlock = PFBlocks.primedEgg(FrogKind.Predator.PROWLER.fallbackCategory());
        helper.setBlock(eggPos, eggBlock);

        ServerLevel level = helper.getLevel();
        BlockPos absEggPos = helper.absolutePos(eggPos);
        if (!(level.getBlockEntity(absEggPos) instanceof PrimedFrogEggBlockEntity eggBe)) {
            helper.fail("primed egg has no BlockEntity");
            return;
        }
        eggBe.setKind(FrogKind.Predator.PROWLER);
        eggBe.setPendingStats(4, 6, 2);

        // Force the hatch through the block's own tick (the scheduled path).
        eggBlock.tick(level.getBlockState(absEggPos), level, absEggPos, level.getRandom());

        List<ResourceTadpole> tadpoles = helper.getEntities(PFEntities.RESOURCE_TADPOLE.get());
        if (tadpoles.isEmpty()) {
            helper.fail("expected hatched tadpoles");
            return;
        }
        for (ResourceTadpole tadpole : tadpoles) {
            if (tadpole.getKind() != FrogKind.Predator.PROWLER) {
                helper.fail("hatched tadpole kind is " + tadpole.getKind().id() + ", expected predator/prowler");
                return;
            }
        }

        // Mature one and confirm the frog keeps the kind + the bred stats.
        ResourceTadpole first = tadpoles.get(0);
        first.ageUp();
        List<ResourceFrog> frogs = helper.getEntities(PFEntities.RESOURCE_FROG.get());
        if (frogs.isEmpty()) {
            helper.fail("tadpole did not mature into a frog");
            return;
        }
        ResourceFrog frog = frogs.get(0);
        if (frog.getKind() != FrogKind.Predator.PROWLER) {
            helper.fail("matured frog kind is " + frog.getKind().id() + ", expected predator/prowler");
            return;
        }
        if (frog.getAppetite() != 4 || frog.getBounty() != 6 || frog.getReach() != 2) {
            helper.fail("matured predator lost its bred stats: A" + frog.getAppetite()
                + "/B" + frog.getBounty() + "/R" + frog.getReach() + ", expected A4/B6/R2");
            return;
        }
        helper.succeed();
    }

    /** The tadpole bucket round-trips the predator kind (the #210 whole-identity lesson). */
    private static void predatorTadpoleBucketRoundTripPreservesKind(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.WATER);

        ResourceTadpole source = helper.spawn(PFEntities.RESOURCE_TADPOLE.get(), pos);
        source.setKind(FrogKind.Predator.RIFT);

        ItemStack bucket = new ItemStack(PFItems.RESOURCE_TADPOLE_BUCKET.get());
        source.saveToBucketTag(bucket);

        ResourceTadpole released = helper.spawn(PFEntities.RESOURCE_TADPOLE.get(), pos.east());
        released.setKind(FrogKind.resource(Category.BOG));
        net.minecraft.world.item.component.CustomData data =
            bucket.get(net.minecraft.core.component.DataComponents.BUCKET_ENTITY_DATA);
        if (data == null) {
            helper.fail("bucket's BUCKET_ENTITY_DATA is null after saveToBucketTag");
            return;
        }
        released.loadFromBucketTag(data.copyTag());
        if (released.getKind() != FrogKind.Predator.RIFT) {
            helper.fail("bucket round-trip lost the predator kind: got " + released.getKind().id());
            return;
        }
        helper.succeed();
    }

    /**
     * {@code predators.enabled=false} (#281 config gate): the designated crosses
     * AND predator breed-true pairings refuse; a same-species pair still mates
     * (the pre-predation behavior is untouched).
     */
    private static void predatorsDisabledConfigBlocksCrossesAndBreedTrue(GameTestHelper helper) {
        PFConfig.predatorsEnabledOverride = Boolean.FALSE;
        try {
            ResourceFrog bog = frogOf(helper, new BlockPos(2, 2, 1), FrogKind.resource(Category.BOG));
            ResourceFrog cave = frogOf(helper, new BlockPos(2, 2, 3), FrogKind.resource(Category.CAVE));
            if (bog.canMate(cave)) {
                helper.fail("with predators.enabled=false the BOG x CAVE cross must refuse");
                return;
            }
            ResourceFrog p1 = frogOf(helper, new BlockPos(3, 2, 1), FrogKind.Predator.GULPER);
            ResourceFrog p2 = frogOf(helper, new BlockPos(3, 2, 3), FrogKind.Predator.GULPER);
            if (p1.canMate(p2)) {
                helper.fail("with predators.enabled=false predator breed-true must refuse");
                return;
            }
            ResourceFrog bog2 = frogOf(helper, new BlockPos(1, 2, 3), FrogKind.resource(Category.BOG));
            if (!bog.canMate(bog2)) {
                helper.fail("with predators.enabled=false a same-species pair must still mate");
                return;
            }
            helper.succeed();
        } finally {
            PFConfig.predatorsEnabledOverride = null;
        }
    }
}
