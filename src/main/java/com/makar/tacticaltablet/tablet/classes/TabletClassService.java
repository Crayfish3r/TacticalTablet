package com.makar.tacticaltablet.tablet.classes;

import com.makar.tacticaltablet.clan.ClanManager;
import com.makar.tacticaltablet.game.MapSetManager;
import com.makar.tacticaltablet.game.chaos.ChaosSetManager;
import com.makar.tacticaltablet.inventory.InventoryManager;
import com.makar.tacticaltablet.progression.ClassTier;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.progression.TabletProgressOperation.*;
import com.makar.tacticaltablet.tablet.*;
import com.makar.tacticaltablet.tablet.net.*;
import com.makar.tacticaltablet.tablet.preparation.TabletPreparationService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import java.util.*;

/** All calls, ticket observations and sends run on the server thread. No runtime objects enter storage work. */
public final class TabletClassService {
    private record Watch(Identity identity, ServerGamePacketListenerImpl connection, Status sent) { }
    private static final Map<UUID, Watch> watchers = new HashMap<>();
    private static long revision;
    private TabletClassService() { }

    public static void handle(ServerPlayer player, Identity identity) {
        if (player == null) return;
        if (!PacketHandler.allowC2S(player, identity.kind() == Kind.FETCH
                ? PacketHandler.C2SAction.TABLET_SNAPSHOT : PacketHandler.C2SAction.TABLET)) {
            send(player, identity, new Result(identity, Status.REJECTED, 0, "Слишком частые запросы. Повторите позже."));
            return;
        }
        String denial = "";
        if (!InventoryManager.hasTablet(player) || !player.isAlive()) denial = "Нужен планшет в инвентаре живого игрока.";
        ClassDefinition definition = ClassDefinitions.byClassKey(identity.classKey()).orElse(null);
        if (identity.kind() != Kind.FETCH && (definition == null
                || identity.kind() == Kind.PURCHASE_CLASS && !PlayerProgressManager.isShopClass(identity.classKey())
                || identity.kind() == Kind.UNLOCK_BASE && !PlayerProgressManager.isUnlockableBaseClass(identity.classKey())
                || identity.kind() == Kind.UPGRADE_TIER && !PlayerProgressManager.isBaseProgressionClass(identity.classKey())))
            denial = "Недопустимое действие для класса.";
        if (!denial.isEmpty()) { send(player, identity, new Result(identity, Status.REJECTED, 0, denial)); return; }
        if (identity.kind() == Kind.FETCH) { send(player, identity, null); return; }
        Result result = PlayerProgressManager.applyDurableTabletOperation(player, identity);
        if (result.status() == Status.ACCEPTED || result.status() == Status.SAVE_FAILED)
            watchers.put(player.getUUID(), new Watch(identity, player.connection, result.status()));
        send(player, identity, result);
    }

    public static void tick(MinecraftServer server) {
        for (var entry : new ArrayList<>(watchers.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Watch watch = entry.getValue();
            if (player == null || player.hasDisconnected() || player.connection != watch.connection()) {
                watchers.remove(entry.getKey()); continue;
            }
            Result result = PlayerProgressManager.tabletOperationResult(player, watch.identity());
            if (result == null || result.status() == watch.sent()) continue;
            send(player, watch.identity(), result);
            if (result.status() == Status.SAVED || result.status() == Status.REJECTED) {
                watchers.remove(entry.getKey());
                // Existing state maps are synchronized only after the entitlement's commit.
                com.makar.tacticaltablet.progression.ClassXPManager.sync(player);
            } else watchers.put(entry.getKey(), new Watch(watch.identity(), player.connection, result.status()));
        }
    }

    public static void reset() { watchers.clear(); }

    private static void send(ServerPlayer player, Identity identity, Result result) {
        if (result == null) result = PlayerProgressManager.latestTabletResult(player, identity.session());
        boolean chaos = MapSetManager.isChaosSet(), competitive = MapSetManager.isCompetitiveSet();
        boolean blocked = PlayerProgressManager.hasPendingTabletOperation(player);
        int balance = PlayerProgressManager.getConfirmedTabletCoins(player);
        var chaosState = ChaosSetManager.snapshot(player);
        List<TabletClassSnapshot.Row> rows = new ArrayList<>();
        for (ClassDefinition d : ClassDefinitions.all()) {
            String key = d.classKey(); boolean base = PlayerProgressManager.isBaseProgressionClass(key);
            var personal = PlayerProgressManager.getPersonalTabletClass(player, key);
            boolean owned = ClanManager.MARINE_CLASS.equals(key) ? ClanManager.isClanClassUnlocked(player, key)
                    : PlayerProgressManager.isExclusiveClass(key) ? PlayerProgressManager.isExclusiveClassGranted(player, key) : personal.owned();
            boolean allowed = chaos ? chaosState.offered().contains(key)
                    : competitive ? base : owned;
            int tier = chaos ? ChaosSetManager.tierFor(player, key) : competitive && base ? PlayerProgressManager.getLevel(player, key)
                    : base ? personal.tier() : d.fixedTier();
            ClassTier current = ClassTier.clamp(tier);
            // Effective mode tiers are never presented as personal progress or upgrade targets.
            ClassTier next = base && !chaos && !competitive && owned ? current.next().orElse(null) : null;
            float progress = next == null ? 0 : Math.max(0, Math.min(1,
                    (personal.xp() - current.requiredXp()) / (float) (next.requiredXp() - current.requiredXp())));
            String upgrade = chaos ? "Улучшения недоступны в Хаосе." : competitive ? "В этом режиме уровень задаёт номер игры."
                    : !owned ? "Сначала откройте класс." : next == null ? "Максимум"
                    : personal.xp() < next.requiredXp() ? "Накопите опыт"
                    : balance < next.upgradeCost() ? "Нужно " + next.upgradeCost() + " монет" : "";
            if (blocked) upgrade = "Ожидаем сохранения операции.";
            Kind purchase = PlayerProgressManager.isUnlockableBaseClass(key) ? Kind.UNLOCK_BASE
                    : PlayerProgressManager.isShopClass(key) ? Kind.PURCHASE_CLASS : Kind.FETCH;
            int price = purchase == Kind.UNLOCK_BASE ? PlayerProgressManager.BASE_UNLOCK_COST
                    : purchase == Kind.PURCHASE_CLASS ? PlayerProgressManager.getShopPrice(key) : 0;
            String purchaseReason = owned ? (base ? "Открыт" : "Куплено")
                    : chaos ? "Покупки недоступны в Хаосе." : competitive ? "Покупки недоступны в соревновательном режиме."
                    : blocked ? "Ожидаем сохранения операции." : balance < price ? "Недостаточно монет" : "";
            rows.add(new TabletClassSnapshot.Row(key, d.actionId(), owned, allowed,
                    TabletPreparationService.classReason(player, d), current.id(), next == null ? -1 : next.id(), progress,
                    upgrade, next == null ? 0 : next.upgradeCost(), purchase, price, purchaseReason, blocked));
        }
        PacketHandler.sendToPlayer(player, new TabletClassesStatePacket(new TabletClassSnapshot(identity.session(), identity.requestId(),
                ++revision, balance, result, PlayerProgressManager.pendingTabletOperation(player), rows)));
    }
}
