package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Frog tongue / Froglight drop GameTests, migrated from the MC 1.21.1
 * {@code PFGameTests} monolith to MC 26.1. Covers the variant-aware and
 * brewed-effect Froglight drop paths (tongue kill, Cleaver kill, direct-feed),
 * the category-filtered tongue sensor, the Frog Net capture round trip,
 * Princess's Kiss conversion, and Frog Leg death drops.
 *
 * <p>26.1 delta applied here: lethal damage is dealt via
 * {@code Entity.hurtServer(ServerLevel, DamageSource, float)} (the old
 * {@code hurt(DamageSource, float)} server-side path); the {@link ServerLevel}
 * is the one each test already obtains from {@code helper.getLevel()}.
 */
final class TongueDropsFroglightTests {

    private TongueDropsFroglightTests() {
    }

    static void register() {
        PFGameTests.test("variant_slime_kill_drops_configurable_froglight", 100, TongueDropsFroglightTests::variantSlimeKillDropsConfigurableFroglight);
        PFGameTests.test("brewed_slime_kill_stamps_effect_on_froglight", 100, TongueDropsFroglightTests::brewedSlimeKillStampsEffectOnFroglight);
        PFGameTests.test("froglight_cleaver_kill_drops_froglight", 100, TongueDropsFroglightTests::froglightCleaverKillDropsFroglight);
        PFGameTests.test("brewed_capture_picks_highest_amplifier", 100, TongueDropsFroglightTests::brewedCapturePicksHighestAmplifier);
        PFGameTests.test("brewed_froglight_aura_buffs_nearby_entity", 140, TongueDropsFroglightTests::brewedFroglightAuraBuffsNearbyEntity);
        PFGameTests.test("frog_net_preserves_species_and_stats", 20, TongueDropsFroglightTests::frogNetPreservesSpeciesAndStats);
        PFGameTests.test("frog_net_catches_vanilla_frog", 20, TongueDropsFroglightTests::frogNetCatchesVanillaFrog);
        PFGameTests.test("princess_kiss_converts_frog_to_villager", 60, TongueDropsFroglightTests::princessKissConvertsFrogToVillager);
        PFGameTests.test("killed_frog_drops_raw_legs", 60, TongueDropsFroglightTests::killedFrogDropsRawLegs);
        PFGameTests.test("burning_frog_drops_cooked_legs", 60, TongueDropsFroglightTests::burningFrogDropsCookedLegs);
        PFGameTests.test("brewed_froglight_toggled_off_does_not_buff", 140, TongueDropsFroglightTests::brewedFroglightToggledOffDoesNotBuff);
        PFGameTests.test("brewed_froglight_held_buffs_not_inventory", 120, TongueDropsFroglightTests::brewedFroglightHeldBuffsNotInventory);
        PFGameTests.test("frog_tongue_targets_only_matching_category_slime", 200, TongueDropsFroglightTests::frogTongueTargetsOnlyMatchingCategorySlime);
        PFGameTests.test("matching_frog_kill_drops_configurable_froglight", 100, TongueDropsFroglightTests::matchingFrogKillDropsConfigurableFroglight);
        PFGameTests.test("mismatched_frog_kill_drops_no_froglight", 100, TongueDropsFroglightTests::mismatchedFrogKillDropsNoFroglight);
        PFGameTests.test("direct_feed_category_only_bucket_is_no_op", 100, TongueDropsFroglightTests::directFeedCategoryOnlyBucketIsNoOp);
        PFGameTests.test("direct_feed_variant_slime_drops_configurable_froglight", 100, TongueDropsFroglightTests::directFeedVariantSlimeDropsConfigurableFroglight);
        PFGameTests.test("direct_feed_mismatched_category_is_a_no_op", 100, TongueDropsFroglightTests::directFeedMismatchedCategoryIsANoOp);
    }

