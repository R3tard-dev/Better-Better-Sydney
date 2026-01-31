package me.aidan.sydney.modules.impl.combat;

import me.aidan.sydney.events.SubscribeEvent;
import me.aidan.sydney.events.impl.TickEvent;
import me.aidan.sydney.modules.Module;
import me.aidan.sydney.modules.RegisterModule;
import me.aidan.sydney.settings.impl.BooleanSetting;
import me.aidan.sydney.settings.impl.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

@RegisterModule(name = "AutoAnvil", description = "Places anvils above players at the highest possible position.", category = Module.Category.COMBAT)
public class AutoAnvilModule extends Module {
    
    private long lastPlaceTime = 0;
    
    public NumberSetting delay = new NumberSetting("Delay", "Delay between anvil placements in milliseconds.", "ms", 250, 250, 1500);
    public BooleanSetting rotate = new BooleanSetting("Rotate", "Rotates to the block before placing.", true);
    public BooleanSetting strictDirection = new BooleanSetting("Strict Direction", "Uses strict direction checking for placement.", false);
    public NumberSetting range = new NumberSetting("Range", "Maximum range to target players.", "blocks", 10, 1, 20);
    public BooleanSetting autoDisable = new BooleanSetting("Auto Disable", "Automatically disables if no anvil is found.", true);
    
    private Entity target;
    
    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;
        
        int anvilSlot = getHotbarItemSlot(Items.ANVIL);
        if (anvilSlot == -1) {
            if (autoDisable.getValue()) {
                setToggled(false);
            }
            return;
        }
        
        target = getClosestPlayer(range.getValue().floatValue());
        if (target == null) return;
        
        if (System.currentTimeMillis() - lastPlaceTime < delay.getValue().longValue()) return;
        
        BlockPos placePos = getHighestPlaceablePos((PlayerEntity) target);
        if (placePos == null) return;
        
        Direction direction = getPlaceableSide(placePos);
        if (direction == null) return;
        
        mc.player.getInventory().selectedSlot = anvilSlot;
        
        if (rotate.getValue()) {
            float[] rotations = getBlockRotations(placePos, direction);
            if (rotations != null) {
                mc.player.setYaw(rotations[0]);
                mc.player.setPitch(rotations[1]);
            }
        }
        
        placeBlock(placePos, direction);
        lastPlaceTime = System.currentTimeMillis();
    }
    
    private int getHotbarItemSlot(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                return i;
            }
        }
        return -1;
    }
    
    private Entity getClosestPlayer(float range) {
        Entity closest = null;
        double closestDist = range;
        
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            if (!(entity instanceof PlayerEntity)) continue;
            
            double dist = mc.player.distanceTo(entity);
            if (dist < closestDist) {
                closest = entity;
                closestDist = dist;
            }
        }
        
        return closest;
    }
    
    private BlockPos getHighestPlaceablePos(PlayerEntity player) {
        BlockPos playerPos = player.getBlockPos();
        
        for (int y = 2; y <= 5; y++) {
            BlockPos checkPos = playerPos.up(y);
            
            if (mc.world.getBlockState(checkPos).isReplaceable() && 
                canPlaceBlock(checkPos)) {
                return checkPos;
            }
        }
        
        return null;
    }
    
    private boolean canPlaceBlock(BlockPos pos) {
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        
        return getPlaceableSide(pos) != null;
    }
    
    private Direction getPlaceableSide(BlockPos pos) {
        Direction[] directions = strictDirection.getValue() 
            ? new Direction[]{Direction.DOWN, Direction.UP}
            : Direction.values();
        
        for (Direction dir : directions) {
            BlockPos neighbor = pos.offset(dir);
            Block block = mc.world.getBlockState(neighbor).getBlock();
            
            if (block != Blocks.AIR && block != Blocks.WATER && block != Blocks.LAVA) {
                return dir;
            }
        }
        
        return null;
    }
    
    private float[] getBlockRotations(BlockPos pos, Direction side) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d blockCenter = Vec3d.of(pos).add(0.5, 0.5, 0.5);
        Vec3d directionVec = Vec3d.of(side.getVector()).multiply(0.5);
        Vec3d targetVec = blockCenter.add(directionVec);
        
        double diffX = targetVec.x - eyePos.x;
        double diffY = targetVec.y - eyePos.y;
        double diffZ = targetVec.z - eyePos.z;
        
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));
        
        return new float[]{yaw, pitch};
    }
    
    private void placeBlock(BlockPos pos, Direction side) {
        BlockPos neighbor = pos.offset(side);
        Direction opposite = side.getOpposite();
        
        Vec3d hitVec = Vec3d.of(neighbor).add(0.5, 0.5, 0.5)
            .add(Vec3d.of(opposite.getVector()).multiply(0.5));
        
        BlockHitResult hitResult = new BlockHitResult(
            hitVec,
            opposite,
            neighbor,
            false
        );
        
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(
            Hand.MAIN_HAND,
            hitResult,
            0
        ));
        
        mc.player.swingHand(Hand.MAIN_HAND);
    }
    
    @Override
    public String getMetaData() {
        return target != null ? target.getName().getString() : null;
    }
}