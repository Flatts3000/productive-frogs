package com.flatts.productivefrogs.client.screen;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.menu.SpawneryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Client screen for the {@link com.flatts.productivefrogs.content.block.SpawneryBlock}.
 * Furnace-shaped: bottle + fuel column (with burn flame between), primer + output
 * column, and a cook-progress arrow bridging them.
 *
 * <p>Background lives at {@code assets/productivefrogs/textures/gui/container/spawnery.png},
 * composited from vanilla furnace.png by {@code scripts/generate_spawnery_gui.ps1}
 * (primer slot frame added, vanilla burn-flame + progress-arrow sprites re-inlined
 * into the (176, *) atlas region so these blits work).
 */
public class SpawneryScreen extends AbstractContainerScreen<SpawneryMenu> {

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
        ProductiveFrogs.MOD_ID, "textures/gui/container/spawnery.png");

    // Cook-progress arrow: vanilla furnace burn_progress sprite (24x16) re-inlined
    // at (176, 14); drawn between the input column and the output slot.
    private static final int ARROW_BG_X = 79;
    private static final int ARROW_BG_Y = 34;
    private static final int ARROW_SRC_X = 176;
    private static final int ARROW_SRC_Y = 14;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 16;

    // Burn flame: vanilla furnace lit_progress sprite (14x14) re-inlined at (176, 0);
    // drawn just above the fuel slot, burning down from the top as fuel depletes.
    private static final int FLAME_BG_X = 56;
    private static final int FLAME_BG_Y = 36;
    private static final int FLAME_SRC_X = 176;
    private static final int FLAME_SIZE = 14;

    private static final int BG_TEX_WIDTH = 256;
    private static final int BG_TEX_HEIGHT = 256;

    public SpawneryScreen(SpawneryMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        // AbstractContainerScreen#render in NeoForge 1.21.1 draws the background,
        // slots, and labels but does NOT render item tooltips - a renderBg-only
        // screen shows none. Add the tooltip pass here; super.render does not
        // render it, so there's no double-draw.
        super.render(gui, mouseX, mouseY, partialTick);
        this.renderTooltip(gui, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        gui.blit(BACKGROUND, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight,
                 BG_TEX_WIDTH, BG_TEX_HEIGHT);

        // Burn flame - shrinks from the top as burnTime drops. litPixels is the
        // visible flame height (0..14); the source row + height always stay within
        // the 14px sprite (no negative source Y even at full burn, unlike the naive
        // 12-lit form).
        int burn = this.menu.getBurnTime();
        int burnTotal = this.menu.getBurnDuration();
        if (burn > 0 && burnTotal > 0) {
            int litPixels = Math.min(FLAME_SIZE, burn * FLAME_SIZE / burnTotal);
            if (litPixels > 0) {
                int top = FLAME_SIZE - litPixels;
                gui.blit(BACKGROUND,
                         x + FLAME_BG_X, y + FLAME_BG_Y + top,
                         FLAME_SRC_X, top,
                         FLAME_SIZE, litPixels,
                         BG_TEX_WIDTH, BG_TEX_HEIGHT);
            }
        }

        // Cook-progress arrow - fills left-to-right as the cook approaches completion.
        int progress = this.menu.getCookProgress();
        int total = this.menu.getCookTotal();
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
