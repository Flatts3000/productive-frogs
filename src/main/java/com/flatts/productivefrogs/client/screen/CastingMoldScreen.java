package com.flatts.productivefrogs.client.screen;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.entity.CastingMoldBlockEntity;
import com.flatts.productivefrogs.content.menu.CastingMoldMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Client screen for the Casting Mold: a fluid gauge (the buffered molten,
 * drawn with the fluid's own still sprite + tint), the cast-progress arrow,
 * and the output slot. Furnace-shaped background (the Milker GUI sheet is the
 * placeholder until the Mold's own art pass - the unused input-slot frame
 * under the gauge is known and accepted).
 *
 * <p>Fluid amount + progress ride the menu's synced {@code ContainerData};
 * the fluid TYPE comes off the client-synced BlockEntity through
 * {@link CastingMoldMenu#blockEntity()}.
 */
public class CastingMoldScreen extends PFContainerScreen<CastingMoldMenu> {

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
        ProductiveFrogs.MOD_ID, "textures/gui/container/casting_mold.png");

    // Same composited sheet conventions as the Milker GUI (see
    // SlimeMilkerScreen) - arrow sprite re-inlined at (176, 14).
    private static final int ARROW_BG_X = 79;
    private static final int ARROW_BG_Y = 34;
    private static final int ARROW_SRC_X = 176;
    private static final int ARROW_SRC_Y = 14;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 16;

    // Fluid gauge: a 16-wide column on the left side of the panel (the sheet
    // draws a recessed frame around it at (25, 16)), filling bottom-up with
    // the buffered fluid.
    private static final int GAUGE_X = 26;
    private static final int GAUGE_Y = 17;
    private static final int GAUGE_WIDTH = 16;
    private static final int GAUGE_HEIGHT = 52;

    private static final int BG_TEX_WIDTH = 256;
    private static final int BG_TEX_HEIGHT = 256;

    public CastingMoldScreen(CastingMoldMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        gui.blit(BACKGROUND, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight,
                 BG_TEX_WIDTH, BG_TEX_HEIGHT);

        int progress = this.menu.getProgress();
        int total = this.menu.getProgressTotal();
        if (progress > 0 && total > 0) {
            int filled = Math.min(ARROW_WIDTH, (progress * ARROW_WIDTH) / total);
            gui.blit(BACKGROUND,
                     x + ARROW_BG_X, y + ARROW_BG_Y,
                     ARROW_SRC_X, ARROW_SRC_Y,
                     filled, ARROW_HEIGHT,
                     BG_TEX_WIDTH, BG_TEX_HEIGHT);
        }

        renderFluidGauge(gui, x + GAUGE_X, y + GAUGE_Y);
    }

    /** Bottom-up fluid column using the buffered fluid's sprite + tint. */
    private void renderFluidGauge(GuiGraphics gui, int gx, int gy) {
        CastingMoldBlockEntity be = this.menu.blockEntity();
        int amount = this.menu.getFluidAmount();
        if (be == null || amount <= 0) {
            return;
        }
        FluidStack buffered = be.fluid();
        if (buffered.isEmpty()) {
            return;
        }
        Fluid fluid = buffered.getFluid();
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
        TextureAtlasSprite sprite = Minecraft.getInstance()
            .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(ext.getStillTexture(buffered));
        int tint = ext.getTintColor();
        float r = ((tint >> 16) & 0xFF) / 255.0F;
        float g = ((tint >> 8) & 0xFF) / 255.0F;
        float b = (tint & 0xFF) / 255.0F;

        int filledHeight = Math.min(GAUGE_HEIGHT,
            amount * GAUGE_HEIGHT / CastingMoldBlockEntity.TANK_CAPACITY);
        // Tile the 16x16 sprite up the column, bottom-up.
        int remaining = filledHeight;
        int drawY = gy + GAUGE_HEIGHT;
        while (remaining > 0) {
            int sliceH = Math.min(16, remaining);
            drawY -= sliceH;
            gui.blit(gx, drawY, 0, GAUGE_WIDTH, sliceH, sprite, r, g, b, 1.0F);
            remaining -= sliceH;
        }
    }
}
