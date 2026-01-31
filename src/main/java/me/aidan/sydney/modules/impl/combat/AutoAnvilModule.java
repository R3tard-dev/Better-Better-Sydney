package me.aidan.sydney.modules.impl.combat;

import me.aidan.sydney.Sydney;
import me.aidan.sydney.events.SubscribeEvent;
import me.aidan.sydney.events.impl.PlayerUpdateEvent;
import me.aidan.sydney.modules.Module;
import me.aidan.sydney.modules.RegisterModule;
import me.aidan.sydney.settings.impl.BooleanSetting;
import me.aidan.sydney.settings.impl.ModeSetting;
import me.aidan.sydney.settings.impl.NumberSetting;
import me.aidan.sydney.utils.minecraft.InventoryUtils;
import me.aidan.sydney.utils.minecraft.WorldUtils;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

@RegisterModule(name = "AutoAnvil", description = "Places anvils above players at the highest possible position.", category = Module.Category.COMBAT)
public class AutoAnvilModule extends Module {
    
    public ModeSetting autoSwitch = new ModeSetting("Switch", "The mode that will be used for automatically switching to anvils.", "Silent", InventoryUtils.SWITCH_MODES);
    public NumberSetting delay = new NumberSetting("Delay", "The amount of ticks that have to be waited for between placements.", "ticks", 0, 0, 20);
    public BooleanSetting rotate = new BooleanSetting("Rotate", "Sends a packet rotation whenever placing a block.", true);
    public BooleanSetting strictDirection = new BooleanSetting("StrictDirection", "Only places using directions that face you.", false);
    public NumberSetting range = new NumberSetting("Range", "Maximum range to target players.", "blocks", 10.0, 1.0, 20.0);
    public BooleanSetting crystalDestruction = new BooleanSetting("CrystalDestruction", "Destroys any crystals that interfere with block placement.", true);
    public BooleanSetting render = new BooleanSetting("Render", "Whether or not to render the place position.", true);
    public BooleanSetting autoDisable = new BooleanSetting("AutoDisable", "Automatically disables if no anvil is found.", true);
    
    private PlayerEntity target;
    private int ticks = 0;
    
    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (mc.player == null || mc.world == null) return;
        
        if (ticks < delay.getValue().intValue()) {
            ticks++;
            return;
        }
        
        int anvilSlot = InventoryUtils.find(Items.ANVIL, 0, 
            autoSwitch.getValue().equalsIgnoreCase("AltSwap") || autoSwitch.getValue().equalsIgnoreCase("AltPickup") ? 35 : 8);
        
        if (anvilSlot == -1) {
            if (autoDisable.getValue()) {
                Sydney.CHAT_MANAGER.tagged("No anvils could be found in your hotbar.", getName());
                setToggled(false);
            }
            return;
        }
        
        int previousSlot = mc.player.getInventory().selectedSlot;
        
        target = getClosestPlayer();
        if (target == null) return;
        
        BlockPos placePos = getHighestPlaceablePos(target);
        if (placePos == null) return;
        
        Direction direction = WorldUtils.getDirection(placePos, strictDirection.getValue());
        if (direction == null) return;
        
        InventoryUtils.switchSlot(autoSwitch.getValue(), anvilSlot, previousSlot);
        
        WorldUtils.placeBlock(placePos, direction, Hand.MAIN_HAND, rotate.getValue(), crystalDestruction.getValue(), render.getValue());
        
        InventoryUtils.switchBack(autoSwitch.getValue(), anvilSlot, previousSlot);
        
        ticks = 0;
    }
    
    private PlayerEntity getClosestPlayer() {
        PlayerEntity closest = null;
        double closestDist = range.getValue().doubleValue();
        
        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (!player.isAlive() || player.getHealth() <= 0.0f) continue;
            if (Sydney.FRIEND_MANAGER.contains(player.getName().getString())) continue;
            
            double dist = mc.player.squaredDistanceTo(player);
            if (dist > MathHelper.square(range.getValue().doubleValue())) continue;
            
            if (closest == null || dist < closestDist) {
                closest = player;
                closestDist = dist;
            }
        }
        
        return closest;
    }
    
    private BlockPos getHighestPlaceablePos(PlayerEntity player) {
        BlockPos playerPos = player.getBlockPos();
        
        // Start from 2 blocks above player's feet and go up to 5 blocks
        for (int y = 2; y <= 5; y++) {
            BlockPos checkPos = playerPos.up(y);
            
            // Check if position is replaceable and placeable
            if (mc.world.getBlockState(checkPos).isReplaceable() && 
                WorldUtils.isPlaceable(checkPos) &&
                WorldUtils.getDirection(checkPos, strictDirection.getValue()) != null) {
                return checkPos;
            }
        }
        
        return null;
    }
    
    @Override
    public String getMetaData() {
        return target != null ? target.getName().getString() : null;
    }
}