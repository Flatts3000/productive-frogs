package com.flatts.productivefrogs.client.jei;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFDataComponents;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * JEI recipe category for the Spawnery: a glass bottle plus a primer (and one
 * slime ball of fuel) becomes a bottled frogspawn ({@code FrogEggItem}).
 *
 * <p>One recipe per species: the primer slot cycles through every item in that
 * species' {@code spawnery_primer/<species>} tag, so a modpack that retunes the
 * tag (Sky Frogs maps cave -> cobblestone, geode -> redstone) shows the override
 * automatically. A separate recipe covers the slime-ball -> plain vanilla
 * frogspawn case. The plugin builds the list and gates it on
 * {@code spawnery.enabled} (the block is hidden from JEI when off).
 *
 * <p>Layout mirrors the in-world transform from {@code SpawneryBlockEntity}: a
 * furnace-shaped fuel column (animated flame above the slime-ball slot) and a
 * cook arrow timed to {@code spawnery.productionTicks}; the primer is consumed.
 */
public final class SpawneryRecipeCategory implements IRecipeCategory<SpawneryRecipeCategory.Recipe> {

    /**
     * One Spawnery conversion. {@code primers} is every item that primes this
     * species (the slot cycles them); {@code output} is the resulting frogspawn
     * bottle (stamped with a {@link Category}, or plain for the vanilla case).
     */
    public record Recipe(List<ItemStack> primers, ItemStack output) {
    }

    public static final RecipeType<Recipe> TYPE =
        RecipeType.create(ProductiveFrogs.MOD_ID, "spawnery", Recipe.class);

    /** Fallback animation duration when the config spec isn't loaded yet. */
    private static final int DEFAULT_PRODUCTION_TICKS = 200;

    /** Constant ingredients in every recipe (only the primer + output vary). */
    private static final ItemStack GLASS_BOTTLE = new ItemStack(Items.GLASS_BOTTLE);
    private static final ItemStack SLIME_BALL = new ItemStack(Items.SLIME_BALL);

    private static final int WIDTH = 100;
    private static final int HEIGHT = 54;
    private static final int BOTTLE_X = 0;
    private static final int BOTTLE_Y = 0;
    private static final int PRIMER_X = 22;
    private static final int PRIMER_Y = 0;
    private static final int FLAME_X = 1;
    private static final int FLAME_Y = 20;
    private static final int FUEL_X = 0;
    private static final int FUEL_Y = 36;
    private static final int ARROW_X = 48;
    private static final int ARROW_Y = 18;
    private static final int OUTPUT_X = 78;
    private static final int OUTPUT_Y = 18;

    private final IDrawable icon;
    private final IDrawableAnimated flame;
    private final IDrawableAnimated arrow;

    public SpawneryRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(PFBlocks.SPAWNERY.get().asItem()));
        int ticks = PFConfig.SPEC.isLoaded()
            ? PFConfig.SPAWNERY_PRODUCTION_TICKS.get()
            : DEFAULT_PRODUCTION_TICKS;
        this.flame = guiHelper.createAnimatedRecipeFlame(ticks);
        this.arrow = guiHelper.createAnimatedRecipeArrow(ticks);
    }

    @Override
    public RecipeType<Recipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("productivefrogs.jei.category.spawnery");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, BOTTLE_X, BOTTLE_Y)
            .setStandardSlotBackground()
            .addItemStack(GLASS_BOTTLE);
        builder.addSlot(RecipeIngredientRole.INPUT, PRIMER_X, PRIMER_Y)
            .setStandardSlotBackground()
            .addItemStacks(recipe.primers());
        builder.addSlot(RecipeIngredientRole.INPUT, FUEL_X, FUEL_Y)
            .setStandardSlotBackground()
            .addItemStack(SLIME_BALL);
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
            .setOutputSlotBackground()
            .addItemStack(recipe.output());
    }

    @Override
    public void draw(Recipe recipe, IRecipeSlotsView slotsView,
                     GuiGraphics gui, double mouseX, double mouseY) {
        flame.draw(gui, FLAME_X, FLAME_Y);
        arrow.draw(gui, ARROW_X, ARROW_Y);
    }

    @Override
    public ResourceLocation getRegistryName(Recipe recipe) {
        // Stable per-recipe id: the output's contained category (one per species),
        // or "vanilla" for the plain-frogspawn case.
        Category cat = recipe.output().get(PFDataComponents.CONTAINED_CATEGORY.get());
        return ResourceLocation.fromNamespaceAndPath(
            ProductiveFrogs.MOD_ID, "spawnery/" + (cat == null ? "vanilla" : cat.id()));
    }
}
