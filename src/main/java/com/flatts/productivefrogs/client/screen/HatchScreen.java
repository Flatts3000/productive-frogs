package com.flatts.productivefrogs.client.screen;

import com.flatts.productivefrogs.content.menu.HatchMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Client screen for the Hatch's 18-slot froglight output. Rendered as a vanilla
 * 2-row chest: the shared {@code generic_54.png} container texture, blitted as
 * the top (2 slot rows) + the player-inventory bottom, so the Hatch reads as an
 * ordinary chest the player takes Froglights out of (it is output-only - the
 * Terrarium fills it).
 */
public class HatchScreen extends PFContainerScreen<HatchMenu> {

    private static final Identifier CONTAINER =
        Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int ROWS = 2;

    public HatchScreen(HatchMenu menu, Inventory playerInv, Component title) {
        // vanilla generic-container height for 2 rows
        super(menu, playerInv, title, 176, 114 + ROWS * 18);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(gui, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;
        // Top: the title bar + ROWS slot rows.
        gui.blit(RenderPipelines.GUI_TEXTURED, CONTAINER, x, y, 0.0F, 0.0F, this.imageWidth, ROWS * 18 + 17, 256, 256);
        // Bottom: the player-inventory section (source v=126, height 96).
        gui.blit(RenderPipelines.GUI_TEXTURED, CONTAINER, x, y + ROWS * 18 + 17, 0.0F, 126.0F, this.imageWidth, 96, 256, 256);
    }
}
