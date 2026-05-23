package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.CaveSlime;
import com.flatts.productivefrogs.content.entity.GeodeSlime;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.content.entity.ResourceTadpole;
import com.flatts.productivefrogs.content.entity.TideSlime;
import com.flatts.productivefrogs.content.entity.VoidSlime;
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
                .build("resource_tadpole")
        );

    public static final DeferredHolder<EntityType<?>, EntityType<ResourceFrog>> RESOURCE_FROG =
        ENTITIES.register(
            "resource_frog",
            () -> EntityType.Builder.<ResourceFrog>of(ResourceFrog::new, MobCategory.CREATURE)
                .sized(0.5F, 0.5F)
                .eyeHeight(0.4F)
                .build("resource_frog")
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
                .build("resource_slime")
        );

    /**
     * Cave Slime — the MINERAL parent species. Vanilla-shaped bounding box +
     * client tracking range; same {@code Slime}-derived behavior. The default
     * discovery category MINERAL is set in
     * {@link com.flatts.productivefrogs.event.SlimeSplitDiscoveryHandler}'s
     * {@code categoryForParent} via an {@code instanceof CaveSlime} check.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<CaveSlime>> CAVE_SLIME =
        ENTITIES.register(
            "cave_slime",
            () -> EntityType.Builder.<CaveSlime>of(CaveSlime::new, MobCategory.MONSTER)
                .sized(0.52F, 0.52F)
                .eyeHeight(0.325F)
                .spawnDimensionsScale(4.0F)
                .clientTrackingRange(10)
                .build("cave_slime")
        );

    /**
     * Geode Slime — the GEM parent species. Same shape as CaveSlime; the only
     * differences are the EntityType registration name, the renderer texture,
     * and the {@code instanceof GeodeSlime → GEM} branch in
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
                .build("geode_slime")
        );

    /**
     * Tide Slime — the AQUATIC parent species. Same shape as the other parent
     * species; differences are the registration name, the renderer texture,
     * and the {@code instanceof TideSlime → AQUATIC} branch in
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
                .build("tide_slime")
        );

    /**
     * Void Slime — the ARCANE parent species. Closes the parent-species set
     * (Cave/Geode/Tide/Void cover the four non-vanilla categories MINERAL /
     * GEM / AQUATIC / ARCANE). The {@code instanceof VoidSlime → ARCANE}
     * branch lives in {@link com.flatts.productivefrogs.event.SlimeSplitDiscoveryHandler}.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<VoidSlime>> VOID_SLIME =
        ENTITIES.register(
            "void_slime",
            () -> EntityType.Builder.<VoidSlime>of(VoidSlime::new, MobCategory.MONSTER)
                .sized(0.52F, 0.52F)
                .eyeHeight(0.325F)
                .spawnDimensionsScale(4.0F)
                .clientTrackingRange(10)
                .build("void_slime")
        );

    private PFEntities() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}