    private static void variantSlimeKillDropsConfigurableFroglight(GameTestHelper helper) {
        BlockPos frogPos = new BlockPos(2, 2, 2);
        BlockPos slimePos = new BlockPos(3, 2, 2);

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        // V1.5: iron is a Cave variant — frog category must match the
        // slime's category for FrogTongueDropHandler to emit the drop.
        frog.setCategory(Category.CAVE);

        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), slimePos);
        slime.setSize(1, true);
        Identifier ironVariant = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");
        slime.setVariant(ironVariant);
        // setVariant syncs category from the registry. V1.5: iron is a Cave
        // variant (was Metallic / BOG pre-remap).
        if (slime.getCategory() != Category.CAVE) {
            helper.fail("setVariant(iron) should have synced category to CAVE, got " + slime.getCategory());
        }

        slime.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(frog), 999.0F);

        helper.succeedWhen(() -> {
            net.minecraft.world.item.Item expected = PFItems.CONFIGURABLE_FROGLIGHT.get();
            boolean found = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .anyMatch(itemEntity -> {
                    ItemStack stack = itemEntity.getItem();
                    if (!stack.is(expected)) return false;
                    Identifier variant = stack.get(
                        com.flatts.productivefrogs.registry.PFDataComponents.SLIME_VARIANT.get());
                    return ironVariant.equals(variant);
                });
            if (!found) {
                helper.fail("expected configurable_froglight stamped with iron variant to drop at frog");
            }
        });
    }

    private static void brewedSlimeKillStampsEffectOnFroglight(GameTestHelper helper) {
        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(2, 2, 2));
        frog.setCategory(Category.CAVE);
        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), new BlockPos(3, 2, 2));
        slime.setSize(1, true);
        slime.setVariant(Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        // Splash-potion stand-in: the effect is live on the slime when the frog eats it.
        slime.addEffect(new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.SPEED, 600, 0));
        slime.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(frog), 999.0F);

        helper.succeedWhen(() -> {
            boolean found = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .map(net.minecraft.world.entity.item.ItemEntity::getItem)
                .filter(s -> s.is(PFItems.CONFIGURABLE_FROGLIGHT.get()))
                .map(s -> s.get(com.flatts.productivefrogs.registry.PFDataComponents.STORED_EFFECT.get()))
                .anyMatch(e -> e != null
                    && e.effect().value() == net.minecraft.world.effect.MobEffects.SPEED.value()
                    && e.amplifier() == 0 && e.enabled());
            if (!found) {
                helper.fail("dropped Froglight should carry STORED_EFFECT(movement_speed, 0, enabled)");
            }
        });
    }

    private static void froglightCleaverKillDropsFroglight(GameTestHelper helper) {
        net.minecraft.world.entity.monster.zombie.Zombie killer =
            helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(1, 2, 2));
        killer.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
            new net.minecraft.world.item.ItemStack(PFItems.FROGLIGHT_CLEAVER.get()));
        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), new BlockPos(3, 2, 2));
        slime.setSize(1, true);
        slime.setVariant(Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        slime.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(killer), 999.0F);

        helper.succeedWhen(() -> {
            boolean found = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .map(net.minecraft.world.entity.item.ItemEntity::getItem)
                .filter(s -> s.is(PFItems.CONFIGURABLE_FROGLIGHT.get()))
                .anyMatch(s -> Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron")
                    .equals(s.get(com.flatts.productivefrogs.registry.PFDataComponents.SLIME_VARIANT.get())));
            if (!found) {
                helper.fail("the Cleaver kill should drop an iron-variant Froglight");
            }
        });
    }

    private static void brewedCapturePicksHighestAmplifier(GameTestHelper helper) {
        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(2, 2, 2));
        frog.setCategory(Category.CAVE);
        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), new BlockPos(3, 2, 2));
        slime.setSize(1, true);
        slime.setVariant(Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        slime.addEffect(new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.SPEED, 600, 0));
        slime.addEffect(new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.STRENGTH, 600, 1));
        slime.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(frog), 999.0F);

        helper.succeedWhen(() -> {
            boolean strengthFound = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .map(net.minecraft.world.entity.item.ItemEntity::getItem)
                .filter(s -> s.is(PFItems.CONFIGURABLE_FROGLIGHT.get()))
                .map(s -> s.get(com.flatts.productivefrogs.registry.PFDataComponents.STORED_EFFECT.get()))
                .anyMatch(e -> e != null
                    && e.effect().value() == net.minecraft.world.effect.MobEffects.STRENGTH.value()
                    && e.amplifier() == 1);
            if (!strengthFound) {
                helper.fail("pick rule should capture the higher-amplifier Strength II over Speed I");
            }
        });
    }

    private static void brewedFroglightAuraBuffsNearbyEntity(GameTestHelper helper) {
        BlockPos blockPos = new BlockPos(2, 1, 2);
        helper.setBlock(blockPos, PFBlocks.CONFIGURABLE_FROGLIGHT.get());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(blockPos))
                instanceof com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity be)) {
            helper.fail("configurable froglight did not create its BlockEntity");
            return;
        }
        be.setEffect(new com.flatts.productivefrogs.data.StoredEffect(
            net.minecraft.world.effect.MobEffects.SPEED, 0, true));
        net.minecraft.world.entity.animal.pig.Pig pig = helper.spawn(
            net.minecraft.world.entity.EntityType.PIG, new BlockPos(2, 2, 3));
        helper.succeedWhen(() -> helper.assertTrue(
            pig.hasEffect(net.minecraft.world.effect.MobEffects.SPEED),
            "enabled aura should apply Speed to a nearby entity"));
    }

    private static void frogNetPreservesSpeciesAndStats(GameTestHelper helper) {
        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(2, 2, 2));
        frog.setCategory(Category.GEODE);
        frog.setStats(7, 5, 9);

        net.minecraft.world.item.ItemStack net =
            new net.minecraft.world.item.ItemStack(PFItems.FROG_NET.get());
        com.flatts.productivefrogs.content.item.FrogNetItem.captureEntity(frog, net);
        frog.discard();

        if (!com.flatts.productivefrogs.content.item.FrogNetItem.isFilled(net)) {
            helper.fail("net should be filled after capture");
            return;
        }

        net.minecraft.world.entity.Entity rebuilt = com.flatts.productivefrogs.registry.PFItems.FROG_NET.get()
            .entityFromStack(net, helper.getLevel());
        if (!(rebuilt instanceof ResourceFrog restored)) {
            helper.fail("net should rebuild a Resource Frog on release, got " + rebuilt);
            return;
        }
        helper.assertTrue(restored.getCategory() == Category.GEODE,
            "released frog category should be GEODE, was " + restored.getCategory());
        helper.assertTrue(
            restored.getAppetite() == 7 && restored.getBounty() == 5 && restored.getReach() == 9,
            "released frog stats should be 7/5/9, were " + restored.getAppetite()
                + "/" + restored.getBounty() + "/" + restored.getReach());
        restored.discard();
        helper.succeed();
    }

    private static void frogNetCatchesVanillaFrog(GameTestHelper helper) {
        net.minecraft.world.entity.animal.frog.Frog frog =
            helper.spawn(net.minecraft.world.entity.EntityType.FROG, new BlockPos(2, 2, 2));

        if (!com.flatts.productivefrogs.content.item.FrogNetItem.isCatchable(frog)) {
            helper.fail("a vanilla frog should be catchable");
            return;
        }

        net.minecraft.world.item.ItemStack netStack =
            new net.minecraft.world.item.ItemStack(PFItems.FROG_NET.get());
        com.flatts.productivefrogs.content.item.FrogNetItem.captureEntity(frog, netStack);
        frog.discard();

        net.minecraft.world.entity.Entity rebuilt = com.flatts.productivefrogs.registry.PFItems.FROG_NET.get()
            .entityFromStack(netStack, helper.getLevel());
        if (rebuilt == null || rebuilt.getType() != net.minecraft.world.entity.EntityType.FROG) {
            helper.fail("net should rebuild a vanilla frog, got " + rebuilt);
            return;
        }
        rebuilt.discard();
        helper.succeed();
    }

    private static void princessKissConvertsFrogToVillager(GameTestHelper helper) {
        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(2, 2, 2));
        frog.setData(com.flatts.productivefrogs.registry.PFAttachments.PRINCESS_CONVERTING.get(), 2);

        helper.succeedWhen(() -> {
            boolean villager = !helper.getEntities(net.minecraft.world.entity.EntityType.VILLAGER).isEmpty();
            boolean frogGone = helper.getEntities(PFEntities.RESOURCE_FROG.get()).isEmpty();
            if (!villager || !frogGone) {
                helper.fail("the kiss conversion should replace the frog with a villager");
            }
        });
    }

    private static void killedFrogDropsRawLegs(GameTestHelper helper) {
        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(2, 2, 2));
        frog.setCategory(Category.CAVE);
        frog.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1000.0F);

        helper.succeedWhen(() -> {
            boolean dropped = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .map(net.minecraft.world.entity.item.ItemEntity::getItem)
                .anyMatch(s -> s.is(PFItems.RAW_FROG_LEGS.get()));
            if (!dropped) {
                helper.fail("a killed frog should drop raw frog legs");
            }
        });
    }

    private static void burningFrogDropsCookedLegs(GameTestHelper helper) {
        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(2, 2, 2));
        frog.setCategory(Category.CAVE);
        frog.igniteForSeconds(5);
        // Let the on-fire flag settle (set during the entity tick), then kill.
        helper.runAfterDelay(3, () ->
            frog.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1000.0F));

        helper.succeedWhen(() -> {
            boolean cooked = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .map(net.minecraft.world.entity.item.ItemEntity::getItem)
                .anyMatch(s -> s.is(PFItems.COOKED_FROG_LEGS.get()));
            if (!cooked) {
                helper.fail("a frog killed while on fire should drop cooked frog legs");
            }
        });
    }

    private static void brewedFroglightToggledOffDoesNotBuff(GameTestHelper helper) {
        BlockPos blockPos = new BlockPos(2, 1, 2);
        helper.setBlock(blockPos, PFBlocks.CONFIGURABLE_FROGLIGHT.get());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(blockPos))
                instanceof com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity be)) {
            helper.fail("configurable froglight did not create its BlockEntity");
            return;
        }
        be.setEffect(new com.flatts.productivefrogs.data.StoredEffect(
            net.minecraft.world.effect.MobEffects.SPEED, 0, true));
        be.toggleAura(); // now off
        if (be.isAuraActive()) {
            helper.fail("toggleAura should have disabled the aura");
            return;
        }
        net.minecraft.world.entity.animal.pig.Pig pig = helper.spawn(
            net.minecraft.world.entity.EntityType.PIG, new BlockPos(2, 2, 3));
        helper.runAfterDelay(100L, () -> {
            if (pig.hasEffect(net.minecraft.world.effect.MobEffects.SPEED)) {
                helper.fail("a toggled-off aura must not apply its effect");
            } else {
                helper.succeed();
            }
        });
    }

    private static void brewedFroglightHeldBuffsNotInventory(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player =
            helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        com.flatts.productivefrogs.content.item.ConfigurableFroglightItem item =
            (com.flatts.productivefrogs.content.item.ConfigurableFroglightItem) PFItems.CONFIGURABLE_FROGLIGHT.get();

        // Inventory (not held): tick the stack as a non-hand slot - must NOT buff.
        ItemStack inInventory = new ItemStack(item);
        inInventory.set(com.flatts.productivefrogs.registry.PFDataComponents.STORED_EFFECT.get(),
            new com.flatts.productivefrogs.data.StoredEffect(
                net.minecraft.world.effect.MobEffects.SPEED, 0, true));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
        for (int i = 0; i < 60; i++) {
            item.inventoryTick(inInventory, helper.getLevel(), player, null);
        }
        if (player.hasEffect(net.minecraft.world.effect.MobEffects.SPEED)) {
            helper.fail("a brewed Froglight in an inventory slot must not self-buff");
            return;
        }

        // Held in main hand: same stack now in hand - must buff within one pulse.
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, inInventory);
        helper.onEachTick(() -> item.inventoryTick(
            player.getMainHandItem(), helper.getLevel(), player, net.minecraft.world.entity.EquipmentSlot.MAINHAND));
        helper.succeedWhen(() -> helper.assertTrue(
            player.hasEffect(net.minecraft.world.effect.MobEffects.SPEED),
            "a held enabled brewed Froglight should self-buff the carrier"));
    }

    private static void frogTongueTargetsOnlyMatchingCategorySlime(GameTestHelper helper) {
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

    private static void matchingFrogKillDropsConfigurableFroglight(GameTestHelper helper) {
        Category cat = Category.TIDE;
        BlockPos frogPos = new BlockPos(2, 2, 2);
        BlockPos slimePos = new BlockPos(3, 2, 2);

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(cat);

        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), slimePos);
        slime.setSize(1, true);
        Identifier prismarine = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "prismarine");
        slime.setVariant(prismarine);

        slime.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(frog), 999.0F);

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

    private static void mismatchedFrogKillDropsNoFroglight(GameTestHelper helper) {
        BlockPos frogPos = new BlockPos(2, 2, 2);
        BlockPos slimePos = new BlockPos(3, 2, 2);

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(Category.BOG);

        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), slimePos);
        slime.setSize(1, true);
        // Blaze is an Infernal variant — categorically wrong for the Bog frog.
        slime.setVariant(Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "blaze"));

        slime.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(frog), 999.0F);

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

    private static void directFeedCategoryOnlyBucketIsNoOp(GameTestHelper helper) {
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

    private static void directFeedVariantSlimeDropsConfigurableFroglight(GameTestHelper helper) {
        BlockPos frogPos = new BlockPos(2, 2, 2);

        ResourceSlime source = helper.spawn(PFEntities.RESOURCE_SLIME.get(), new BlockPos(4, 2, 4));
        source.setSize(1, true);
        Identifier ironVariant = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");
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
                    Identifier variant = stack.get(
                        com.flatts.productivefrogs.registry.PFDataComponents.SLIME_VARIANT.get());
                    return ironVariant.equals(variant);
                });
            if (!found) {
                helper.fail("expected configurable_froglight stamped with iron variant to drop");
            }
        });
    }

    private static void directFeedMismatchedCategoryIsANoOp(GameTestHelper helper) {
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
}
