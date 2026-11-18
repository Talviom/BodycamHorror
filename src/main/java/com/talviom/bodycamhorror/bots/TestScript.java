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
 *   screen chat|inventory|close   открыть чат (как T), инвентарь мода (как Tab) у хозяина мира или закрыть экран
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
            done = false;
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
        while (next < lines.size() && waitTicks == 0) {
            String line = lines.get(next++).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("wait ")) {
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
            } else if (line.startsWith("screen ")) {
                screen(server, line.substring(7).trim());
            } else {
                run(server, line);
            }
        }
        if (next >= lines.size() && waitTicks == 0) {
            done = true;
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
