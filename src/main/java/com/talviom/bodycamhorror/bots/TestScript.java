package com.talviom.bodycamhorror.bots;

import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Сценарий проверки без человека. Включается только свойством JVM {@code -Dbh.testScript=<файл>}
 * (в обычной игре его нет, и класс ничего не делает).
 *
 * Сервер после запуска построчно выполняет файл и пишет всё в лог с пометкой [bh-test]:
 *   # комментарий
 *   wait 40            подождать 40 тиков
 *   expect текст       в ответе на прошлую команду должен быть этот текст, иначе FAIL
 *   reject текст       в ответе на прошлую команду этого текста быть не должно
 *   shot имя           скриншот клиента в run/screenshots/имя.png (с шейдерами, как по F2)
 *   hud on|off         показать или спрятать интерфейс (как F1)
 *   screen chat|inventory|menu|pause|close   чат (как T), инвентарь мода (как Tab), главное меню мода, меню паузы (как Esc) или закрыть экран
 *   waitplayers 2 2400 ждать, пока на сервере будет 2 игрока (не дольше 2400 тиков, иначе FAIL);
 *                      так сценарий по сети ждёт второе окно игры (gradlew runClient -PbhJoin=localhost:25565)
 *   любая другая строка выполняется как команда сервера (без косой черты)
 * В конце пишет [bh-test] DONE и число ошибок.
 */
public final class TestScript {
    private static final String PREFIX = "[bh-test] ";

    private final List<String> lines = new ArrayList<>();
    private final StringBuilder lastOutput = new StringBuilder();
    private int next;
    private int waitTicks;
    private int failures;
    private boolean done = true;
    /** waitplayers: сколько игроков ждём и сколько тиков ещё готовы ждать. */
    private int waitPlayers;
    private int waitPlayersTicks;

    private final CommandSource sink = new CommandSource() {
        @Override
        public void sendSystemMessage(Component message) {
            String text = message.getString();
            lastOutput.append(text).append('\n');
            BodycamHorror.LOGGER.info(PREFIX + "  < " + text);
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    };

    @SubscribeEvent
    public void onTestServerStarted(ServerStartedEvent e) {
        String file = System.getProperty("bh.testScript");
        if (file == null || file.isBlank()) return;
        try {
            lines.clear();
            lines.addAll(Files.readAllLines(Path.of(file), StandardCharsets.UTF_8));
            next = 0;
            failures = 0;
            // Даём миру и игроку пару секунд на загрузку.
            waitTicks = 60;
            waitPlayers = 0;
            done = false;
            // Только в сценарии проверки (свойство есть лишь в dev-запуске): второе окно игры
            // заходит под своим именем без входа в аккаунт, поэтому сервер не проверяет имена.
            e.getServer().setUsesAuthentication(false);
            // Пока идёт сценарий, игра не встаёт на паузу, если окно потеряло фокус: можно работать в других окнах.
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientShots.keepRunningUnfocused(true));
            BodycamHorror.LOGGER.info(PREFIX + "START " + file + " (" + lines.size() + " lines)");
        } catch (IOException ex) {
            BodycamHorror.LOGGER.error(PREFIX + "Cannot read " + file, ex);
        }
    }

