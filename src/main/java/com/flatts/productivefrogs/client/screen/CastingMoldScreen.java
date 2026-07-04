package com.flatts.productivefrogs.client.screen;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.entity.CastingMoldBlockEntity;
import com.flatts.productivefrogs.content.menu.CastingMoldMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
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

    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
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
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        super.extractTooltip(gui, mouseX, mouseY);
        renderGaugeTooltip(gui, mouseX, mouseY);
    }

    /**
     * Hover tooltip for the fluid gauge: the buffered fluid's name plus
     * {@code amount / capacity mB} (or "Empty"). Slots have their own tooltip
     * pass in {@link PFContainerScreen}; the gauge isn't a slot, so it needs
     * this explicit hit-test.
     */
    private void renderGaugeTooltip(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        int gx = (this.width - this.imageWidth) / 2 + GAUGE_X;
        int gy = (this.height - this.imageHeight) / 2 + GAUGE_Y;
        if (mouseX < gx || mouseX >= gx + GAUGE_WIDTH || mouseY < gy || mouseY >= gy + GAUGE_HEIGHT) {
            return;
        }
        CastingMoldBlockEntity be = this.menu.blockEntity();
        FluidStack buffered = be == null ? FluidStack.EMPTY : be.fluid();
        java.util.List<Component> lines = new java.util.ArrayList<>();
        if (buffered.isEmpty()) {
            lines.add(Component.translatable("productivefrogs.gui.fluid_empty"));
        } else {
            lines.add(buffered.getHoverName());
            // Amount from the synced ContainerData (live while the menu is
            // open), capacity from the constant.
            lines.add(Component.translatable("productivefrogs.gui.fluid_amount",
                this.menu.getFluidAmount(), CastingMoldBlockEntity.TANK_CAPACITY)
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        gui.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(gui, mouseX, mouseY, partialTick);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        gui.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight,
                 BG_TEX_WIDTH, BG_TEX_HEIGHT);

        int progress = this.menu.getProgress();
        int total = this.menu.getProgressTotal();
        if (progress > 0 && total > 0) {
            int filled = Math.min(ARROW_WIDTH, (progress * ARROW_WIDTH) / total);
            gui.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND,
                     x + ARROW_BG_X, y + ARROW_BG_Y,
                     (float) ARROW_SRC_X, (float) ARROW_SRC_Y,
                     filled, ARROW_HEIGHT,
                     BG_TEX_WIDTH, BG_TEX_HEIGHT);
        }

        renderFluidGauge(gui, x + GAUGE_X, y + GAUGE_Y);
    }

    /** Bottom-up fluid column using the buffered fluid's sprite + tint. */
    private void renderFluidGauge(GuiGraphicsExtractor gui, int gx, int gy) {
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
        // 26.1: IClientFluidTypeExtensions.getStillTexture/getTintColor are gone;
        // the fluid's baked still sprite now lives on the FluidStateModelSet.
        TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager()
            .getFluidStateModelSet().get(fluid.defaultFluidState())
            .stillMaterial().sprite();
        // TODO(26.1 port): the per-fluid tint moved to FluidModel.tintSource() (a
        // BlockTintSource that resolves against world/biome context, unavailable in
        // a GUI). Drawing untinted for now; verify the molten colour at runClient
        // and, if the greyscale sprite needs the variant tint, resolve a flat
        // colour for the gauge here.
        int argb = 0xFFFFFFFF;

        int filledHeight = Math.min(GAUGE_HEIGHT,
            amount * GAUGE_HEIGHT / CastingMoldBlockEntity.TANK_CAPACITY);
        // Tile the 16x16 sprite up the column, bottom-up.
        int remaining = filledHeight;
        int drawY = gy + GAUGE_HEIGHT;
        while (remaining > 0) {
            int sliceH = Math.min(16, remaining);
            drawY -= sliceH;
            gui.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, gx, drawY, GAUGE_WIDTH, sliceH, argb);
            remaining -= sliceH;
        }
    }
}
