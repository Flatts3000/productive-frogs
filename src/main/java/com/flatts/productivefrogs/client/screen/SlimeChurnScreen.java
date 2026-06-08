package com.flatts.productivefrogs.client.screen;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.menu.SlimeChurnMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Client screen for the Slime Churn (#187). Furnace-family layout: two
 * stacked input slots on the left (milk bucket over empty buckets), two
 * stacked output slots on the right (slime buckets over spent containers),
 * progress arrow between, filling left-to-right as the spawn-interval
 * countdown approaches the next event.
 *
 * <p>Background texture lives at
 * {@code assets/productivefrogs/textures/gui/container/slime_churn.png}
 * (composed by {@code scripts/generate_slime_churn_gui.ps1}); the arrow
 * sprite is re-inlined at (176, 14) like the other appliance GUIs.
 */
public class SlimeChurnScreen extends PFContainerScreen<SlimeChurnMenu> {

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
        ProductiveFrogs.MOD_ID, "textures/gui/container/slime_churn.png");

    private static final int ARROW_BG_X = 79;
    private static final int ARROW_BG_Y = 34;
    private static final int ARROW_SRC_X = 176;
    private static final int ARROW_SRC_Y = 14;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 16;

    private static final int BG_TEX_WIDTH = 256;
    private static final int BG_TEX_HEIGHT = 256;

    public SlimeChurnScreen(SlimeChurnMenu menu, Inventory playerInv, Component title) {
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

        // Interval-progress arrow. Unlike the Milker's fixed 100-tick cook,
        // the churn interval is randomized per event (the source cadence), so
        // the fill is progress/total of the CURRENT interval - it still reads
        // as "how close is the next slime".
        int progress = this.menu.getIntervalProgress();
        int total = this.menu.getIntervalTotal();
        if (progress > 0 && total > 0) {
            int filled = Math.min(ARROW_WIDTH, (progress * ARROW_WIDTH) / total);
            gui.blit(BACKGROUND,
                     x + ARROW_BG_X, y + ARROW_BG_Y,
                     ARROW_SRC_X, ARROW_SRC_Y,
                     filled, ARROW_HEIGHT,
                     BG_TEX_WIDTH, BG_TEX_HEIGHT);
        }
    }
}
