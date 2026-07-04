package com.flatts.productivefrogs.client.jei;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.item.SlimeMilkBucketItem;
import com.flatts.productivefrogs.registry.PFBlocks;
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
 * JEI recipe category for the Slime Churn (#187) - the Milker category's
 * inverse: a variant Slime Milk bucket plus an empty bucket converts to the
 * matching captured variant Slime Bucket, with the spent milk container
 * returned. One recipe per {@code SlimeVariant} (the plugin enumerates the
 * registry the same way the Milker category does), so datapack variants
 * surface automatically.
 *
 * <p>Layout mirrors the Churn GUI's 2x2: inputs stacked left (milk over
 * empty buckets), outputs stacked right (slime bucket over the returned
 * container), animated arrow between. The arrow is timed to the midpoint of
 * the source spawn interval - cosmetic only; the real cadence is randomized
 * per event and catalyst-modulated.
 */
public final class SlimeChurnRecipeCategory implements IRecipeCategory<SlimeChurnRecipeCategory.Recipe> {

    /** One churn conversion: a variant Slime Milk bucket -> its captured Slime Bucket. */
    public record Recipe(ItemStack milkBucket, ItemStack slimeBucket) {
    }

    public static final RecipeType<Recipe> TYPE =
        RecipeType.create(ProductiveFrogs.MOD_ID, "slime_churn", Recipe.class);

    private static final int WIDTH = 80;
    private static final int HEIGHT = 44;
    private static final int INPUT_X = 1;
    private static final int OUTPUT_X = 59;
    private static final int TOP_SLOT_Y = 2;
    private static final int BOTTOM_SLOT_Y = 24;
    private static final int ARROW_X = 24;
    private static final int ARROW_Y = 13;

    private final IDrawable icon;
    private final IDrawableAnimated arrow;

    public SlimeChurnRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(PFBlocks.SLIME_CHURN.get().asItem()));
        this.arrow = guiHelper.createAnimatedRecipeArrow(representativeIntervalTicks());
    }

    /**
     * Midpoint of the configured spawn-interval range, for the arrow's sweep
     * time. Guarded for the title-screen case where COMMON config isn't
     * loaded yet (falls back to the default midpoint).
     */
    private static int representativeIntervalTicks() {
        if (PFConfig.SPEC.isLoaded()) {
            return Math.max(1,
                (PFConfig.MIN_SPAWN_INTERVAL_TICKS.get() + PFConfig.MAX_SPAWN_INTERVAL_TICKS.get()) / 2);
        }
        return 400;
    }

    @Override
    public RecipeType<Recipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("productivefrogs.jei.category.slime_churn");
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
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, TOP_SLOT_Y)
            .setStandardSlotBackground()
            .addItemStack(recipe.milkBucket());
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, BOTTOM_SLOT_Y)
            .setStandardSlotBackground()
            .addItemStack(new ItemStack(Items.BUCKET));
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, TOP_SLOT_Y)
            .setOutputSlotBackground()
            .addItemStack(recipe.slimeBucket());
        // The spent milk container, returned once the bucket's spawn budget
        // drains (the Churn's second output slot).
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, BOTTOM_SLOT_Y)
            .setOutputSlotBackground()
            .addItemStack(new ItemStack(Items.BUCKET));
    }

    @Override
    public void draw(Recipe recipe, IRecipeSlotsView slotsView,
                     GuiGraphics gui, double mouseX, double mouseY) {
        arrow.draw(gui, ARROW_X, ARROW_Y);
    }

    @Override
    public ResourceLocation getRegistryName(Recipe recipe) {
        // Stable per-recipe id - keyed on the input milk bucket's variant
        // (the item identity carries it, v1.8 per-variant items).
        if (recipe.milkBucket().getItem() instanceof SlimeMilkBucketItem milk) {
            return milk.variantId();
        }
        return ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "slime_churn");
    }
}
