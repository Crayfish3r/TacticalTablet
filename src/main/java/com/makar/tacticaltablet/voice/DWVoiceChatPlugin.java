package com.makar.tacticaltablet.voice;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.NameTagIconRenderEvent;
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;

@ForgeVoicechatPlugin
public final class DWVoiceChatPlugin implements VoicechatPlugin {

    private static final String PLUGIN_ID = "dw_voicechat";

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, VoiceChatTeamManager::onVoicechatServerStarted);
        registration.registerEvent(PlayerConnectedEvent.class, VoiceChatTeamManager::onPlayerConnected);
        registration.registerEvent(NameTagIconRenderEvent.class, event -> {
            event.cancel();
        });
    }
}
