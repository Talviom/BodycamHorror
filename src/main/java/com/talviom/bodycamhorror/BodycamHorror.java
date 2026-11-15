package com.talviom.bodycamhorror;

import com.talviom.bodycamhorror.command.BhCommand;
import com.talviom.bodycamhorror.match.MatchEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(BodycamHorror.MOD_ID)
public class BodycamHorror {
    public static final String MOD_ID = "bodycamhorror";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BodycamHorror() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BhConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, BhConfig.CLIENT_SPEC);

        MinecraftForge.EVENT_BUS.register(new MatchEvents());
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> BhCommand.register(e.getDispatcher()));
    }
}
