package com.flatts.productivefrogs.client.screen;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.menu.SlurryPressMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Client screen for the Slurry Press (#281, Phase 3). The Churn's
 * furnace-family layout: filled Ender Net over empty buckets on the left,
 * Slurry bucket over the returned net on the right, press-progress arrow
 * between (a fixed 100-tick cycle, so the fill is linear like the Milker's).
 *
 * <p>Background texture lives at
 * {@code assets/productivefrogs/textures/gui/container/slurry_press.png}
 * (composed by {@code scripts/generate_slurry_press_gui.py}); the arrow
 * sprite is re-inlined at (176, 14) like the other appliance GUIs.
 */
public class SlurryPressScreen extends PFContainerScreen<SlurryPressMenu> {

    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
        ProductiveFrogs.MOD_ID, "textures/gui/container/slurry_press.png");

    private static final int ARROW_BG_X = 79;
    private static final int ARROW_BG_Y = 34;
    private static final int ARROW_SRC_X = 176;
    private static final int ARROW_SRC_Y = 14;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 16;

    private static final int BG_TEX_WIDTH = 256;
    private static final int BG_TEX_HEIGHT = 256;

    public SlurryPressScreen(SlurryPressMenu menu, Inventory playerInv, Component title) {
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
        int total = this.menu.getProgressTotal();
        if (progress > 0 && total > 0) {
            int filled = Math.min(ARROW_WIDTH, (progress * ARROW_WIDTH) / total);
            gui.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND,
                     x + ARROW_BG_X, y + ARROW_BG_Y,
                     (float) ARROW_SRC_X, (float) ARROW_SRC_Y,
                     filled, ARROW_HEIGHT,
                     BG_TEX_WIDTH, BG_TEX_HEIGHT);
        }
    }
}
