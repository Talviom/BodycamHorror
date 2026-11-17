package com.talviom.bodycamhorror.bots;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Боты для проверки мультиплеера в одиночку: настоящие серверные игроки без клиента.
 * Сервер считает их обычными игроками (команды, раунды, страх, фонарик, смерть, наблюдение),
 * а все пакеты для них просто выбрасываются. Боты стоят на месте и ничего не делают сами:
 * ими управляют командами (/bh bot ..., /tp, /kill, /damage, /execute as <бот> run ...).
 */
public final class BotPlayers {
    private static final Map<String, ServerPlayer> BOTS = new LinkedHashMap<>();

    private BotPlayers() {}

    /** Соединение без клиента: всё, что сервер отправляет боту, выбрасывается. */
    private static final class BotConnection extends Connection {
        BotConnection() {
            super(PacketFlow.SERVERBOUND);
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
        }
    }

    public static boolean isBot(ServerPlayer p) {
        return BOTS.get(p.getGameProfile().getName()) == p;
    }

    @Nullable
    public static ServerPlayer get(String name) {
        return BOTS.get(name);
    }

    public static Collection<String> names() {
        return new ArrayList<>(BOTS.keySet());
    }

    /** @return новый бот или null, если игрок с таким именем уже на сервере */
    @Nullable
    public static ServerPlayer spawn(MinecraftServer server, String name, Vec3 pos, float yaw) {
        if (server.getPlayerList().getPlayerByName(name) != null) return null;
        ServerLevel level = server.overworld();
        GameProfile profile = new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name);
        ServerPlayer bot = new ServerPlayer(server, level, profile);
        BotConnection connection = new BotConnection();
        // Встроенный канал нужен, чтобы у соединения был настоящий (пустой) канал netty.
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, bot);
        bot.teleportTo(level, pos.x, pos.y, pos.z, yaw, 0f);
        BOTS.put(name, bot);
        return bot;
    }

    public static boolean remove(MinecraftServer server, String name) {
        ServerPlayer bot = BOTS.remove(name);
        if (bot == null) return false;
        // Через обычный выход игрока: срабатывают те же события, что и у живого человека.
        bot.connection.onDisconnect(net.minecraft.network.chat.Component.literal("Bot removed"));
        return true;
    }

    public static int removeAll(MinecraftServer server) {
        List<String> all = new ArrayList<>(BOTS.keySet());
        for (String name : all) remove(server, name);
        return all.size();
    }

    public static final class BotTickEvents {
        /** Клиент обычно сам двигает время своего игрока. У бота клиента нет, поэтому это делает сервер. */
        @SubscribeEvent
        public void onBotServerTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || BOTS.isEmpty()) return;
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            for (Map.Entry<String, ServerPlayer> en : BOTS.entrySet()) {
                ServerPlayer bot = en.getValue();
                if (bot.isDeadOrDying()) {
                    // Смерть вне матча: бот сразу возрождается, как игрок, нажавший «Respawn».
                    en.setValue(server.getPlayerList().respawn(bot, false));
                    continue;
                }
                bot.doTick();
            }
        }

        @SubscribeEvent
        public void onBotServerStopped(ServerStoppedEvent e) {
            BOTS.clear();
        }
    }
}
