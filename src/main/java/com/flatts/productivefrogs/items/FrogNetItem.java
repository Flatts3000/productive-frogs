package com.flatts.productivefrogs.items;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

public class FrogNetItem extends Item {
    public FrogNetItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack itemStack = player.getStackInHand(hand);
        if (itemStack.hasNbt() && itemStack.getNbt().contains("captured_frog")) {
            // Release frog
            HitResult hitResult = world.rayTrace(new RaycastContext(player.getEyePos(), player.getEyePos().add(player.getRotationVector(1.0F).multiply(5.0D)), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                NbtCompound nbt = itemStack.getNbt().getCompound("captured_frog");
                Entity entity = EntityType.loadEntityWithPassengers(nbt, world, (entity) -> true);
                if (entity != null) {
                    entity.setPos(hitResult.getPos().x, hitResult.getPos().y, hitResult.getPos().z);
                    world.spawnEntity(entity);
                    itemStack.getNbt().remove("captured_frog");
                    return ActionResult.success(itemStack);
                }
            }
        } else {
            // Catch frog
            HitResult hitResult = world.rayTrace(new RaycastContext(player.getEyePos(), player.getEyePos().add(player.getRotationVector(1.0F).multiply(5.0D)), RaycastContext.ShapeType.ENTITY, RaycastContext.FluidHandling.NONE, player));
            if (hitResult.getType() == HitResult.Type.ENTITY) {
                Entity entity = hitResult.getEntity();
                if (entity instanceof ResourceFrogEntity) {
                    NbtCompound nbt = new NbtCompound();
                    entity.saveWithoutId(nbt);
                    itemStack.getOrCreateNbt().put("captured_frog", nbt);
                    entity.remove();
                    return ActionResult.success(itemStack);
                }
            }
        }
        return ActionResult.pass(itemStack);
    }
}