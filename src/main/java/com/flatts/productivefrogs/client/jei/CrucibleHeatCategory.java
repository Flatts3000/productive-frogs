package com.flatts.productivefrogs.client.jei;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.registry.PFBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * JEI category listing the Froglight Crucible's heat sources and their heat
 * values - the discoverability surface for the {@code crucible_heat} data map
 * (Ex Deorum-style "what do I put under it?"). Entries are enumerated from
 * the live (synced) data map at {@code registerRecipes} time, so pack
 * overrides and additions display automatically; the placed Lava Froglight
 * (the one code-side heat rule) is appended by the plugin.
 *
 * <p>Layout: the source's item at left, "Heat: N" text beside it. Blocks with
 * no item form (lava, fire) display via a representative item (lava bucket,
 * flint and steel); the heat number is what matters.
 */
public final class CrucibleHeatCategory implements IRecipeCategory<CrucibleHeatCategory.Entry> {

    /**
     * One heat source: the display stack, its heat value, and the id that
     * keys the JEI registry name (the source block's id, or the froglight
     * variant id for the Lava Froglight entry).
     */
    public record Entry(ItemStack display, int heat, ResourceLocation id) {
    }

    public static final RecipeType<Entry> TYPE =
        RecipeType.create(ProductiveFrogs.MOD_ID, "crucible_heat", Entry.class);

    private static final int WIDTH = 110;
    private static final int HEIGHT = 26;
    private static final int SLOT_X = 1;
    private static final int SLOT_Y = 4;
    private static final int TEXT_X = 26;
    private static final int TEXT_Y = 9;

    private final IDrawable icon;

    public CrucibleHeatCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(PFBlocks.CRUCIBLE.get().asItem()));
    }

    @Override
    public RecipeType<Entry> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("productivefrogs.jei.category.crucible_heat");
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
    public void setRecipe(IRecipeLayoutBuilder builder, Entry entry, IFocusGroup focuses) {
        // INPUT (not CATALYST) so item lookups surface the category too -
        // pressing R/U on a torch shows it heats the Crucible.
        builder.addSlot(RecipeIngredientRole.INPUT, SLOT_X, SLOT_Y)
            .setStandardSlotBackground()
            .addItemStack(entry.display());
    }

    @Override
    public void draw(Entry entry, IRecipeSlotsView slotsView,
                     GuiGraphics gui, double mouseX, double mouseY) {
        gui.drawString(Minecraft.getInstance().font,
            Component.translatable("productivefrogs.jei.crucible_heat_value", entry.heat()),
            TEXT_X, TEXT_Y, 0xFF404040, false);
    }

    @Override
    public ResourceLocation getRegistryName(Entry entry) {
        return entry.id();
    }
}
