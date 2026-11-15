package com.talviom.bodycamhorror.match;

import com.talviom.bodycamhorror.horror.FearSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

public class MatchEvents {
    private final MatchManager match = MatchManager.INSTANCE;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) match.tick(server);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (match.onPlayerKilled(p.server, p)) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer && match.damageBlocked()) e.setCanceled(true);
    }

    /** Получил или нанёс урон: перестрелка, хоррор-события ненадолго замолкают. */
    @SubscribeEvent
    public void onMatchHurt(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer victim) FearSystem.INSTANCE.markCombat(victim);
        if (e.getSource().getEntity() instanceof ServerPlayer attacker) FearSystem.INSTANCE.markCombat(attacker);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) match.onPlayerLeft(p.server, p);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) match.onPlayerJoined(p);
    }
}
