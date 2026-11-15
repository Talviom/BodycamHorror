package com.talviom.bodycamhorror.flashlight;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

public class FlashlightEvents {
    @SubscribeEvent
    public void onFlashlightServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) Flashlight.tick(server);
    }

    @SubscribeEvent
    public void onFlashlightServerStopping(ServerStoppingEvent e) {
        Flashlight.clearAll();
    }
}