    @SubscribeEvent
    public void onTestServerTick(TickEvent.ServerTickEvent e) {
        if (done || e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        // На встроенном сервере ждём, пока в мир зайдёт хозяин.
        if (!server.isDedicatedServer() && server.getPlayerList().getPlayerCount() == 0) return;
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        if (waitPlayers > 0) {
            int count = server.getPlayerList().getPlayerCount();
            if (count >= waitPlayers) {
                BodycamHorror.LOGGER.info(PREFIX + "PASS waitplayers " + waitPlayers + " (" + count + " on the server)");
                waitPlayers = 0;
                waitTicks = 40; // дать вошедшему догрузиться
                return;
            }
            if (--waitPlayersTicks <= 0) {
                failures++;
                BodycamHorror.LOGGER.error(PREFIX + "FAIL waitplayers " + waitPlayers + " (only " + count + " on the server)");
                waitPlayers = 0;
            }
            return;
        }
        while (next < lines.size() && waitTicks == 0) {
            String line = lines.get(next++).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("waitplayers ")) {
                String[] parts = line.substring(12).trim().split("\\s+");
                waitPlayers = Integer.parseInt(parts[0]);
                waitPlayersTicks = parts.length > 1 ? Integer.parseInt(parts[1]) : 2400;
                BodycamHorror.LOGGER.info(PREFIX + "> " + line);
                break;
            } else if (line.startsWith("wait ")) {
                waitTicks = Integer.parseInt(line.substring(5).trim());
            } else if (line.startsWith("expect ")) {
                check(line.substring(7).trim(), true);
            } else if (line.startsWith("reject ")) {
                check(line.substring(7).trim(), false);
            } else if (line.startsWith("shot ")) {
                String name = line.substring(5).trim();
                BodycamHorror.LOGGER.info(PREFIX + "> " + line);
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientShots.shot(name));
            } else if (line.startsWith("hud ")) {
                boolean visible = line.substring(4).trim().equals("on");
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientShots.hud(visible));
            } else if (line.startsWith("press ")) {
                String label = line.substring(6).trim();
                BodycamHorror.LOGGER.info(PREFIX + "> " + line);
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientShots.press(label));
            } else if (line.startsWith("screen ")) {
                screen(server, line.substring(7).trim());
            } else if (line.startsWith("lang ")) {
                // Язык игры (ru_ru, en_us): перезагружает ресурсы, после этой строки нужен wait 100.
                String code = line.substring(5).trim();
                BodycamHorror.LOGGER.info(PREFIX + "> " + line);
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientShots.lang(code));
            } else {
                run(server, line);
            }
        }
        if (next >= lines.size() && waitTicks == 0) {
            done = true;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientShots.keepRunningUnfocused(false));
            BodycamHorror.LOGGER.info(PREFIX + "DONE failures=" + failures);
        }
    }

    /** Экран у хозяина мира (первый вошедший игрок): чат, инвентарь мода, главное меню мода или закрыть. */
    private void screen(MinecraftServer server, String what) {
        BodycamHorror.LOGGER.info(PREFIX + "> screen " + what);
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        net.minecraft.server.level.ServerPlayer host = server.getPlayerList().getPlayers().get(0);
        switch (what) {
            case "inventory" -> com.talviom.bodycamhorror.inventory.BhInventoryMenu.open(host);
            case "chat" -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientShots::openChat);
            case "menu" -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientShots::openMenu);
            case "pause" -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientShots::openPause);
            case "death" -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientShots::openDeath);
            case "options", "video", "packs", "worlds", "mods", "lan" -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientShots.openVanilla(what));
            default -> {
                host.closeContainer();
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientShots::closeScreen);
            }
        }
    }

    private void run(MinecraftServer server, String command) {
        lastOutput.setLength(0);
        BodycamHorror.LOGGER.info(PREFIX + "> " + command);
        ServerLevel level = server.overworld();
        CommandSourceStack source = new CommandSourceStack(sink, Vec3.atLowerCornerOf(level.getSharedSpawnPos()), Vec2.ZERO,
                level, 4, "BhTest", Component.literal("BhTest"), server, null);
        try {
            server.getCommands().performPrefixedCommand(source, command);
        } catch (RuntimeException ex) {
            failures++;
            BodycamHorror.LOGGER.error(PREFIX + "FAIL command threw: " + command, ex);
        }
    }

    private void check(String text, boolean mustContain) {
        boolean has = lastOutput.toString().contains(text);
        if (has == mustContain) {
            BodycamHorror.LOGGER.info(PREFIX + "PASS " + (mustContain ? "expect " : "reject ") + text);
        } else {
            failures++;
            BodycamHorror.LOGGER.error(PREFIX + "FAIL " + (mustContain ? "expect " : "reject ") + text);
        }
    }
}
