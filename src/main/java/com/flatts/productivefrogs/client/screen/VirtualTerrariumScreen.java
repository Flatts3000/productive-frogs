package com.flatts.productivefrogs.client.screen;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.menu.VirtualTerrariumMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Client screen for the Virtual Terrarium Processor. Slice 1 reuses the Slime Milker
 * background as a placeholder and draws a duration bar from the synced cycle progress;
 * a bespoke void-tier GUI texture lands in the resources slice.
 */
public class VirtualTerrariumScreen extends PFContainerScreen<VirtualTerrariumMenu> {

    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
        ProductiveFrogs.MOD_ID, "textures/gui/container/slime_milker.png");

    private static final int BG_TEX_WIDTH = 256;
    private static final int BG_TEX_HEIGHT = 256;

    // Duration bar drawn under the output row (placeholder positions for slice 1).
    private static final int BAR_X = 62;
    private static final int BAR_Y = 54;
    private static final int BAR_WIDTH = 90;
    private static final int BAR_HEIGHT = 4;

    public VirtualTerrariumScreen(VirtualTerrariumMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(gui, mouseX, mouseY, partialTick);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        gui.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight,
                 BG_TEX_WIDTH, BG_TEX_HEIGHT);

        int progress = this.menu.getProgress();
        int interval = this.menu.getInterval();
        int filled = interval > 0 ? Math.min(BAR_WIDTH, (progress * BAR_WIDTH) / interval) : 0;
        gui.fill(x + BAR_X, y + BAR_Y, x + BAR_X + BAR_WIDTH, y + BAR_Y + BAR_HEIGHT, 0xFF20102E);
        if (filled > 0) {
            gui.fill(x + BAR_X, y + BAR_Y, x + BAR_X + filled, y + BAR_Y + BAR_HEIGHT, 0xFF9A4DEA);
        }
    }
}
