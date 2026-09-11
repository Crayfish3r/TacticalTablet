package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.inventory.InventoryManager;
import com.makar.tacticaltablet.progression.CosmeticCatalog;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.game.lobby.LobbyManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-authoritative cosmetic purchase request. The client supplies no price. */
public final class CosmeticPurchasePacket {
    private static final int MAX_PRODUCT_ID_LENGTH = 64;
    private final String productId;

    public CosmeticPurchasePacket(String productId) {
        this.productId = productId == null ? "" : productId;
    }

    public CosmeticPurchasePacket(FriendlyByteBuf buffer) {
        this.productId = buffer.readUtf(MAX_PRODUCT_ID_LENGTH);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(productId, MAX_PRODUCT_ID_LENGTH);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (!PacketHandler.allowC2S(player, PacketHandler.C2SAction.TABLET)
                    || !InventoryManager.hasTablet(player)) {
                LobbyManager.sync(player);
                return;
            }
            PlayerProgressManager.applyTabletCosmeticPurchase(player, productId, result -> {
                switch (result) {
                    case PURCHASED -> player.sendSystemMessage(Component.translatable(
                            "message.tacticaltablet.cosmetic.ghillie_suit.purchased",
                            CosmeticCatalog.GHILLIE_SUIT_PRICE));
                    case ALREADY_OWNED -> player.sendSystemMessage(Component.translatable(
                            "message.tacticaltablet.cosmetic.ghillie_suit.already_owned"));
                    case NOT_ENOUGH_COINS -> player.sendSystemMessage(Component.translatable(
                            "message.tacticaltablet.cosmetic.ghillie_suit.not_enough",
                            CosmeticCatalog.GHILLIE_SUIT_PRICE));
                    case NOT_PURCHASABLE -> player.sendSystemMessage(Component.translatable(
                            "message.tacticaltablet.cosmetic.invalid"));
                }
            });
        });
        context.setPacketHandled(true);
    }
}
