package com.makar.tacticaltablet.game.lobby;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

final class LobbyBootstrapSavedData extends SavedData {
    static final String DATA_NAME = "tacticaltablet_lobby_bootstrap";
    private static final String VERSION_TAG = "BootstrapVersion";

    private int version;

    LobbyBootstrapSavedData() {
    }

    static LobbyBootstrapSavedData load(CompoundTag tag) {
        LobbyBootstrapSavedData data = new LobbyBootstrapSavedData();
        data.version = Math.max(0, tag.getInt(VERSION_TAG));
        return data;
    }

    int version() {
        return version;
    }

    void markVersion(int newVersion) {
        if (newVersion <= version) return;
        version = newVersion;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt(VERSION_TAG, version);
        return tag;
    }
}
