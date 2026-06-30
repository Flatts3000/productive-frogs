package com.flatts.productivefrogs.client.jei;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFDataComponents;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * JEI recipe category for the Slime Milker: a variant Slime Bucket converts to
 * the matching variant-stamped Slime Milk bucket. No fuel - the slime is the
 * input. One recipe per {@code SlimeVariant} (the plugin enumerates the registry
 * the same way it does for the info pages), so new datapack variants surface
 * automatically.
 *
 * <p>Layout mirrors the in-world transform from
 * {@link SlimeMilkerBlockEntity#serverTick}: input bucket -> animated arrow
 * timed to the {@link SlimeMilkerBlockEntity#COOK_TIME_TOTAL}-tick cook ->
 * output milk bucket.
 */
public final class SlimeMilkerRecipeCategory implements IRecipeCategory<SlimeMilkerRecipeCategory.Recipe> {

    /** One milker conversion: a variant Slime Bucket -> its Slime Milk bucket. */
    public record Recipe(ItemStack input, ItemStack output) {
    }

    public static final RecipeType<Recipe> TYPE =
        RecipeType.create(ProductiveFrogs.MOD_ID, "slime_milker", Recipe.class);

    private static final int WIDTH = 80;
    private static final int HEIGHT = 26;
    private static final int INPUT_X = 1;
    private static final int OUTPUT_X = 59;
    private static final int SLOT_Y = 4;
    private static final int ARROW_X = 24;
    private static final int ARROW_Y = 4;

    private final IDrawable icon;
    private final IDrawableAnimated arrow;

    public SlimeMilkerRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(PFBlocks.SLIME_MILKER.get().asItem()));
        this.arrow = guiHelper.createAnimatedRecipeArrow(SlimeMilkerBlockEntity.COOK_TIME_TOTAL);
    }

    @Override
    public RecipeType<Recipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("productivefrogs.jei.category.slime_milker");
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
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, SLOT_Y)
            .setStandardSlotBackground()
            .addItemStack(recipe.input());
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, SLOT_Y)
            .setOutputSlotBackground()
            .addItemStack(recipe.output());
    }

    @Override
    public void draw(Recipe recipe, IRecipeSlotsView slotsView,
                     GuiGraphicsExtractor gui, double mouseX, double mouseY) {
        arrow.draw(gui, ARROW_X, ARROW_Y);
    }

    @Override
    public Identifier getRegistryName(Recipe recipe) {
        // Stable per-recipe id so JEI bookmarks / recipe lookups resolve - keyed
        // on the output milk bucket's variant component (unique per variant).
        Identifier variant = recipe.output().get(PFDataComponents.SLIME_VARIANT.get());
        return variant != null ? variant
            : Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "slime_milker");
    }
}
