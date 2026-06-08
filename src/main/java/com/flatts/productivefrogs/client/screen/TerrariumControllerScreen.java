package com.flatts.productivefrogs.client.screen;

import com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity;
import com.flatts.productivefrogs.content.menu.TerrariumControllerMenu;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Status screen for the Terrarium Controller: a formed/not-formed indicator (with
 * the first structural problem when unformed), the buffered milk (variant swatch +
 * name + a variant-tinted fill bar with the charge count), and the multiblock's
 * Sprinkler / Incubator counts. Procedural panel, no GUI sheet.
 */
public class TerrariumControllerScreen extends PFContainerScreen<TerrariumControllerMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF555555;
    private static final int BAR_BORDER = 0xFF373737;
    private static final int BAR_TRACK = 0xFF8B8B8B;
    private static final int DEFAULT_MILK = 0xFFF0F0E0;
    private static final int OK_GREEN = 0xFF3FA037;
    private static final int BAD_RED = 0xFFB23030;
    private static final int SWATCH_BORDER = 0xFF2B2B2B;
    private static final int TEXT = 0x404040;

    private static final int DOT_X = 9;
    private static final int DOT_Y = 19;
    private static final int DOT = 8;
    private static final int SWATCH_X = 9;
    private static final int SWATCH_Y = 33;
    private static final int SWATCH = 9;
    private static final int BAR_X = 9;
    private static final int BAR_Y = 47;
    private static final int BAR_W = 158;
    private static final int BAR_H = 12;

    public TerrariumControllerScreen(TerrariumControllerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 94;
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

        boolean formed = this.menu.formed();

        // Formed indicator dot (green = formed, red = not).
        int dx = x + DOT_X;
        int dy = y + DOT_Y;
        gui.fill(dx - 1, dy - 1, dx + DOT + 1, dy + DOT + 1, SWATCH_BORDER);
        gui.fill(dx, dy, dx + DOT, dy + DOT, formed ? OK_GREEN : BAD_RED);

        // Milk swatch - only when milk is actually buffered (no empty placeholder).
        ResourceLocation variant = this.menu.tankVariant();
        int milkColor = variant == null ? DEFAULT_MILK : variantColor(variant);
        if (variant != null) {
            int sx = x + SWATCH_X;
            int sy = y + SWATCH_Y;
            gui.fill(sx - 1, sy - 1, sx + SWATCH + 1, sy + SWATCH + 1, SWATCH_BORDER);
            gui.fill(sx, sy, sx + SWATCH, sy + SWATCH, milkColor);
        }

        // Buffer bar: recessed track + variant-tinted fill.
        int bx = x + BAR_X;
        int by = y + BAR_Y;
        gui.fill(bx - 1, by - 1, bx + BAR_W + 1, by + BAR_H + 1, BAR_BORDER);
        gui.fill(bx, by, bx + BAR_W, by + BAR_H, BAR_TRACK);
        int depth = Math.max(1, this.menu.bufferDepth());
        int filled = Math.max(0, Math.min(BAR_W, this.menu.charges() * BAR_W / depth));
        if (filled > 0) {
            gui.fill(bx, by, bx + filled, by + BAR_H, variant == null ? DEFAULT_MILK : milkColor);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        gui.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT, false);

        // Formed / problem line beside the indicator dot.
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
        gui.drawString(this.font, structure, DOT_X + DOT + 4, DOT_Y, TEXT, false);

        // Milk name beside the swatch - only when milk is buffered.
        ResourceLocation variant = this.menu.tankVariant();
        if (variant != null) {
            gui.drawString(this.font,
                Component.translatable("block.productivefrogs." + variant.getPath() + "_slime_milk"),
                SWATCH_X + SWATCH + 4, SWATCH_Y + 1, TEXT, false);
        }

        // Charge count overlaid on the buffer bar.
        gui.drawCenteredString(this.font, this.menu.charges() + " / " + this.menu.bufferDepth(),
            BAR_X + BAR_W / 2, BAR_Y + 2, 0xFFFFFFFF);

        // Multiblock contents + live population (dashes when unformed).
        boolean formed = this.menu.formed();
        int row = BAR_Y + BAR_H + 6;
        gui.drawString(this.font,
            Component.translatable("productivefrogs.gui.controller.sprinklers", formed ? String.valueOf(this.menu.sprinklerCount()) : "-"),
            BAR_X, row, TEXT, false);
        gui.drawString(this.font,
            Component.translatable("productivefrogs.gui.controller.incubators", formed ? String.valueOf(this.menu.incubatorCount()) : "-"),
            BAR_X + 88, row, TEXT, false);
        gui.drawString(this.font,
            Component.translatable("productivefrogs.gui.controller.frogs",
                formed ? this.menu.frogCount() + " / " + this.menu.frogCap() : "-"),
            BAR_X, row + 11, TEXT, false);
    }

    /** Opaque ARGB primary colour for a milk variant, or the milky default when unresolved. */
    private static int variantColor(ResourceLocation variant) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return DEFAULT_MILK;
        }
        Registry<SlimeVariant> registry = mc.level.registryAccess()
            .registry(PFRegistries.SLIME_VARIANT).orElse(null);
        if (registry == null) {
            return DEFAULT_MILK;
        }
        SlimeVariant v = registry.get(variant);
        return v == null ? DEFAULT_MILK : (0xFF000000 | v.primaryColor());
    }
}
