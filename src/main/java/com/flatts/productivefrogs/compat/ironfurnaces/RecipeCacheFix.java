package com.flatts.productivefrogs.compat.ironfurnaces;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * Makes Iron Furnaces' "can this item be smelted?" answer component-aware.
 *
 * <p><b>The bug this exists for.</b> Iron Furnaces decides whether a stack may
 * enter a furnace with:
 *
 * <pre>{@code
 * public boolean hasRecipe(ItemStack stack) {
 *     Item item = stack.getItem();
 *     return ModSetup.HAS_RECIPE.computeIfAbsent(item, value ->
 *         this.recipeCheckSmelting.getRecipeFor(new SingleRecipeInput(stack), this.level).isPresent());
 * }
 * }</pre>
 *
 * <p>{@code ModSetup.HAS_RECIPE} is a {@code Map<Item, Boolean>}: a static
 * cache, keyed by item alone, that lives for the whole process. So the
 * <em>first</em> stack of a given item ever tested decides the answer for every
 * later stack of that item, for the rest of the session.
 *
 * <p>Every Froglight variant is the same item, and their smelting recipes match
 * on the {@code slime_variant} component, so whether a recipe exists depends on
 * the component and not on the item. A single Froglight carrying no variant -
 * one taken from the creative tab, or produced by a mod that drops the
 * component when it copies a block - has no smelting recipe. Test one in a
 * furnace once and {@code false} is cached against the Froglight item, and from
 * that moment <b>no Froglight of any variant can be put into any Iron Furnace
 * at all</b>, by hand or by pipe, until the game is restarted.
 *
 * <p>That is the "the furnace just won't take my Froglights" report, the
 * restart that fixes it, and the reason it looks random: it depends entirely on
 * which Froglight the furnace happened to see first.
 *
 * <p><b>The fix.</b> Stacks that carry data components skip the shared cache
 * and ask the recipe manager directly. Stacks that do not - which is nearly
 * everything, and every case the cache was designed for - take Iron Furnaces'
 * original cached path untouched, so the fast path stays fast and plain items
 * behave exactly as before.
 *
 * <p>This is the same defect as the auto-split bug in
 * {@link FactoryAutoSplit}: item identity treated as item id. Different method,
 * different symptom, one cause. See {@code docs/ironfurnaces_component_fixes.md}.
 */
public final class RecipeCacheFix {

    private RecipeCacheFix() {
    }

    /**
     * Whether this stack must bypass Iron Furnaces' item-keyed recipe cache.
     *
     * <p>True exactly when the stack carries data components, because those are
     * the stacks whose smeltability the cache cannot represent. Everything else
     * is left on the original path: the cache is a real optimisation on a hot
     * insertion check, and it is only wrong for items whose identity is finer
     * than their id.
     */
    public static boolean needsComponentAwareCheck(ItemStack stack) {
        return !stack.isEmpty() && !stack.getComponentsPatch().isEmpty();
    }

    /**
     * Uncached smeltability for one stack, components included.
     *
     * <p>No caching of our own on purpose. Iron Furnaces already calls
     * {@code getRecipeFor} uncached every tick from {@code getRecipeNonCached},
     * so an uncached lookup on the insertion path costs no more than the mod
     * already spends, and a component-keyed cache would need eviction to avoid
     * growing without bound on a pack with many variants.
     *
     * @param level      the furnace's level; {@code null} means "cannot know", answered false
     * @param recipeType smelting, smoking or blasting, per the furnace's current mode
     */
    public static boolean hasRecipe(Level level, RecipeType<? extends AbstractCookingRecipe> recipeType,
                                    ItemStack stack) {
        if (level == null || recipeType == null || stack.isEmpty()) {
            return false;
        }
        @SuppressWarnings("unchecked")
        RecipeType<AbstractCookingRecipe> type = (RecipeType<AbstractCookingRecipe>) recipeType;
        return level.getRecipeManager()
            .getRecipeFor(type, new SingleRecipeInput(stack), level)
            .isPresent();
    }
}
