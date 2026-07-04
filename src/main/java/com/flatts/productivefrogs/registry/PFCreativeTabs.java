package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.data.Category;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Creative-mode tab registration. Productive Frogs gets a single dedicated tab
 * that aggregates every item the mod registers.
 *
 * <p><b>The accept ORDER here is the mod's public item ordering</b> (maintainer
 * ruling, 2026-07-03): JEI builds its ingredient list from the creative tabs'
 * display items (verified against JEI 29.x {@code ItemStackListFactory}), so
 * this sequence is what players scroll in BOTH the tab and JEI. Items are
 * grouped by what they are, in gameplay-progression order:
 *
 * <ol>
 *   <li>Tools and hand items (nets, treats, food, weapon)</li>
 *   <li>The frog lifecycle (egg bottles, frogspawn blocks, tadpole buckets)</li>
 *   <li>Slimes in buckets (the long per-variant run stays contiguous)</li>
 *   <li>Fluids and catalysts (milk per variant, mimic, slurry, XP, the four
 *       catalysts that buff them)</li>
 *   <li>Machines and automation (appliances, basins, EE machines, Terrarium)</li>
 *   <li>Froglights (the per-variant production output)</li>
 *   <li>Boss / endgame blocks (altars, receptacles, reinforced froglights)</li>
 *   <li>Spawn eggs (frogs, tadpoles, slimes - vanilla keeps eggs last too)</li>
 * </ol>
 *
 * Every config gate ({@code #196}/{@code #200}/{@code #201}/{@code #202} etc.)
 * is unchanged - only the order moved.
 */
public final class PFCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ProductiveFrogs.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> PRODUCTIVE_FROGS_TAB =
        CREATIVE_MODE_TABS.register(
            "productive_frogs",
            () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.productivefrogs"))
                .icon(() -> PFItems.FROG_EGG.get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    // Variant catalog is the SLIME_VARIANT datapack registry, read
                    // from the display params' holder lookup. Present when the tab
                    // is (re)built in-world; absent/empty at the title screen before
                    // a world's datapacks load, in which case the variant-driven
                    // groups below simply show nothing until a world is joined
                    // (standard for datapack-registry-driven creative entries).
                    var variantLookup = parameters.holders().lookup(PFRegistries.SLIME_VARIANT);

                    // ---------------- 1. Tools and hand items ----------------
                    // The Frog Net - catch/release tool (#205); hidden when
                    // config-disabled. Only the empty net is a creative entry.
                    if (PFConfig.frogNetEnabled()) {
                        output.accept(PFItems.FROG_NET.get());
                    }
                    // The Ender Net (#281 Phase 3) sits beside its sibling.
                    if (PFConfig.predatorsEnabled()) {
                        output.accept(PFItems.ENDER_NET.get());
                    }
                    // Sweetslime - the breeding treat; hidden when the frog-stat
                    // layer is off (#202).
                    if (PFConfig.frogStatsEnabled()) {
                        output.accept(PFItems.SWEETSLIME.get());
                    }
                    // Sweetslimed Lily Pad - the frog perch (#214).
                    if (PFConfig.lilyPadPerchEnabled()) {
                        output.accept(PFItems.SWEETSLIMED_LILY_PAD.get());
                    }
                    // Princess's Kiss - the Ender Dragon drop (#216).
                    if (PFConfig.princessKissEnabled()) {
                        output.accept(PFItems.PRINCESS_KISS.get());
                    }
                    // Froglight Cleaver (#212) - hidden when its own toggle OR the
                    // boss master is off (its recipe needs boss Froglights, #200).
                    if (PFConfig.froglightWeaponEnabled() && PFConfig.bossEnabled()) {
                        output.accept(PFItems.FROGLIGHT_CLEAVER.get());
                    }
                    // Frog Legs - the death-drop food chain (#194).
                    if (PFConfig.frogLegsEnabled()) {
                        output.accept(PFItems.RAW_FROG_LEGS.get());
                        output.accept(PFItems.COOKED_FROG_LEGS.get());
                        output.accept(PFItems.FROG_LEGS_SOUP.get());
                    }

                    // ---------------- 2. The frog lifecycle ----------------
                    // Default (unprimed) Frog Egg bottle, then one primed bottle per
                    // category (the JEI plugin subtypes by CONTAINED_CATEGORY).
                    output.accept(PFItems.FROG_EGG.get());
                    for (Category cat : Category.values()) {
                        ItemStack primed = new ItemStack(PFItems.FROG_EGG.get());
                        primed.set(PFDataComponents.CONTAINED_CATEGORY.get(), cat);
                        output.accept(primed);
                    }
                    // The placed frogspawn blocks: six species + Midas (#253, a
                    // frogspawn block, not a spawn egg).
                    for (var entry : PFItems.PRIMED_FROG_EGG_ITEMS.values()) {
                        output.accept(entry.get());
                    }
                    if (PFConfig.equivalenceEnabled()) {
                        output.accept(PFItems.MIDAS_FROG_EGG.get());
                    }
                    // Per-kind egg blocks (predators + apex, 2026-07-04 ruling).
                    if (PFConfig.predatorsEnabled()) {
                        for (var entry : PFItems.KIND_FROG_EGG_ITEMS.values()) {
                            output.accept(entry.get());
                        }
                    }
                    // Default empty Resource Tadpole Bucket, then one stamped per
                    // category (the JEI subtype keys off BUCKET_ENTITY_DATA).
                    output.accept(PFItems.RESOURCE_TADPOLE_BUCKET.get());
                    for (Category cat : Category.values()) {
                        output.accept(makeCategoryTadpoleBucket(cat));
                    }

                    // ---------------- 3. Slimes in buckets ----------------
                    // Default empty Slime Bucket, then one stamped per variant
                    // (mirrors ResourceSlime.saveToBucketTag).
                    output.accept(PFItems.SLIME_BUCKET.get());
                    variantLookup.ifPresent(reg -> reg.listElements().forEach(h -> {
                        if (h.value().isEnabled(h.key().identifier())) {
                            output.accept(PFItems.variantSlimeBucket(h.key().identifier(), h.value().category()));
                        }
                    }));
                    // Mimic Slime Bucket (#253) - the EE lane's slime, hidden when
                    // the lane is disabled.
                    if (PFConfig.equivalenceEnabled()) {
                        output.accept(PFItems.MIMIC_SLIME_BUCKET.get());
                    }

                    // ---------------- 4. Fluids and catalysts ----------------
                    // One Slime Milk bucket per registry variant that has a fluid
                    // (v1.8 -> R-1). A content-only variant has no bucket
                    // (slimeMilkBucket returns EMPTY) - skip it.
                    variantLookup.ifPresent(reg -> reg.listElements().forEach(h -> {
                        if (!h.value().isEnabled(h.key().identifier())) {
                            return;
                        }
                        ItemStack milk = PFItems.slimeMilkBucket(h.key().identifier());
                        if (!milk.isEmpty()) {
                            output.accept(milk);
                        }
                    }));
                    // Mimic Milk (#253), Mob Slurry + Liquid Experience (#281).
                    if (PFConfig.equivalenceEnabled()) {
                        output.accept(PFItems.MIMIC_MILK_BUCKET.get());
                    }
                    if (PFConfig.predatorsEnabled()) {
                        output.accept(PFItems.MOB_SLURRY_BUCKET.get());
                        output.accept(PFItems.LIQUID_EXPERIENCE_BUCKET.get());
                    }
                    // Slime Milk catalysts (buff a source / Basin). Each shows only
                    // when its own flag - ANDed with the catalysts master - is on
                    // (#201); the accessors fail open before the config spec loads.
                    if (PFConfig.catalystCountEnabled()) {
                        output.accept(PFItems.COUNT_CATALYST.get());
                    }
                    if (PFConfig.catalystSpeedEnabled()) {
                        output.accept(PFItems.SPEED_CATALYST.get());
                    }
                    if (PFConfig.catalystQuantityEnabled()) {
                        output.accept(PFItems.QUANTITY_CATALYST.get());
                    }
                    if (PFConfig.catalystInfiniteEnabled()) {
                        output.accept(PFItems.INFINITE_CATALYST.get());
                    }

                    // ---------------- 5. Machines and automation ----------------
                    // The slime production chain first (#196 gates as before) ...
                    if (!PFConfig.SPEC.isLoaded() || PFConfig.SLIME_MILKER_ENABLED.get()) {
                        output.accept(PFItems.SLIME_MILKER.get());
                    }
                    if (!PFConfig.SPEC.isLoaded() || PFConfig.SLIME_CHURN_ENABLED.get()) {
                        output.accept(PFItems.SLIME_CHURN.get());
                    }
                    // The Slime Milk Basin (#281 Phase 3) - slime-side, ungated.
                    output.accept(PFItems.SLIME_MILK_BASIN.get());
                    // ... then the mob (predation) chain (#281 Phase 3) ...
                    if (PFConfig.predatorsEnabled()) {
                        output.accept(PFItems.SLURRY_PRESS.get());
                        output.accept(PFItems.MOB_SLURRY_BASIN.get());
                    }
                    // ... the melt-and-cast lane (v1.12) ...
                    if (!PFConfig.SPEC.isLoaded() || PFConfig.CRUCIBLE_ENABLED.get()) {
                        output.accept(PFItems.CRUCIBLE.get());
                    }
                    if (!PFConfig.SPEC.isLoaded() || PFConfig.CASTING_MOLD_ENABLED.get()) {
                        output.accept(PFItems.CASTING_MOLD.get());
                    }
                    // ... the Equivalence lane's machines (#253) ...
                    if (PFConfig.equivalenceEnabled()) {
                        output.accept(PFItems.ALEMBIC.get());
                        output.accept(PFItems.DISTILLER.get());
                    }
                    // ... the Spawnery (skyblock bootstrap; off by default, #196;
                    // isLoaded guards the title-screen build) ...
                    if (PFConfig.SPEC.isLoaded() && PFConfig.SPAWNERY_ENABLED.get()) {
                        output.accept(PFItems.SPAWNERY.get());
                    }
                    // ... and the Terrarium multiblock set (#185).
                    output.accept(PFItems.TERRARIUM_CONTROLLER.get());
                    output.accept(PFItems.SPRINKLER.get());
                    output.accept(PFItems.INCUBATOR.get());
                    output.accept(PFItems.HATCH.get());

                    // ---------------- 6. Froglights ----------------
                    // One configurable_froglight per shipped variant - each stack
                    // carries its variant id (SLIME_VARIANT) so creative testers see
                    // exactly what the production loop produces.
                    variantLookup.ifPresent(reg -> reg.listElements().forEach(h -> {
                        if (!h.value().isEnabled(h.key().identifier())) {
                            return;
                        }
                        ItemStack stack = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
                        stack.set(PFDataComponents.SLIME_VARIANT.get(), h.key().identifier());
                        output.accept(stack);
                    }));

                    // ---------------- 7. Boss / endgame blocks ----------------
                    // Hidden when the boss master is off (#200); the boss variants'
                    // own entries hide via the per-variant isEnabled gate above.
                    if (PFConfig.bossEnabled()) {
                        output.accept(PFItems.NETHER_STAR_CATALYST.get());
                        output.accept(PFItems.DRAGON_EGG_CATALYST.get());
                        output.accept(PFItems.WITHER_SKELETON_SKULL_CATALYST.get());
                        output.accept(PFItems.DRAGON_BREATH_CATALYST.get());
                        // Dragon altar (#249): reinforced froglights, crystal socket, hatch.
                        output.accept(PFItems.REINFORCED_OBSIDIAN_FROGLIGHT.get());
                        output.accept(PFItems.REINFORCED_END_STONE_FROGLIGHT.get());
                        output.accept(PFItems.END_CRYSTAL_RECEPTACLE.get());
                        output.accept(PFItems.END_DRAGON_ALTAR_HATCH.get());
                        // Wither altar (#247): reinforced froglights, receptacles,
                        // hatch, and the Withered Star capstone.
                        output.accept(PFItems.REINFORCED_SOUL_SAND_FROGLIGHT.get());
                        output.accept(PFItems.REINFORCED_GLOWSTONE_FROGLIGHT.get());
                        output.accept(PFItems.SOUL_SAND_RECEPTACLE.get());
                        output.accept(PFItems.WITHER_SKULL_RECEPTACLE.get());
                        output.accept(PFItems.WITHER_ALTAR_HATCH.get());
                        output.accept(PFItems.WITHERED_STAR.get());
                        // Warden altar (#279): the Shrieker Pit - reinforced froglights,
                        // rim receptacles, hatch, and the Echoing Catalyst capstone.
                        output.accept(PFItems.REINFORCED_SCULK_FROGLIGHT.get());
                        output.accept(PFItems.REINFORCED_ECHO_SHARD_FROGLIGHT.get());
                        output.accept(PFItems.SHRIEKER_RECEPTACLE.get());
                        output.accept(PFItems.WARDEN_ALTAR_HATCH.get());
                        output.accept(PFItems.ECHOING_CATALYST.get());
                        // Elder Guardian altar (#280): the Monument Well - reinforced
                        // froglights, roof receptacles, hatch, and the Monument Core.
                        output.accept(PFItems.REINFORCED_PRISMARINE_FROGLIGHT.get());
                        output.accept(PFItems.REINFORCED_SPONGE_FROGLIGHT.get());
                        output.accept(PFItems.TIDE_OFFERING_RECEPTACLE.get());
                        output.accept(PFItems.ELDER_ALTAR_HATCH.get());
                        output.accept(PFItems.MONUMENT_CORE.get());
                    }

                    // ---------------- 8. Spawn eggs ----------------
                    // Frogs first (species, Midas, predators), tadpoles after
                    // (same order), then the slimes: per-variant Resource Slime
                    // eggs, then the six upstream parent species.
                    for (var entry : PFItems.RESOURCE_FROG_SPAWN_EGGS.values()) {
                        output.accept(entry.get());
                    }
                    if (PFConfig.equivalenceEnabled()) {
                        output.accept(PFItems.MIDAS_FROG_SPAWN_EGG.get());
                    }
                    if (PFConfig.predatorsEnabled()) {
                        for (var entry : PFItems.PREDATOR_FROG_SPAWN_EGGS.values()) {
                            output.accept(entry.get());
                        }
                        for (var entry : PFItems.APEX_FROG_SPAWN_EGGS.values()) {
                            output.accept(entry.get());
                        }
                    }
                    for (var entry : PFItems.RESOURCE_TADPOLE_SPAWN_EGGS.values()) {
                        output.accept(entry.get());
                    }
                    if (PFConfig.equivalenceEnabled()) {
                        output.accept(PFItems.MIDAS_TADPOLE_SPAWN_EGG.get());
                    }
                    if (PFConfig.predatorsEnabled()) {
                        for (var entry : PFItems.PREDATOR_TADPOLE_SPAWN_EGGS.values()) {
                            output.accept(entry.get());
                        }
                        for (var entry : PFItems.APEX_TADPOLE_SPAWN_EGGS.values()) {
                            output.accept(entry.get());
                        }
                    }
                    // One stamped stack per variant (no unstamped base egg - a
                    // variant-less Resource Slime egg isn't a meaningful entry).
                    variantLookup.ifPresent(reg -> reg.listElements().forEach(h -> {
                        if (h.value().isEnabled(h.key().identifier())) {
                            output.accept(PFItems.resourceSlimeSpawnEgg(h.key().identifier()));
                        }
                    }));
                    // Parent species spawn eggs - the upstream sources, last.
                    output.accept(PFItems.BOG_SLIME_SPAWN_EGG.get());
                    output.accept(PFItems.CAVE_SLIME_SPAWN_EGG.get());
                    output.accept(PFItems.GEODE_SLIME_SPAWN_EGG.get());
                    output.accept(PFItems.TIDE_SLIME_SPAWN_EGG.get());
                    output.accept(PFItems.INFERNAL_SLIME_SPAWN_EGG.get());
                    output.accept(PFItems.VOID_SLIME_SPAWN_EGG.get());
                })
                .build()
        );

    /**
     * Build a Resource Tadpole Bucket stamped with a category — mirrors
     * {@code ResourceTadpole.saveToBucketTag}. Tadpole buckets only carry
     * Category (no variant); the dynamic display name in
     * {@code ResourceTadpoleBucketItem.getName} reads this tag too.
     */
    private static ItemStack makeCategoryTadpoleBucket(Category category) {
        ItemStack stack = new ItemStack(PFItems.RESOURCE_TADPOLE_BUCKET.get());
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack, tag -> {
            // Same "Kind" dialect ResourceTadpole.saveToBucketTag writes, so the
            // creative stack and a real scooped bucket are one JEI subtype.
            tag.putString("Kind", com.flatts.productivefrogs.data.FrogKind.resource(category).id());
        });
        return stack;
    }

    private PFCreativeTabs() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
