package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;

/**
 * Встроенный ресурспак мода поверх всех остальных (папка overrides в jar): переводы и текстуры для других модов.
 * Ресурсы мода в общем пакете Mod Resources не перекрывают ресурсы TACZ и голосового чата,
 * поэтому серая подсказка TACZ, тёмные панели голосового чата и пустые строки «новая версия» Bleeding Mod
 * лежат в отдельном паке на самом верху. Пак обязательный: включён всегда, отключить его в списке нельзя.
 */
@Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class OverridesPack {
    private OverridesPack() {}

    @SubscribeEvent
    public static void onBhAddOverridesPack(AddPackFindersEvent e) {
        if (e.getPackType() != PackType.CLIENT_RESOURCES) return;
        Path path = ModList.get().getModFileById(BodycamHorror.MOD_ID).getFile().findResource("overrides");
        Pack pack = Pack.readMetaAndCreate("bodycamhorror_overrides", Component.literal("Bodycam Horror overrides"), true,
                id -> new PathPackResources(id, path, true), PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);
        if (pack == null) {
            BodycamHorror.LOGGER.warn("[BH ui] overrides pack not found at {}", path);
            return;
        }
        e.addRepositorySource(consumer -> consumer.accept(pack));
        BodycamHorror.LOGGER.info("[BH ui] overrides pack added on top");
    }
}
