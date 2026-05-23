package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.ParentSpeciesEntry;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFRegistries;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSplitEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Random-discovery path from {@code docs/slime_sourcing.md} (Path 1) — when a
 * vanilla {@link Slime} or {@link MagmaCube} splits on death, each child has a
 * chance to become a category-locked {@link ResourceSlime} drawn from the
 * parent species's default category pool.
 *
 * <p>Per-parent-species category mapping is driven by the
 * {@link PFRegistries#PARENT_SPECIES} datapack registry — six default JSONs
 * at {@code data/productivefrogs/productivefrogs/parent_species/} ship with
 * the mod (vanilla {@code Slime}/{@code MagmaCube} → METALLIC/INFERNAL plus
 * the four PF parent species), and modpacks can override individual entries
 * or wire new modded slimes into the discovery loop without recompiling.
 *
 * <p>{@link MobSplitEvent} is the right hook for this: NeoForge fires it
 * synchronously from {@code Slime#remove(RemovalReason)} after the children
 * are computed but before they're added to the world, and the children list
 * is mutable — we can swap a vanilla child for a fresh ResourceSlime in place
 * without booting + discarding a vanilla entity first.
 *
 * <p>{@link ResourceSlime} splits don't fire this event because our
 * {@link ResourceSlime#remove} override does its own category-propagating
 * split and zeros size before delegating to super, so vanilla's split path
 * (which fires the event) is suppressed.
 */
@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID)
public final class SlimeSplitDiscoveryHandler {

    /**
     * Test-only override for the discovery chance. When non-null, takes
     * precedence over {@link PFConfig#DISCOVERY_CHANCE_PER_OFFSPRING} —
     * GameTests set this to {@code 1.0f} in a try/finally so split outcomes
     * are deterministic. Always null in production. Volatile so the test
     * thread's write is visible to the server thread that runs the event
     * handler.
     */
    @Nullable
    public static volatile Float testOverride = null;

    /**
     * Effective discovery chance — test override wins when present,
     * otherwise reads the mod config. Production code calls this; only
     * GameTest setup writes {@link #testOverride}.
     */
    public static float discoveryChancePerOffspring() {
        Float override = testOverride;
        return override != null ? override : PFConfig.DISCOVERY_CHANCE_PER_OFFSPRING.get().floatValue();
    }

    private SlimeSplitDiscoveryHandler() {
        // event handler, not instantiable
    }

    @SubscribeEvent
    public static void onMobSplit(MobSplitEvent event) {
        Mob parent = event.getParent();
        if (!(parent instanceof Slime)) {
            return;
        }
        if (parent instanceof ResourceSlime) {
            // Resource Slimes already propagate their own category through
            // splits via ResourceSlime#splitWithCategory. Defensive — this
            // branch shouldn't fire because our override suppresses vanilla's
            // split path before MobSplitEvent reaches the event hook.
            return;
        }

        Level level = parent.level();
        Category category = categoryForParent(parent, level);
        if (category == null) {
            return;
        }

        Registry<SlimeVariant> variantRegistry = level.registryAccess()
            .registry(PFRegistries.SLIME_VARIANT).orElse(null);

        List<Mob> children = event.getChildren();
        for (int i = 0; i < children.size(); i++) {
            Mob child = children.get(i);
            if (!(child instanceof Slime childSlime)) {
                continue;
            }
            if (parent.getRandom().nextFloat() >= discoveryChancePerOffspring()) {
                continue;
            }
            ResourceSlime resource = PFEntities.RESOURCE_SLIME.get().create(level);
            if (resource == null) {
                continue;
            }
            resource.snapTo(child.getX(), child.getY(), child.getZ(), child.getYRot(), child.getXRot());
            resource.setSize(childSlime.getSize(), true);
            // Category first (fallback when no variants in pool), then a
            // weighted variant pick — setVariant re-syncs category from the
            // registry so the two stay consistent when a variant is picked.
            resource.setCategory(category);
            if (variantRegistry != null) {
                Map.Entry<ResourceLocation, SlimeVariant> picked =
                    SlimeVariant.pickWeighted(variantRegistry, category, parent.getRandom());
                if (picked != null) {
                    resource.setVariant(picked.getKey());
                }
            }
            children.set(i, resource);
        }
    }

    /**
     * Default category pool for a parent slime species, resolved through the
     * {@link PFRegistries#PARENT_SPECIES} datapack registry: look up the
     * parent's EntityType id and return the matching entry's category, or
     * {@code null} if the registry has no mapping for that type (a modded
     * slime nobody's wired into the discovery loop).
     *
     * <p>The mapping was previously a hardcoded {@code instanceof} chain
     * in Java. Six default JSONs ship at
     * {@code data/productivefrogs/productivefrogs/parent_species/} so the
     * mod's own out-of-the-box behavior is unchanged; datapacks can override
     * any entry or add new ones to wire modded slimes (e.g. Mythic Metals'
     * Pyrite Slime → METALLIC) without recompiling.
     *
     * <p>The {@code EntityType} lookup is the cleaner formulation: each of
     * our PF parent species is its own {@link net.minecraft.world.entity.EntityType},
     * so we no longer need the "check subclasses before vanilla {@code Slime}
     * via getClass() strict equality" footgun that the legacy {@code instanceof}
     * chain had — entity-type ids are inherently unique per species.
     *
     * <p>Linear scan over the registry is fine at V1 scale (6 entries); if
     * the registry ever grows past a couple dozen, materialize a cache
     * elsewhere rather than on this hot path.
     */
    @Nullable
    private static Category categoryForParent(Mob parent, Level level) {
        ResourceLocation parentTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(parent.getType());
        if (parentTypeId == null) {
            return null;
        }
        Registry<ParentSpeciesEntry> registry = level.registryAccess()
            .registry(PFRegistries.PARENT_SPECIES).orElse(null);
        if (registry == null) {
            return null;
        }
        for (ParentSpeciesEntry entry : registry) {
            if (entry.entityType().equals(parentTypeId)) {
                return entry.category();
            }
        }
        return null;
    }
}
