package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.CaveSlime;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFRegistries;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSplitEvent;
import org.jspecify.annotations.Nullable;

/**
 * Random-discovery path from {@code docs/slime_sourcing.md} (Path 1) — when a
 * vanilla {@link Slime} or {@link MagmaCube} splits on death, each child has a
 * chance to become a category-locked {@link ResourceSlime} drawn from the
 * parent species's default category pool.
 *
 * <p>Default pools (V1 simplification, no per-resource variants yet):
 * <ul>
 *   <li>{@code minecraft:slime} → {@link Category#METALLIC}</li>
 *   <li>{@code minecraft:magma_cube} → {@link Category#INFERNAL}</li>
 * </ul>
 *
 * <p>Categories not covered by vanilla parent species (MINERAL / GEM / AQUATIC
 * / ARCANE) wait for the parent-species PRs (Cave / Geode / Tide / Void Slime)
 * to land — they'll each register their own default pool here.
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
     * Per-offspring discovery chance. Exposed for the GameTest harness to
     * force deterministic conversion. V2 may make this a per-parent-entity
     * config value (see {@code docs/slime_sourcing.md} §V1 Configurability).
     */
    public static float discoveryChancePerOffspring = 0.05f;

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

        Category category = categoryForParent(parent);
        if (category == null) {
            return;
        }

        Level level = parent.level();
        Registry<SlimeVariant> variantRegistry = level.registryAccess()
            .lookup(PFRegistries.SLIME_VARIANT).orElse(null);

        List<Mob> children = event.getChildren();
        for (int i = 0; i < children.size(); i++) {
            Mob child = children.get(i);
            if (!(child instanceof Slime childSlime)) {
                continue;
            }
            if (parent.getRandom().nextFloat() >= discoveryChancePerOffspring) {
                continue;
            }
            ResourceSlime resource = PFEntities.RESOURCE_SLIME.get().create(level, EntitySpawnReason.TRIGGERED);
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
                Map.Entry<Identifier, SlimeVariant> picked =
                    SlimeVariant.pickWeighted(variantRegistry, category, parent.getRandom());
                if (picked != null) {
                    resource.setVariant(picked.getKey());
                }
            }
            children.set(i, resource);
        }
    }

    /**
     * Default category pool for a parent slime species. Returns null if the
     * parent isn't a known parent species — modded slime species can register
     * their own categories via a future datapack registry (see {@code
     * docs/architecture.md} §Slime Sourcing Hooks).
     *
     * <p>{@link CaveSlime} and its siblings (Geode/Tide/Void in later PRs)
     * are checked BEFORE the {@code parent.getClass() == Slime.class}
     * vanilla-green check — they extend {@code Slime}, so that strict equality
     * would otherwise miss them (instanceof Slime is true; getClass() is
     * CaveSlime).
     */
    @Nullable
    private static Category categoryForParent(Mob parent) {
        if (parent instanceof MagmaCube) {
            return Category.INFERNAL;
        }
        if (parent instanceof CaveSlime) {
            return Category.MINERAL;
        }
        if (parent.getClass() == Slime.class) {
            return Category.METALLIC;
        }
        return null;
    }
}
