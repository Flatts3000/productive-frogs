package com.flatts.productivefrogs.client.jei;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity;
import com.flatts.productivefrogs.content.recipe.CrucibleMeltRecipe;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFRecipeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * JEI category for the Froglight Crucible's melt recipes: a variant Froglight
 * -> its fluid (as a real JEI fluid ingredient, so {@code U} on a lava bucket
 * surfaces the Crucible as a source). Recipes come straight from the
 * {@code productivefrogs:crucible_melting} recipe type via the RecipeManager,
 * so datapack additions (including wave 2's molten metals) display with no
 * code change.
 *
 * <p>The arrow animates over the torch-heat melt duration of a 180 mB metal
 * Froglight (1,200 ticks at heat 1); hotter sources and smaller recipes melt
 * proportionally faster - the heat ladder itself lives in the companion
 * {@link CrucibleHeatCategory}.
 */
public final class CrucibleMeltCategory implements IRecipeCategory<RecipeHolder<CrucibleMeltRecipe>> {

    public static final RecipeType<RecipeHolder<CrucibleMeltRecipe>> TYPE =
        RecipeType.createFromVanilla(PFRecipeTypes.CRUCIBLE_MELTING.get());

    private static final int WIDTH = 96;
    private static final int HEIGHT = 26;
    private static final int INPUT_X = 1;
    private static final int OUTPUT_X = 75;
    private static final int SLOT_Y = 4;
    private static final int ARROW_X = 30;
    private static final int ARROW_Y = 4;

    /** Torch-heat melt duration of the reference 180 mB metal Froglight. */
    private static final int TORCH_MELT_TICKS =
        180 / CrucibleBlockEntity.MELT_PER_HEAT * CrucibleBlockEntity.MELT_PULSE_TICKS;

    private final IDrawable icon;
    private final IDrawableAnimated arrow;

    public CrucibleMeltCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(PFBlocks.CRUCIBLE.get().asItem()));
        this.arrow = guiHelper.createAnimatedRecipeArrow(TORCH_MELT_TICKS);
    }

    @Override
    public RecipeType<RecipeHolder<CrucibleMeltRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("productivefrogs.jei.category.crucible_melting");
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
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<CrucibleMeltRecipe> holder, IFocusGroup focuses) {
        CrucibleMeltRecipe recipe = holder.value();
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, SLOT_Y)
            .setStandardSlotBackground()
            .addIngredients(recipe.ingredient());
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, SLOT_Y)
            .setOutputSlotBackground()
            .addIngredient(NeoForgeTypes.FLUID_STACK, recipe.result().copy());
    }

    @Override
    public void draw(RecipeHolder<CrucibleMeltRecipe> holder, IRecipeSlotsView slotsView,
                     GuiGraphics gui, double mouseX, double mouseY) {
        arrow.draw(gui, ARROW_X, ARROW_Y);
    }

    @Override
    public Identifier getRegistryName(RecipeHolder<CrucibleMeltRecipe> holder) {
        return holder.id();
    }
}
