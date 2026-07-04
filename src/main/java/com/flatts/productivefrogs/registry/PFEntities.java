package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.BogSlime;
import com.flatts.productivefrogs.content.entity.CaveSlime;
import com.flatts.productivefrogs.content.entity.DragonsbaneFrog;
import com.flatts.productivefrogs.content.entity.GeodeSlime;
import com.flatts.productivefrogs.content.entity.InfernalSlime;
import com.flatts.productivefrogs.content.entity.MimicSlime;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.content.entity.ResourceTadpole;
import com.flatts.productivefrogs.content.entity.TideSlime;
import com.flatts.productivefrogs.content.entity.VoidSlime;
import com.flatts.productivefrogs.content.entity.WitherbaneFrog;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.monster.Slime;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entity registry for Productive Frogs.
 *
 * <p>{@link ResourceTadpole} and {@link ResourceFrog} are sized to match
 * their vanilla counterparts so vanilla renderers can be reused without
 * tuning hitboxes per-mod. Mob category matches vanilla too:
 * {@code WATER_CREATURE} for tadpoles, {@code CREATURE} for frogs.
 */
public final class PFEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(Registries.ENTITY_TYPE, ProductiveFrogs.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<ResourceTadpole>> RESOURCE_TADPOLE =
        ENTITIES.register(
            "resource_tadpole",
            () -> EntityType.Builder.<ResourceTadpole>of(ResourceTadpole::new, MobCategory.WATER_CREATURE)
                .sized(Tadpole.HITBOX_WIDTH, Tadpole.HITBOX_HEIGHT)
                .eyeHeight(Tadpole.HITBOX_HEIGHT * 0.5F)
                // 1.21.1 EntityType.Builder.build takes the registry id String,
                // not a ResourceKey (that signature change came in 1.21.4+).
                .build(ProductiveFrogs.MOD_ID + ":resource_tadpole")
        );

    public static final DeferredHolder<EntityType<?>, EntityType<ResourceFrog>> RESOURCE_FROG =
        ENTITIES.register(
            "resource_frog",
            () -> EntityType.Builder.<ResourceFrog>of(ResourceFrog::new, MobCategory.CREATURE)
                .sized(0.5F, 0.5F)
                .eyeHeight(0.4F)
                .build(ProductiveFrogs.MOD_ID + ":resource_frog")
        );

    /**
     * The dragon altar's display frog (#249) - "Dragonsbane", a display-only frog the
     * altar spawns when its structure is valid and removes when broken. {@code MISC}
     * category so it never natural-spawns or counts toward mob caps. Frog-sized; reuses
     * the vanilla frog renderer + attributes.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<DragonsbaneFrog>> DRAGONSBANE =
        ENTITIES.register(
            "dragonsbane",
            () -> EntityType.Builder.<DragonsbaneFrog>of(DragonsbaneFrog::new, MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .eyeHeight(0.4F)
                .build(ProductiveFrogs.MOD_ID + ":dragonsbane")
        );

    /**
     * The Wither Altar's display frog (#247) - "Witherbane". Same lifecycle role as
     * {@link DragonsbaneFrog}: a {@code MISC}-category display entity the altar spawns when
     * valid and removes when broken. Frog-sized; reuses the vanilla frog attributes.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<WitherbaneFrog>> WITHERBANE =
        ENTITIES.register(
            "witherbane",
            () -> EntityType.Builder.<WitherbaneFrog>of(WitherbaneFrog::new, MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .eyeHeight(0.4F)
                .build(ProductiveFrogs.MOD_ID + ":witherbane")
        );

    /**
     * Single ResourceSlime entity type — category is carried as synced data,
     * not encoded in the type. Base dimensions match vanilla Slime's size-1
     * hitbox (0.52F × 0.52F); the actual in-world hitbox is computed at
     * runtime by {@code Slime#getDefaultDimensions(...).scale(getSize())},
     * so the vanilla SlimeRenderer geometry is reusable across sizes 1-127.
     * Per-instance size is set via {@link Slime#setSize} as normal.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<ResourceSlime>> RESOURCE_SLIME =
        ENTITIES.register(
            "resource_slime",
            () -> EntityType.Builder.<ResourceSlime>of(ResourceSlime::new, MobCategory.MONSTER)
                // Match vanilla EntityType.SLIME's 1.21.11 sizing: base is the
                // size-1 hitbox (0.52F), and Slime#getDefaultDimensions scales
                // by getSize() directly. Pre-1.21.x used a 2.04F base + an
                // internal 0.255*size scale — using that combo here gave us
                // hitboxes 4× too big at every size (caught in playtest).
                // spawnDimensionsScale(4.0F) matches vanilla and gates
                // natural-spawn position checks against the largest possible
                // runtime hitbox. The Cave/Geode/Tide/Void registrations below
                // reuse the same sizing; they all inherit vanilla Slime's
                // getDefaultDimensions and only diverge on category/renderer.
                .sized(0.52F, 0.52F)
                .eyeHeight(0.325F)
                .spawnDimensionsScale(4.0F)
                .clientTrackingRange(10)
                .build(ProductiveFrogs.MOD_ID + ":resource_slime")
        );

    /**
     * Cave Slime — the CAVE parent species. Vanilla-shaped bounding box +
     * client tracking range; same {@code Slime}-derived behavior. The default
     * discovery category CAVE is resolved in
     * {@link com.flatts.productivefrogs.event.SlimeSplitDiscoveryHandler}'s
     * {@code categoryForParent} by an EntityType-id lookup against the
     * {@code parent_species} datapack registry.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<CaveSlime>> CAVE_SLIME =
        ENTITIES.register(
            "cave_slime",
            () -> EntityType.Builder.<CaveSlime>of(CaveSlime::new, MobCategory.MONSTER)
                .sized(0.52F, 0.52F)
                .eyeHeight(0.325F)
                .spawnDimensionsScale(4.0F)
                .clientTrackingRange(10)
                .build(ProductiveFrogs.MOD_ID + ":cave_slime")
        );

    /**
     * Geode Slime — the GEODE parent species. Same shape as CaveSlime; the only
     * differences are the EntityType registration name, the renderer texture,
     * and the {@code geode_slime → GEODE} entry in the {@code parent_species}
     * datapack registry consulted by
     * {@link com.flatts.productivefrogs.event.SlimeSplitDiscoveryHandler}.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<GeodeSlime>> GEODE_SLIME =
        ENTITIES.register(
            "geode_slime",
            () -> EntityType.Builder.<GeodeSlime>of(GeodeSlime::new, MobCategory.MONSTER)
                .sized(0.52F, 0.52F)
                .eyeHeight(0.325F)
                .spawnDimensionsScale(4.0F)
                .clientTrackingRange(10)
                .build(ProductiveFrogs.MOD_ID + ":geode_slime")
        );

    /**
     * Tide Slime — the TIDE parent species. Same shape as the other parent
     * species; differences are the registration name, the renderer texture,
     * and the {@code tide_slime → TIDE} entry in the {@code parent_species}
     * datapack registry consulted by
     * {@link com.flatts.productivefrogs.event.SlimeSplitDiscoveryHandler}.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<TideSlime>> TIDE_SLIME =
        ENTITIES.register(
            "tide_slime",
            () -> EntityType.Builder.<TideSlime>of(TideSlime::new, MobCategory.MONSTER)
                .sized(0.52F, 0.52F)
                .eyeHeight(0.325F)
                .spawnDimensionsScale(4.0F)
                .clientTrackingRange(10)
                .build(ProductiveFrogs.MOD_ID + ":tide_slime")
        );

    /**
     * Void Slime — the VOID parent species. Same shape as the other parent
     * species; differences are the registration name, the renderer texture,
     * and the {@code void_slime → VOID} entry in the {@code parent_species}
     * datapack registry consulted by
     * {@link com.flatts.productivefrogs.event.SlimeSplitDiscoveryHandler}.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<VoidSlime>> VOID_SLIME =
        ENTITIES.register(
            "void_slime",
            () -> EntityType.Builder.<VoidSlime>of(VoidSlime::new, MobCategory.MONSTER)
                .sized(0.52F, 0.52F)
                .eyeHeight(0.325F)
                .spawnDimensionsScale(4.0F)
                .clientTrackingRange(10)
                .build(ProductiveFrogs.MOD_ID + ":void_slime")
        );

    /**
     * Bog Slime — the BOG parent species. Swamp-themed; replaces vanilla
     * {@code minecraft:slime} as the canonical BOG parent (V1.5).
     */
    public static final DeferredHolder<EntityType<?>, EntityType<BogSlime>> BOG_SLIME =
        ENTITIES.register(
            "bog_slime",
            () -> EntityType.Builder.<BogSlime>of(BogSlime::new, MobCategory.MONSTER)
                .sized(0.52F, 0.52F)
                .eyeHeight(0.325F)
                .spawnDimensionsScale(4.0F)
                .clientTrackingRange(10)
                .build(ProductiveFrogs.MOD_ID + ":bog_slime")
        );

    /**
     * Infernal Slime — the INFERNAL parent species. Nether-themed; replaces
     * vanilla {@code minecraft:magma_cube} as the canonical INFERNAL parent
     * (V1.5).
     */
    public static final DeferredHolder<EntityType<?>, EntityType<InfernalSlime>> INFERNAL_SLIME =
        ENTITIES.register(
            "infernal_slime",
            () -> EntityType.Builder.<InfernalSlime>of(InfernalSlime::new, MobCategory.MONSTER)
                .sized(0.52F, 0.52F)
                .eyeHeight(0.325F)
                .spawnDimensionsScale(4.0F)
                .clientTrackingRange(10)
                .build(ProductiveFrogs.MOD_ID + ":infernal_slime")
        );

    /**
     * The Mimic Slime (#253) - the Equivalence lane's synthesized slime. Carries
     * an arbitrary item id as synced data (no category/variant); always size 1,
     * never splits. A sibling of {@link ResourceSlime}, NOT a subclass, so the
     * species-frog sensor/drop ({@code instanceof ResourceSlime}) ignore it - only
     * Midas eats it. Vanilla slime sizing, like the other PF slimes.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<MimicSlime>> MIMIC_SLIME =
        ENTITIES.register(
            "mimic_slime",
            () -> EntityType.Builder.<MimicSlime>of(MimicSlime::new, MobCategory.MONSTER)
                .sized(0.52F, 0.52F)
                .eyeHeight(0.325F)
                .spawnDimensionsScale(4.0F)
                .clientTrackingRange(10)
                .build(ProductiveFrogs.MOD_ID + ":mimic_slime")
        );

    private PFEntities() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}
