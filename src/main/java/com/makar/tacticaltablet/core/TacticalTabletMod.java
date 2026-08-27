package com.makar.tacticaltablet.core;

import com.makar.tacticaltablet.airdrop.AirdropCommands;
import com.makar.tacticaltablet.airdrop.AirdropEvents;
import com.makar.tacticaltablet.command.CoinCommand;
import com.makar.tacticaltablet.command.CorpseTestCommand;
import com.makar.tacticaltablet.command.DebugXPCommand;
import com.makar.tacticaltablet.command.ExtractionPointCommand;
import com.makar.tacticaltablet.command.GiveClassCommand;
import com.makar.tacticaltablet.command.IntegrationCheckCommand;
import com.makar.tacticaltablet.command.LobbyCommand;
import com.makar.tacticaltablet.command.KillsDiscordCommand;
import com.makar.tacticaltablet.command.MapRotationCommand;
import com.makar.tacticaltablet.command.OnlineWebhookCommand;
import com.makar.tacticaltablet.command.ResetCommand;
import com.makar.tacticaltablet.command.RespawnControlCommand;
import com.makar.tacticaltablet.command.RtpCommand;
import com.makar.tacticaltablet.command.SadTromboneCommand;
import com.makar.tacticaltablet.command.TestModeCommand;
import com.makar.tacticaltablet.command.XpBoostCommand;
import com.makar.tacticaltablet.game.ServerEvents;
import com.makar.tacticaltablet.integration.moderndamage.ModernDamageIntegration;
import com.makar.tacticaltablet.moderation.ModerationCommand;
import com.makar.tacticaltablet.prefix.PrefixCommand;
import com.makar.tacticaltablet.tablet.net.PacketHandler;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(TacticalTabletMod.MODID)
public class TacticalTabletMod {
    public static final String MODID = "tacticaltablet";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TacticalTabletMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        ModParticles.PARTICLES.register(modEventBus);

        ModLoadingContext.get().registerConfig(
                ModConfig.Type.SERVER,
                TacticalTabletServerConfig.SPEC
        );
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.CLIENT,
                TacticalTabletClientConfig.SPEC
        );

        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::onConfigLoading);

        MinecraftForge.EVENT_BUS.register(ServerEvents.class);
        MinecraftForge.EVENT_BUS.register(AirdropEvents.class);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PacketHandler.register();
            ModernDamageIntegration.initialize();
        });
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.TACTICAL_TABLET);
            event.accept(ModItems.CONTRACT_TRACKER);
        }
    }

    private void onConfigLoading(ModConfigEvent.Loading event) {
        ModConfig config = event.getConfig();
        if (config.getSpec() != TacticalTabletServerConfig.SPEC) return;
        if (TacticalTabletServerConfig.migrateKillFeedDefault()) {
            config.save();
            LOGGER.info("Enabled the primary Tactical kill feed while migrating the legacy server config.");
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ResetCommand.register(event.getDispatcher());
        DebugXPCommand.register(event.getDispatcher());
        CoinCommand.register(event.getDispatcher());
        TestModeCommand.register(event.getDispatcher());
        MapRotationCommand.register(event.getDispatcher());
        RespawnControlCommand.register(event.getDispatcher());
        RtpCommand.register(event.getDispatcher());
        KillsDiscordCommand.register(event.getDispatcher());
        OnlineWebhookCommand.register(event.getDispatcher());
        AirdropCommands.register(event.getDispatcher());
        ExtractionPointCommand.register(event.getDispatcher());
        CorpseTestCommand.register(event.getDispatcher());
        IntegrationCheckCommand.register(event.getDispatcher());
        LobbyCommand.register(event.getDispatcher());
        GiveClassCommand.register(event.getDispatcher());
        XpBoostCommand.register(event.getDispatcher());
        SadTromboneCommand.register(event.getDispatcher());
        PrefixCommand.register(event.getDispatcher());
        ModerationCommand.register(event.getDispatcher());
    }
}
