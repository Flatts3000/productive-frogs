package com.flatts.productivefrogs.client.screen;

import com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity;
import com.flatts.productivefrogs.content.menu.TerrariumControllerMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Status screen for the Terrarium Controller: formed-state (with the first
 * structural problem when not formed) and the milk-buffer fill. Procedural panel,
 * no GUI sheet. The "why won't it form" diagnostic lives here now.
 */
public class TerrariumControllerScreen extends PFContainerScreen<TerrariumControllerMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF555555;
    private static final int BAR_BG = 0xFF373737;
    private static final int BAR_FILL = 0xFFE8E0D8;

    private static final int BAR_X = 18;
    private static final int BAR_Y = 50;
    private static final int BAR_W = 140;
    private static final int BAR_H = 10;

    public TerrariumControllerScreen(TerrariumControllerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 96;
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        gui.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);
        gui.fill(x, y, x + this.imageWidth, y + 1, BEVEL_LIGHT);
        gui.fill(x, y, x + 1, y + this.imageHeight, BEVEL_LIGHT);
        gui.fill(x, y + this.imageHeight - 1, x + this.imageWidth, y + this.imageHeight, BEVEL_DARK);
        gui.fill(x + this.imageWidth - 1, y, x + this.imageWidth, y + this.imageHeight, BEVEL_DARK);

        // Milk-buffer fill bar.
        int bx = x + BAR_X;
        int by = y + BAR_Y;
        gui.fill(bx - 1, by - 1, bx + BAR_W + 1, by + BAR_H + 1, BAR_BG);
        int depth = Math.max(1, this.menu.bufferDepth());
        int filled = Math.max(0, Math.min(BAR_W, this.menu.charges() * BAR_W / depth));
        gui.fill(bx, by, bx + filled, by + BAR_H, BAR_FILL);
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        gui.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);

        Component structure;
        if (this.menu.formed()) {
            structure = Component.translatable("message.productivefrogs.terrarium.formed")
                .withStyle(ChatFormatting.DARK_GREEN);
        } else {
            int idx = this.menu.problemIndex();
            String key = idx >= 0 && idx < TerrariumControllerBlockEntity.PROBLEM_KEYS.length
                ? TerrariumControllerBlockEntity.PROBLEM_KEYS[idx] : null;
            Component reason = key != null
                ? Component.translatable("message.productivefrogs.terrarium." + key)
                : Component.translatable("message.productivefrogs.terrarium.not_formed_generic");
            structure = reason.copy().withStyle(ChatFormatting.DARK_RED);
        }
        gui.drawString(this.font, structure, 18, 24, 0x404040, false);
        gui.drawString(this.font,
            Component.translatable("productivefrogs.gui.controller.buffer", this.menu.charges(), this.menu.bufferDepth()),
            18, 38, 0x404040, false);

        // Multiblock contents: Sprinkler / Incubator counts (dashes when unformed).
        boolean formed = this.menu.formed();
        Object sprinklers = formed ? this.menu.sprinklerCount() : "-";
        Object incubators = formed ? this.menu.incubatorCount() : "-";
        gui.drawString(this.font,
            Component.translatable("productivefrogs.gui.controller.sprinklers", String.valueOf(sprinklers)),
            18, 64, 0x404040, false);
        gui.drawString(this.font,
            Component.translatable("productivefrogs.gui.controller.incubators", String.valueOf(incubators)),
            18, 74, 0x404040, false);
    }
}
