package com.talviom.bodycamhorror.client;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.talviom.bodycamhorror.BhConfig;
import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

/**
 * Клиент: все игроки выглядят одинаково, в военной форме своей команды, какой бы скин ни стоял в лаунчере.
 * Alpha и Bravo по командам на табло, без команды (лобби, зрители) нейтральная форма.
 *
 * Скин подменяется прямо в записи игрока (PlayerInfo), откуда его берут и модель игрока,
 * и руки от первого лица, и голова в списке игроков. Поля ищем по типу, а не по имени,
 * поэтому подмена работает и в собранной игре без миксинов. Если что-то не нашлось,
 * подмена выключается и в лог пишется предупреждение: игра просто покажет обычные скины.
 */
public final class TeamSkins {
    private static final ResourceLocation ALPHA = Vhs.rl("textures/entity/skin/alpha.png");
    private static final ResourceLocation BRAVO = Vhs.rl("textures/entity/skin/bravo.png");
    private static final ResourceLocation NEUTRAL = Vhs.rl("textures/entity/skin/neutral.png");

    private static Field textures;
    private static Field pending;
    private static Field model;
    private static boolean ready;
    private static boolean failed;

    private TeamSkins() {}

    public static ResourceLocation skinFor(Scoreboard scoreboard, String name) {
        PlayerTeam team = scoreboard.getPlayersTeam(name);
        if (team != null) {
            if ("bh_alpha".equals(team.getName())) return ALPHA;
            if ("bh_bravo".equals(team.getName())) return BRAVO;
        }
        return NEUTRAL;
    }

    static void apply() {
        Minecraft mc = Minecraft.getInstance();
        if (failed || mc.level == null || mc.getConnection() == null || !BhConfig.FORCE_SKINS.get()) return;
        if (!ready && !prepare(mc)) return;
        Scoreboard scoreboard = mc.level.getScoreboard();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            ResourceLocation skin = skinFor(scoreboard, info.getProfile().getName());
            try {
                @SuppressWarnings("unchecked")
                Map<MinecraftProfileTexture.Type, ResourceLocation> map =
                        (Map<MinecraftProfileTexture.Type, ResourceLocation>) textures.get(info);
                // Своя загрузка скина у игры больше не запускается; если она уже шла, её результат мы перезапишем.
                pending.setBoolean(info, true);
                if (map.get(MinecraftProfileTexture.Type.SKIN) != skin) map.put(MinecraftProfileTexture.Type.SKIN, skin);
                model.set(info, "default");
            } catch (ReflectiveOperationException | ClassCastException | UnsupportedOperationException e) {
                failed = true;
                BodycamHorror.LOGGER.warn("[BH skins] could not replace player skins, falling back to normal skins", e);
                return;
            }
        }
    }

    /** Один раз: найти поля записи игрока и проверить, что текстуры формы на месте. */
    private static boolean prepare(Minecraft mc) {
        for (ResourceLocation r : new ResourceLocation[]{ALPHA, BRAVO, NEUTRAL}) {
            if (mc.getResourceManager().getResource(r).isEmpty()) {
                failed = true;
                BodycamHorror.LOGGER.warn("[BH skins] missing texture {}, team skins are off", r);
                return false;
            }
        }
        Field map = null;
        Field bool = null;
        Field str = null;
        int maps = 0;
        int bools = 0;
        int strs = 0;
        for (Field f : PlayerInfo.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (Map.class.isAssignableFrom(f.getType())) {
                map = f;
                maps++;
            } else if (f.getType() == boolean.class) {
                bool = f;
                bools++;
            } else if (f.getType() == String.class) {
                str = f;
                strs++;
            }
        }
        if (maps != 1 || bools != 1 || strs != 1) {
            failed = true;
            BodycamHorror.LOGGER.warn("[BH skins] unexpected PlayerInfo layout (maps {}, booleans {}, strings {}), team skins are off",
                    maps, bools, strs);
            return false;
        }
        try {
            map.setAccessible(true);
            bool.setAccessible(true);
            str.setAccessible(true);
        } catch (RuntimeException e) {
            failed = true;
            BodycamHorror.LOGGER.warn("[BH skins] no access to PlayerInfo fields, team skins are off", e);
            return false;
        }
        textures = map;
        pending = bool;
        model = str;
        ready = true;
        BodycamHorror.LOGGER.info("[BH skins] team skins are on");
        return true;
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class SkinForgeEvents {
        @SubscribeEvent
        public static void onSkinClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase == TickEvent.Phase.END) apply();
        }
    }
}
