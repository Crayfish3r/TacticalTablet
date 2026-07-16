package com.makar.tacticaltablet.progression;

import com.makar.tacticaltablet.clan.transaction.RepositoryResult;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionCharacterizationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void levelCalculationUsesCurrentThresholdsAndClampsExperience() {
        assertEquals(ClassTier.BASIC.id(), PlayerProgressManager.getLevelForXP(-1));
        assertEquals(ClassTier.BASIC.id(), PlayerProgressManager.getLevelForXP(299));
        assertEquals(ClassTier.RARE.id(), PlayerProgressManager.getLevelForXP(300));
        assertEquals(ClassTier.EPIC.id(), PlayerProgressManager.getLevelForXP(800));
        assertEquals(ClassTier.LEGEND.id(), PlayerProgressManager.getLevelForXP(1300));
        assertEquals(ClassTier.MONSTER.id(), PlayerProgressManager.getLevelForXP(2000));
        assertEquals(ClassTier.MONSTER.id(), PlayerProgressManager.getLevelForXP(Integer.MAX_VALUE));
    }

    @Test
    void migrationClampsInvalidValuesWithoutInferringTierFromExperience() {
        assertEquals(new PlayerProgressManager.PersistedClassProgress(11, ClassTier.BASIC.id(), 0),
                PlayerProgressManager.migrateClassProgress(1, -10, -100));
        assertEquals(new PlayerProgressManager.PersistedClassProgress(11, ClassTier.BASIC.id(), 1300),
                PlayerProgressManager.migrateClassProgress(10, ClassTier.BASIC.id(), 1300));
        assertEquals(new PlayerProgressManager.PersistedClassProgress(11, ClassTier.MONSTER.id(), 2000),
                PlayerProgressManager.migrateClassProgress(10, 99, Integer.MAX_VALUE));
    }

    @Test
    void shopCatalogCharacterizesPricesFixedTiersAndUnknownIdentifiers() {
        assertEquals(500, PlayerProgressManager.getShopPrice("boomguy"));
        assertEquals(ClassTier.LEGEND.id(), PlayerProgressManager.getShopFixedLevel("boomguy"));
        assertEquals(0, PlayerProgressManager.getShopPrice("unknown"));
        assertEquals(ClassTier.BASIC.id(), PlayerProgressManager.getShopFixedLevel("unknown"));
    }

    @Test
    void sameCreditReceiptIsAppliedOnceAndDifferentReceiptsBothApply() {
        TestState state = new TestState(10);

        assertEquals(RepositoryResult.Status.APPLIED, applyCredit(state, "reward-1", 5).status());
        assertEquals(15, state.coins());
        assertEquals(RepositoryResult.Status.ALREADY_APPLIED, applyCredit(state, "reward-1", 5).status());
        assertEquals(15, state.coins());
        assertEquals(RepositoryResult.Status.APPLIED, applyCredit(state, "reward-2", 5).status());
        assertEquals(20, state.coins());
        assertEquals(2, state.receipts().size());
    }

    @Test
    void invalidAndOverflowingCreditsDoNotChangeBalance() {
        TestState state = new TestState(Integer.MAX_VALUE - 1);

        assertEquals(RepositoryResult.Status.FAILED, applyCredit(state, "zero", 0).status());
        assertEquals(RepositoryResult.Status.FAILED, applyCredit(state, "overflow", 2).status());
        assertEquals(Integer.MAX_VALUE - 1, state.coins());
        assertTrue(state.receipts().isEmpty());
    }

    @Test
    void nullAndDuplicateReceiptCollectionsNormalizeToEmpty() {
        assertTrue(PlayerTransactionReceiptLedger.normalizeReceipts(null).isEmpty());

        AppliedTransactionReceipt duplicate = receipt("duplicate");
        assertTrue(PlayerTransactionReceiptLedger.normalizeReceipts(List.of(duplicate, duplicate)).isEmpty());
    }

    @Test
    void publicPurchaseAndCreditBoundariesRemainSynchronized() throws Exception {
        Method purchase = PlayerProgressManager.class.getDeclaredMethod(
                "purchaseClass", ServerPlayer.class, String.class
        );
        Method credit = PlayerProgressManager.class.getDeclaredMethod(
                "applyIdempotentCoinCredit",
                MinecraftServer.class, UUID.class, String.class, int.class, String.class
        );

        assertTrue(Modifier.isSynchronized(purchase.getModifiers()));
        assertTrue(Modifier.isSynchronized(credit.getModifiers()));
    }

    @Test
    void legacyProfileNormalizationKeepsValidValuesAndRepairsMissingOrInvalidFields() throws Exception {
        Class<?> progressType = Arrays.stream(PlayerProgressManager.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("PlayerProgress"))
                .findFirst()
                .orElseThrow();
        Constructor<?> constructor = progressType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object progress = constructor.newInstance();

        Map<String, Integer> classes = new LinkedHashMap<>();
        classes.put(" Scout ", 100);
        classes.put("scout", 200);
        classes.put("negative", -5);
        Map<String, Integer> unlocks = new LinkedHashMap<>();
        unlocks.put(" DroneOperator ", 1);
        unlocks.put("droneoperator", 0);

        setField(progressType, progress, "dataVersion", 10);
        setField(progressType, progress, "classes", classes);
        setField(progressType, progress, "classTiers", null);
        setField(progressType, progress, "unlockedBaseClasses", unlocks);
        setField(progressType, progress, "purchasedClasses", null);
        setField(progressType, progress, "donations", null);
        setField(progressType, progress, "stats", null);
        setField(progressType, progress, "appliedTransactionReceipts", null);
        setField(progressType, progress, "coins", -20);
        setField(progressType, progress, "wins", 7);
        setField(progressType, progress, "battlePassXp", -2);

        Method normalize = PlayerProgressManager.class.getDeclaredMethod("normalize", progressType);
        normalize.setAccessible(true);
        normalize.invoke(null, progress);

        assertEquals(11, getField(progressType, progress, "dataVersion"));
        assertEquals(0, getField(progressType, progress, "coins"));
        assertEquals(7, getField(progressType, progress, "wins"));
        assertEquals(0, getField(progressType, progress, "battlePassXp"));
        assertEquals(200, integerMap(progressType, progress, "classes").get("scout"));
        assertEquals(0, integerMap(progressType, progress, "classes").get("negative"));
        assertEquals(0, integerMap(progressType, progress, "unlockedBaseClasses").get("droneoperator"));
        assertTrue(integerMap(progressType, progress, "classTiers").containsKey("scout"));
        assertTrue(integerMap(progressType, progress, "purchasedClasses").containsKey("boomguy"));
        assertTrue(integerMap(progressType, progress, "donations").isEmpty());
        assertTrue(integerMap(progressType, progress, "stats").isEmpty());
    }

    private static RepositoryResult applyCredit(TestState state, String receiptId, int amount) {
        return PlayerTransactionReceiptLedger.applyCredit(
                state, receiptId, "characterization", amount, CLOCK, () -> true
        );
    }

    private static AppliedTransactionReceipt receipt(String id) {
        AppliedTransactionReceipt receipt = new AppliedTransactionReceipt();
        receipt.transactionId = id;
        receipt.operationType = "characterization";
        receipt.appliedAt = CLOCK.millis();
        receipt.expectedOldBalance = 10;
        receipt.newBalance = 15;
        receipt.payloadHash = "5";
        return receipt;
    }

    private static void setField(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Class<?> type, Object target, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> integerMap(Class<?> type, Object target, String name) throws Exception {
        return (Map<String, Integer>) getField(type, target, name);
    }

    private static final class TestState implements PlayerTransactionReceiptLedger.State {
        private int coins;
        private List<AppliedTransactionReceipt> receipts = new ArrayList<>();

        private TestState(int coins) {
            this.coins = coins;
        }

        @Override
        public int coins() {
            return coins;
        }

        @Override
        public void coins(int value) {
            coins = value;
        }

        @Override
        public List<AppliedTransactionReceipt> receipts() {
            return receipts;
        }

        @Override
        public void receipts(List<AppliedTransactionReceipt> value) {
            receipts = value == null ? new ArrayList<>() : value;
        }
    }
}
