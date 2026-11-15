package com.talviom.bodycamhorror;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Свои звуки мода. Файлы лежат в assets/bodycamhorror/sounds, список в sounds.json. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, BodycamHorror.MOD_ID);

    public static final RegistryObject<SoundEvent> WHISPER = register("whisper");
    public static final RegistryObject<SoundEvent> RADIO_STATIC = register("radio_static");
    public static final RegistryObject<SoundEvent> BREAKER_OFF = register("breaker_off");
    public static final RegistryObject<SoundEvent> BREAKER_ON = register("breaker_on");
    public static final RegistryObject<SoundEvent> GENERATOR_DOWN = register("generator_down");
    public static final RegistryObject<SoundEvent> GENERATOR_UP = register("generator_up");
    public static final RegistryObject<SoundEvent> HEARTBEAT = register("heartbeat");
    public static final RegistryObject<SoundEvent> KNOCK = register("knock");
    public static final RegistryObject<SoundEvent> LAMP_BUZZ = register("lamp_buzz");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(BodycamHorror.MOD_ID, name)));
    }
}
