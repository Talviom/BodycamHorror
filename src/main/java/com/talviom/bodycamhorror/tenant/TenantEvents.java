package com.talviom.bodycamhorror.tenant;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Слух Жильца раз в 10 тиков и его команды. */
public class TenantEvents {
    private int ticks;

    @SubscribeEvent
    public void onTenantServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++ticks % 10 != 0) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) TenantHearing.tick(server);
    }

    @SubscribeEvent
    public void onTenantCommands(RegisterCommandsEvent e) {
        TenantCommand.register(e.getDispatcher());
    }

    @SubscribeEvent
    public void onTenantServerStopped(ServerStoppedEvent e) {
        TenantHearing.clear();
        TenantRules.clear();
    }
}
