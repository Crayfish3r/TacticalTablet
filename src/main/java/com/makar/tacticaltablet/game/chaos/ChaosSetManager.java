package com.makar.tacticaltablet.game.chaos;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.MapSetManager;
import com.makar.tacticaltablet.tablet.ClassDefinitions;
import com.makar.tacticaltablet.progression.ClassTier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ChaosSetManager {
    public static final int KILL_COINS = 8;
    private static UUID setId;
    private static ChaosDeck deck;
    private static int gameNumber;
    private static List<ChaosClassCard> currentCards = List.of();
    private static List<String> currentClasses = List.of();
    private static final Map<UUID, ChaosPlayerClasses> players = new LinkedHashMap<>();

    private ChaosSetManager() { }

    public static synchronized boolean prepareSet(MinecraftServer server) {
        if (!MapSetManager.isChaosSet()) { clear(); return true; }
        UUID currentSetId = MapSetManager.getSetId();
        if (deck != null && currentSetId.equals(setId)) return true;
        List<String> pool = buildClassPool();
        int uniqueRegisteredClasses = (int) pool.stream()
                .map(ChaosClassCard::identity).distinct().count();
        TacticalTabletMod.LOGGER.info("Chaos registry scan found {} class cards across {} unique classes",
                pool.size(), uniqueRegisteredClasses);
        if (uniqueRegisteredClasses < MapSetManager.GAMES_PER_MAP * ChaosDeck.CLASSES_PER_GAME) {
            TacticalTabletMod.LOGGER.error("Cannot start Chaos: registry contains only {} unique classes",
                    uniqueRegisteredClasses);
            MapSetManager.fallbackChaosToCasual(server,
                    "в серверном реестре меньше 15 уникальных классов (найдено "
                            + uniqueRegisteredClasses + ")");
            clear();
            return false;
        }

        try {
            ChaosDeck prepared = restorePersistedDeck(currentSetId);
            if (prepared == null) prepared = createFreshDeck(pool, currentSetId);
            deck = prepared;
            if (!MapSetManager.getChaosDeck().equals(deck.cards())
                    && !MapSetManager.saveChaosDeck(server, deck.cards())) {
                TacticalTabletMod.LOGGER.error(
                        "Chaos deck could not be persisted; continuing with deterministic deck for set {}",
                        currentSetId);
            }
            setId = currentSetId;
            TacticalTabletMod.LOGGER.info("Chaos set {} prepared from {} registered classes; deck={}",
                    setId, uniqueRegisteredClasses, deck.cards());
            return true;
        } catch (RuntimeException exception) {
            TacticalTabletMod.LOGGER.error("Cannot start Chaos set because of a technical error", exception);
            MapSetManager.fallbackChaosToCasual(server,
                    "техническая ошибка подготовки (подробности в latest.log)");
            clear();
            return false;
        }
    }

    static List<String> buildClassPool() {
        List<String> pool = new ArrayList<>();
        ClassDefinitions.all().forEach(definition -> {
            if (definition.fixedTier() >= ClassTier.BASIC.id()) {
                pool.add(new ChaosClassCard(definition.classKey(), definition.fixedTier()).encode());
            } else {
                for (ClassTier tier : ClassTier.values()) {
                    pool.add(new ChaosClassCard(definition.classKey(), tier.id()).encode());
                }
            }
        });
        return List.copyOf(pool);
    }

    private static ChaosDeck restorePersistedDeck(UUID currentSetId) {
        List<String> persisted = MapSetManager.getChaosDeck();
        if (persisted.isEmpty()) return null;
        try {
            Set<String> registeredClasses = ClassDefinitions.all().stream()
                    .map(definition -> definition.classKey()).collect(Collectors.toSet());
            if (persisted.stream().map(ChaosClassCard::identity).anyMatch(id -> !registeredClasses.contains(id))) {
                throw new IllegalArgumentException("deck references classes absent from the registry");
            }
            if (persisted.stream().noneMatch(value -> value.contains("@"))) {
                persisted = migrateLegacyDeck(persisted, currentSetId);
            }
            return ChaosDeck.restore(persisted, MapSetManager.GAMES_PER_MAP);
        } catch (RuntimeException exception) {
            TacticalTabletMod.LOGGER.warn(
                    "Ignoring invalid persisted Chaos deck for set {}; rebuilding it from the registry",
                    currentSetId, exception);
            return null;
        }
    }

    private static ChaosDeck createFreshDeck(List<String> pool, UUID currentSetId) {
        long seed = currentSetId.getMostSignificantBits() ^ currentSetId.getLeastSignificantBits();
        return new ChaosDeck(pool, MapSetManager.GAMES_PER_MAP, new Random(seed));
    }

    public static synchronized boolean beginGame(MinecraftServer server, int oneBasedGame) {
        if (!MapSetManager.isChaosSet()) { clearGame(); return true; }
        if (!prepareSet(server) || deck == null) return false;
        List<String> next = deck.gameClasses(oneBasedGame);
        if (next.size() != ChaosDeck.CLASSES_PER_GAME) {
            TacticalTabletMod.LOGGER.error("Chaos game {} has no complete class triplet; falling back to Casual", oneBasedGame);
            MapSetManager.fallbackChaosToCasual(server, "не удалось сформировать тройку классов для игры " + oneBasedGame);
            clear();
            return false;
        }
        gameNumber = oneBasedGame;
        currentCards = next.stream().map(ChaosClassCard::decode).toList();
        currentClasses = currentCards.stream().map(ChaosClassCard::classId).toList();
        players.clear();
        TacticalTabletMod.LOGGER.info("Chaos game {} class cards: {}", gameNumber, currentCards);
        return true;
    }

    public static synchronized boolean select(ServerPlayer player, String classId) {
        if (player == null || !isActive()) return false;
        return state(player.getUUID()).select(classId);
    }

    public static synchronized void onDeath(ServerPlayer player) {
        if (player != null && isActive()) state(player.getUUID()).consumeSelected();
    }

    public static synchronized boolean canUse(ServerPlayer player, String classId) {
        return player != null && isActive() && state(player.getUUID()).isAvailable(classId);
    }

    public static synchronized int tierFor(ServerPlayer player, String classId) {
        if (player == null || classId == null || !isActive()) return ClassTier.BASIC.id();
        return currentCards.stream().filter(card -> card.classId().equals(classId))
                .mapToInt(ChaosClassCard::tier).findFirst().orElse(ClassTier.BASIC.id());
    }

    public static synchronized boolean requiresSelection(ServerPlayer player) {
        return player != null && isActive() && state(player.getUUID()).requiresSelection();
    }

    public static synchronized Snapshot snapshot(ServerPlayer player) {
        if (player == null || !isActive()) return Snapshot.inactive();
        ChaosPlayerClasses state = state(player.getUUID());
        Map<String, Integer> tiers = currentCards.stream().collect(Collectors.toMap(
                ChaosClassCard::classId, ChaosClassCard::tier, (left, right) -> left, LinkedHashMap::new));
        return new Snapshot(true, gameNumber, currentClasses, tiers, state.spent(), state.selected(), state.requiresSelection());
    }

    public static synchronized List<List<String>> usedGameClasses() {
        if (deck == null) return List.of();
        List<List<String>> result = new ArrayList<>();
        for (int game = 1; game <= Math.max(gameNumber, MapSetManager.getCompletedGames()); game++) {
            List<String> classes = deck.gameClasses(game).stream()
                    .map(ChaosClassCard::decode).map(ChaosClassCard::classId).toList();
            if (!classes.isEmpty()) result.add(List.copyOf(classes));
        }
        return List.copyOf(result);
    }

    public static synchronized boolean isActive() { return MapSetManager.isChaosSet() && deck != null && !currentClasses.isEmpty(); }
    public static synchronized void finishGame() { players.clear(); currentCards = List.of(); currentClasses = List.of(); }
    public static synchronized void clearGame() { finishGame(); }
    public static synchronized void clear() { finishGame(); deck = null; setId = null; }

    private static ChaosPlayerClasses state(UUID playerId) {
        return players.computeIfAbsent(playerId, ignored -> new ChaosPlayerClasses(currentClasses));
    }

    private static List<String> migrateLegacyDeck(List<String> legacy, UUID currentSetId) {
        long seed = currentSetId.getMostSignificantBits() ^ currentSetId.getLeastSignificantBits();
        Random random = new Random(seed);
        List<String> result = new ArrayList<>();
        for (String classId : legacy) {
            int fixedTier = ClassDefinitions.byClassKey(classId).orElseThrow().fixedTier();
            int tier = fixedTier >= ClassTier.BASIC.id()
                    ? fixedTier : ClassTier.values()[random.nextInt(ClassTier.values().length)].id();
            result.add(new ChaosClassCard(classId, tier).encode());
        }
        return List.copyOf(result);
    }

    public record Snapshot(boolean active, int gameNumber, List<String> offered, Map<String, Integer> tiers, Set<String> spent,
                           String selected, boolean requiresSelection) {
        public static Snapshot inactive() { return new Snapshot(false, 0, List.of(), Map.of(), Set.of(), "", false); }
    }
}
