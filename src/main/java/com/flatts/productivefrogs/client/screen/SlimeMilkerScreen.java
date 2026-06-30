package com.flatts.productivefrogs.client.screen;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity;
import com.flatts.productivefrogs.content.menu.SlimeMilkerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Client screen for the {@link com.flatts.productivefrogs.content.block.SlimeMilkerBlock}.
 * Mirrors vanilla's furnace screen shape — input slot + progress arrow +
 * output slot + standard 27 + 9 player inventory grid — but minus the
 * fuel slot and burn-flame indicator.
 *
 * <p>Background texture lives at
 * {@code assets/productivefrogs/textures/gui/container/slime_milker.png}.
 * The blit arrow uses a fixed 24×16 sprite drawn relative to the
 * cook-progress fraction from the menu's synced {@link
 * net.minecraft.world.inventory.ContainerData}.
 */
public class SlimeMilkerScreen extends PFContainerScreen<SlimeMilkerMenu> {

    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
        ProductiveFrogs.MOD_ID, "textures/gui/container/slime_milker.png");

    // Progress-arrow sprite layout on the background texture. (X1, Y1) is
    // the screen position; (X2, Y2) the source position on the PNG.
    // Width 24 / height 16 matches vanilla's furnace burn_progress sprite,
    // which generate_slime_milker_gui.ps1 composites into (176, 14) so this
    // blit pulls the sprite from our GUI background atlas. In MC 1.21.x
    // vanilla moved the arrow into a sprite-atlas entry instead of inlining
    // it in furnace.png; the script re-inlines so the existing blit works.
    private static final int ARROW_BG_X = 79;
    private static final int ARROW_BG_Y = 34;
    private static final int ARROW_SRC_X = 176;
    private static final int ARROW_SRC_Y = 14;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 16;

    // Background PNG is the standard 256x256 image with the 176x166 GUI
    // region in the top-left, matching vanilla furnace conventions.
    private static final int BG_TEX_WIDTH = 256;
    private static final int BG_TEX_HEIGHT = 256;

    public SlimeMilkerScreen(SlimeMilkerMenu menu, Inventory playerInv, Component title) {
        // Vanilla container size: 176x166 PNG region (the 3-arg base default).
        super(menu, playerInv, title);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(gui, mouseX, mouseY, partialTick);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        gui.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight,
                 BG_TEX_WIDTH, BG_TEX_HEIGHT);

        // Cook-progress arrow — width scales linearly with cookProgress
        // / cookTotal so the arrow "fills" left-to-right as the cook
        // approaches completion.
        int progress = this.menu.getCookProgress();
        int total = this.menu.getCookTotal();
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
