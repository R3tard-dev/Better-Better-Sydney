package me.aidan.sydney.modules.impl.player;

import me.aidan.sydney.events.SubscribeEvent;
import me.aidan.sydney.events.impl.PacketSendEvent;
import me.aidan.sydney.modules.Module;
import me.aidan.sydney.modules.RegisterModule;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.*;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@RegisterModule(name = "Blink", description = "Holds packets and sends them all at once when disabled.", category = Module.Category.PLAYER)
public class BlinkModule extends Module {
    
    private final Queue<Packet<?>> packets = new ConcurrentLinkedQueue<>();

    @SubscribeEvent
    public void onPacketSend(PacketSendEvent event) {
        if (mc.player == null || mc.world == null) return;

        Packet<?> packet = event.getPacket();
        
        if (packet instanceof PlayerMoveC2SPacket || 
            packet instanceof PlayerActionC2SPacket ||
            packet instanceof ClientCommandC2SPacket ||
            packet instanceof PlayerInteractEntityC2SPacket ||
            packet instanceof PlayerInteractBlockC2SPacket ||
            packet instanceof PlayerInteractItemC2SPacket ||
            packet instanceof TeleportConfirmC2SPacket ||
            packet instanceof KeepAliveC2SPacket) {
            
            event.setCancelled(true);
            packets.add(packet);
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        
        if (!packets.isEmpty()) {
            for (Packet<?> packet : packets) {
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(packet);
                }
            }
            packets.clear();
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        packets.clear();
    }

    @Override
    public String getMetaData() {
        return String.valueOf(packets.size());
    }
}