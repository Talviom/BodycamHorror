package com.talviom.bodycamhorror;

import com.talviom.bodycamhorror.flashlight.FlashlightItem;
import com.talviom.bodycamhorror.sabotage.LampBlock;
import com.talviom.bodycamhorror.sabotage.PowerSwitchBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, BodycamHorror.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BodycamHorror.MOD_ID);

    public static final RegistryObject<Block> LAMP = BLOCKS.register("lamp", () -> new LampBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(1.5f).sound(SoundType.GLASS).noLootTable().noOcclusion()
                    .lightLevel(s -> s.getValue(LampBlock.LIT) ? 15 : 0)));
    public static final RegistryObject<Block> BREAKER = BLOCKS.register("breaker", () -> new PowerSwitchBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).sound(SoundType.METAL).noLootTable().noOcclusion(), false));
    public static final RegistryObject<Block> GENERATOR = BLOCKS.register("generator", () -> new PowerSwitchBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).sound(SoundType.METAL).noLootTable().noOcclusion(), true));

    /** 0.9: аварийная лампа, тускло-красная при выключенном генераторе, неразбиваемая (см. EmergencyLampBlock). */
    public static final RegistryObject<Block> EMERGENCY_LAMP = BLOCKS.register("emergency_lamp", () -> new com.talviom.bodycamhorror.sabotage.EmergencyLampBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(-1f, 3600000f).sound(SoundType.METAL).noLootTable().noOcclusion()
                    .lightLevel(s -> s.getValue(com.talviom.bodycamhorror.sabotage.EmergencyLampBlock.LIT) ? 5 : 0)));

    /** Невидимый свет фонарика (убирает себя сам, см. BeamLightBlock). */
    public static final RegistryObject<Block> BEAM_LIGHT = BLOCKS.register("beam_light", () -> new com.talviom.bodycamhorror.flashlight.BeamLightBlock(
            BlockBehaviour.Properties.of().replaceable().noCollission().noLootTable().noOcclusion()
                    .strength(-1f, 3600000f).lightLevel(s -> s.getValue(com.talviom.bodycamhorror.flashlight.BeamLightBlock.LEVEL))));

    /** Знак выхода на полу: сюда команда выносит кассету. */
    public static final RegistryObject<Block> EXIT_MARKER = BLOCKS.register("exit_marker", () -> new com.talviom.bodycamhorror.objective.ExitMarkerBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(-1f, 3600000f).sound(SoundType.METAL).noLootTable().noOcclusion()
                    .lightLevel(s -> 3)));

    public static final RegistryObject<Item> LAMP_ITEM = ITEMS.register("lamp", () -> new BlockItem(LAMP.get(), new Item.Properties()));
    public static final RegistryObject<Item> BREAKER_ITEM = ITEMS.register("breaker", () -> new BlockItem(BREAKER.get(), new Item.Properties()));
    public static final RegistryObject<Item> GENERATOR_ITEM = ITEMS.register("generator", () -> new BlockItem(GENERATOR.get(), new Item.Properties()));
    public static final RegistryObject<Item> EMERGENCY_LAMP_ITEM = ITEMS.register("emergency_lamp", () -> new BlockItem(EMERGENCY_LAMP.get(), new Item.Properties()));

    public static final RegistryObject<Item> FLASHLIGHT = ITEMS.register("flashlight", () -> new FlashlightItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> EXIT_MARKER_ITEM = ITEMS.register("exit_marker", () -> new BlockItem(EXIT_MARKER.get(), new Item.Properties()));
    /** Кассета, цель раунда. Не горит в лаве и не исчезает на земле во время матча (см. TapeObjective). */
    public static final RegistryObject<Item> TAPE = ITEMS.register("tape", () -> new Item(new Item.Properties().stacksTo(1).fireResistant()));
    /** 0.9: кассета, лежащая в мире 3D-моделью (на мебели или на полу). Забрать только подбором, сломать нельзя. */
    public static final RegistryObject<Block> TAPE_BLOCK = BLOCKS.register("tape_block", () -> new com.talviom.bodycamhorror.objective.TapeBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1f, 3600000f).sound(SoundType.WOOD)
                    .noLootTable().noOcclusion().noCollission().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

    private ModBlocks() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        com.talviom.bodycamhorror.gear.ModGear.register(modBus);
        modBus.addListener(ModBlocks::addToTabs);
    }

    private static void addToTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            e.accept(LAMP_ITEM);
            e.accept(BREAKER_ITEM);
            e.accept(GENERATOR_ITEM);
            e.accept(EMERGENCY_LAMP_ITEM);
            e.accept(EXIT_MARKER_ITEM);
        }
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            e.accept(FLASHLIGHT);
            e.accept(TAPE);
        }
    }
}
