package com.flatts.productivefrogs.client.screen;

import com.flatts.productivefrogs.content.menu.HatchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Client screen for the Hatch's 18-slot froglight output. Background is drawn
 * procedurally (a vanilla-grey panel with recessed slot frames) so it needs no
 * GUI sheet - a deliberate placeholder until a Terrarium GUI art pass, same
 * spirit as the Casting Mold's reused sheet.
 */
public class HatchScreen extends PFContainerScreen<HatchMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF555555;
    private static final int SLOT_FRAME = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;

    public HatchScreen(HatchMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 140;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        gui.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);
        // Light bevel top/left, dark bottom/right for a panel read.
        gui.fill(x, y, x + this.imageWidth, y + 1, BEVEL_LIGHT);
        gui.fill(x, y, x + 1, y + this.imageHeight, BEVEL_LIGHT);
        gui.fill(x, y + this.imageHeight - 1, x + this.imageWidth, y + this.imageHeight, BEVEL_DARK);
        gui.fill(x + this.imageWidth - 1, y, x + this.imageWidth, y + this.imageHeight, BEVEL_DARK);
        // Recessed frame behind every slot (output + player inventory).
        for (Slot slot : this.menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            gui.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_FRAME);
            gui.fill(sx, sy, sx + 16, sy + 16, SLOT_FILL);
        }
    }
}
