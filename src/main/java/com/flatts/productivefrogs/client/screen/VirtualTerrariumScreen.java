package com.flatts.productivefrogs.client.screen;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.entity.VirtualTerrariumBlockEntity;
import com.flatts.productivefrogs.content.menu.VirtualTerrariumMenu;
import com.flatts.productivefrogs.content.multiblock.MilkCharge;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFFluids;
import com.flatts.productivefrogs.registry.PFRegistries;
import com.flatts.productivefrogs.registry.PFVariantMilk;
import com.flatts.productivefrogs.util.VariantNames;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Client screen for the Virtual Terrarium Processor: a void-tier panel laying out
 * the RF meter (shown only with an Overclock installed), the feedstock fluid slot,
 * the frog slot, the 3x2 output grid, the molten product tank, and the upgrade
 * column. Live amounts ride the synced {@code ContainerData}; fluid TYPES come off
 * the client BlockEntity. Left-clicking the feedstock slot with a cursor-held bucket
 * fills the tank via {@link VirtualTerrariumMenu#clickMenuButton}.
 */
public class VirtualTerrariumScreen extends PFContainerScreen<VirtualTerrariumMenu> {

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
        ProductiveFrogs.MOD_ID, "textures/gui/container/virtual_terrarium.png");

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 180;
    private static final int BG_TEX_WIDTH = 256;
    private static final int BG_TEX_HEIGHT = 256;

    private static final int RF_X = 8, RF_Y = 18, RF_W = 14, RF_H = 54;
    private static final int FEED_X = 26, FEED_Y = 18, FEED_SIZE = 16;
    private static final int TANK_X = 130, TANK_Y = 18, TANK_W = 16, TANK_H = 54;

    private static final int ARROW_X = 46, ARROW_Y = 31;
    private static final int ARROW_SRC_X = 176, ARROW_SRC_Y = 14;
    private static final int ARROW_WIDTH = 24, ARROW_HEIGHT = 16;

    public VirtualTerrariumScreen(VirtualTerrariumMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
        this.inventoryLabelY = this.imageHeight - 92;
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        gui.blit(BACKGROUND, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, BG_TEX_WIDTH, BG_TEX_HEIGHT);

        // RF meter - only shown when the block needs power (an Overclock is installed);
        // otherwise paint the baked frame over with the panel colour so it disappears.
        if (powered()) {
            fillMeter(gui, x + RF_X, y + RF_Y, RF_W, RF_H,
                this.menu.getEnergyStored(), VirtualTerrariumBlockEntity.ENERGY_CAPACITY, 0xFFC0402A);
        } else {
            gui.fill(x + RF_X - 1, y + RF_Y - 1, x + RF_X + RF_W + 1, y + RF_Y + RF_H + 1, 0xFFC6C6C6);
        }
        // Feedstock fluid slot - fills by the milk's SPAWN budget (the liquid stays until spent).
        fillMeter(gui, x + FEED_X, y + FEED_Y, FEED_SIZE, FEED_SIZE, feedstockSpawns(), feedstockSpawnCap(), feedstockColor());
        // Molten product tank.
        fillMeter(gui, x + TANK_X, y + TANK_Y, TANK_W, TANK_H, this.menu.getProductAmount(), productCapacity(), productColor());

        int interval = this.menu.getInterval();
        int filled = interval > 0 ? Math.min(ARROW_WIDTH, (this.menu.getProgress() * ARROW_WIDTH) / interval) : 0;
        if (filled > 0) {
            gui.blit(BACKGROUND, x + ARROW_X, y + ARROW_Y, ARROW_SRC_X, ARROW_SRC_Y,
                filled, ARROW_HEIGHT, BG_TEX_WIDTH, BG_TEX_HEIGHT);
        }
    }

    /** Bottom-up flat-colour fill of a meter frame. */
    private static void fillMeter(GuiGraphics gui, int mx, int my, int w, int h, int amount, int cap, int argb) {
        if (amount <= 0 || cap <= 0) {
            return;
        }
        int filled = Math.min(h, (int) ((long) amount * h / cap));
        if (filled > 0) {
            gui.fill(mx, my + h - filled, mx + w, my + h, argb);
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        if (inside(mouseX, mouseY, x + FEED_X, y + FEED_Y, FEED_SIZE, FEED_SIZE)) {
            gui.renderComponentTooltip(this.font, feedstockTooltip(), mouseX, mouseY);
        } else if (powered() && inside(mouseX, mouseY, x + RF_X, y + RF_Y, RF_W, RF_H)) {
            gui.renderComponentTooltip(this.font, List.of(Component.translatable("productivefrogs.gui.energy_amount",
                this.menu.getEnergyStored(), VirtualTerrariumBlockEntity.ENERGY_CAPACITY).withStyle(ChatFormatting.GRAY)),
                mouseX, mouseY);
        } else if (inside(mouseX, mouseY, x + TANK_X, y + TANK_Y, TANK_W, TANK_H)) {
            gui.renderComponentTooltip(this.font, productTooltip(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        super.renderLabels(gui, mouseX, mouseY);
        VirtualTerrariumBlockEntity.Status status = this.menu.getStatus();
        if (status == VirtualTerrariumBlockEntity.Status.PRODUCING) {
            return;
        }
        Component text = Component.translatable("productivefrogs.gui.vt.status." + status.name().toLowerCase(Locale.ROOT));
        int color = switch (status) {
            case MISMATCH -> 0xB0322A;
            case NEEDS_POWER -> 0xB07A20;
            default -> 0x707070;
        };
        gui.drawString(this.font, text, 8, 74, color, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int fx = (this.width - this.imageWidth) / 2 + FEED_X;
            int fy = (this.height - this.imageHeight) / 2 + FEED_Y;
            if (mouseX >= fx && mouseX < fx + FEED_SIZE && mouseY >= fy && mouseY < fy + FEED_SIZE
                    && VirtualTerrariumBlockEntity.isFeedstockBucket(this.menu.getCarried())
                    && this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, VirtualTerrariumMenu.FILL_FEEDSTOCK);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // -- fluid type / colour / tooltips (types off the client BE) --

    private FluidStack feedstock() {
        VirtualTerrariumBlockEntity be = this.menu.blockEntity();
        return be == null ? FluidStack.EMPTY : be.getFeedstock().getFluid();
    }

    /** True when the block draws RF (an Overclock is installed) - the only time the RF meter shows. */
    private boolean powered() {
        VirtualTerrariumBlockEntity be = this.menu.blockEntity();
        return be != null && be.hasPoweredUpgrade();
    }

    private int feedstockSpawns() {
        VirtualTerrariumBlockEntity be = this.menu.blockEntity();
        if (be == null) {
            return 0;
        }
        return be.feedstockInfinite() ? be.feedstockSpawnsCapacity() : be.feedstockSpawnsRemaining();
    }

    private int feedstockSpawnCap() {
        VirtualTerrariumBlockEntity be = this.menu.blockEntity();
        return be == null ? 0 : be.feedstockSpawnsCapacity();
    }

    private int feedstockColor() {
        FluidStack fluid = feedstock();
        ResourceLocation variant = PFVariantMilk.variantOf(fluid.getFluid());
        if (variant != null && this.minecraft != null && this.minecraft.level != null) {
            var registry = this.minecraft.level.registryAccess().registry(PFRegistries.SLIME_VARIANT).orElse(null);
            SlimeVariant v = registry == null ? null : registry.get(variant);
            if (v != null) {
                return 0xFF000000 | (v.primaryColor() & 0xFFFFFF);
            }
            return 0xFFB8E0C0;
        }
        if (fluid.is(PFFluids.MIMIC_MILK.get())) {
            return 0xFFE0A8E0;
        }
        return 0xFF8B8B8B;
    }

    private int productCapacity() {
        VirtualTerrariumBlockEntity be = this.menu.blockEntity();
        return be == null ? VirtualTerrariumBlockEntity.MOLTEN_CAPACITY : be.productCapacity();
    }

    private int productColor() {
        return 0xFFE0742A; // molten orange (the product tank is molten-only on this line)
    }

    private List<Component> feedstockTooltip() {
        List<Component> lines = new ArrayList<>();
        FluidStack fluid = feedstock();
        if (fluid.isEmpty()) {
            lines.add(Component.translatable("productivefrogs.gui.fluid_empty"));
            return lines;
        }
        lines.add(feedstockName(fluid));
        MilkCharge charge = MilkCharge.fromFluid(fluid);
        if (charge.infinite()) {
            lines.add(Component.translatable("productivefrogs.jade.spawns_unlimited").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("productivefrogs.jade.spawns_left",
                charge.spawnsRemaining(), charge.capacity()).withStyle(ChatFormatting.GRAY));
        }
        if (charge.speed() > 0) {
            lines.add(Component.translatable("productivefrogs.jade.catalyst_speed",
                charge.speed(), PFConfig.catalystMaxSpeedLevel()).withStyle(ChatFormatting.GRAY));
        }
        if (charge.quantity() > 0) {
            lines.add(Component.translatable("productivefrogs.jade.catalyst_quantity",
                charge.quantity(), PFConfig.catalystMaxQuantityLevel()).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    /**
     * Variant-aware feedstock name ("Iron Slime Milk"), built as a literal exactly like
     * the Basin / milk-source readouts; falls back to the fluid's hover name for Mimic Milk.
     */
    private static Component feedstockName(FluidStack fluid) {
        ResourceLocation variant = PFVariantMilk.variantOf(fluid.getFluid());
        if (variant != null) {
            return Component.literal(VariantNames.titleCase(variant) + " Slime Milk");
        }
        return fluid.getHoverName();
    }

    private List<Component> productTooltip() {
        VirtualTerrariumBlockEntity be = this.menu.blockEntity();
        FluidStack fluid = be == null ? FluidStack.EMPTY : be.getMoltenTank().getFluid();
        List<Component> lines = new ArrayList<>();
        if (fluid.isEmpty()) {
            lines.add(Component.translatable("productivefrogs.gui.fluid_empty"));
        } else {
            lines.add(fluid.getHoverName());
            lines.add(Component.translatable("productivefrogs.gui.fluid_amount",
                this.menu.getProductAmount(), productCapacity()).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }
}
