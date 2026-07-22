package com.makar.tacticaltablet.tablet;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Compatibility registry for the existing tablet classes. The numeric action IDs and class keys in this
 * registry are persistent/network identities and must not be changed as part of UI work.
 */
public final class ClassDefinitions {
    public static final ResourceLocation FALLBACK_ICON = texture("classes/class_fallback.png");

    private static final List<ClassDefinition> DEFINITIONS = List.of(
            base("stormtrooper", "Штурмовик", 0, 0),
            base("sniper", "Снайпер", 1, 1),
            base("scout", "Разведчик", 2, 2),
            base("droneoperator", "Оператор дрона", 3, 3),
            base("machinegunner", "Пулемётчик", 8, 4),
            base("mortarman", "Миномётчик", 5, 5),
            base("rpgtrooper", "РПГ-боец", 9, 6),

            shop("boomguy", "Подрывник", 4, 500, 2, 0),
            shop("dream", "Дрим", 6, 500, 2, 1),
            shop("tagilla", "Тагилла", 10, 750, 2, 2),
            shop("blackops", "Спецназ", 11, 1000, 2, 3),
            shop("cowboy", "Ковбой", 12, 100, 1, 4),
            shop("solider", "Солдат", 13, 50, 0, 5),
            shop("rebel", "Повстанец", 14, 1000, 2, 6),
            shop("saboteur", "Диверсант", 15, 1000, 2, 7),

            exclusive("killer", "Киллер", 16, 2, 0),
            exclusive("miniboss", "Мини-Босс", 17, 2, 1),
            exclusive("shahed", "Шахед оп.", 18, 2, 2),
            exclusive("krot", "Крот", 19, 1, 3),
            exclusive("marine", "Морпех", 20, 2, 4),
            exclusive("medic", "Медик", 21, 2, 5),
            exclusive("microwave", "Микровэйв", 22, 2, 6),
            exclusive("railgunner", "Рэйл-ганнер", 23, 2, 7)
    );

    private static final Map<Integer, ClassDefinition> BY_ACTION_ID;
    private static final Map<String, ClassDefinition> BY_CLASS_KEY;

    static {
        Map<Integer, ClassDefinition> byActionId = new LinkedHashMap<>();
        Map<String, ClassDefinition> byClassKey = new LinkedHashMap<>();
        for (ClassDefinition definition : DEFINITIONS) {
            if (byActionId.put(definition.actionId(), definition) != null) {
                throw new IllegalStateException("Duplicate class actionId " + definition.actionId());
            }
            if (byClassKey.put(definition.classKey(), definition) != null) {
                throw new IllegalStateException("Duplicate classKey " + definition.classKey());
            }
        }
        BY_ACTION_ID = Map.copyOf(byActionId);
        BY_CLASS_KEY = Map.copyOf(byClassKey);
    }

    private ClassDefinitions() {
    }

    public static List<ClassDefinition> all() {
        return DEFINITIONS;
    }

    public static List<ClassDefinition> byCategory(ClassCategory category) {
        List<ClassDefinition> result = new ArrayList<>();
        for (ClassDefinition definition : DEFINITIONS) {
            if (definition.category() == category) result.add(definition);
        }
        result.sort(Comparator.comparingInt(ClassDefinition::displayOrder));
        return List.copyOf(result);
    }

    public static Optional<ClassDefinition> byActionId(int actionId) {
        return Optional.ofNullable(BY_ACTION_ID.get(actionId));
    }

    public static Optional<ClassDefinition> byClassKey(String classKey) {
        return Optional.ofNullable(BY_CLASS_KEY.get(classKey));
    }

    public static Map<Integer, String> actionIdToClassKey() {
        Map<Integer, String> result = new LinkedHashMap<>();
        for (ClassDefinition definition : DEFINITIONS) {
            result.put(definition.actionId(), definition.classKey());
        }
        return Map.copyOf(result);
    }

    private static ClassDefinition base(String classKey, String name, int actionId, int order) {
        return definition(classKey, name, ClassCategory.BASE, actionId, 0, -1, order);
    }

    private static ClassDefinition shop(String classKey, String name, int actionId, int price, int tier, int order) {
        return definition(classKey, name, ClassCategory.SHOP, actionId, price, tier, order);
    }

    private static ClassDefinition exclusive(String classKey, String name, int actionId, int tier, int order) {
        return definition(classKey, name, ClassCategory.EXCLUSIVE, actionId, 0, tier, order);
    }

    private static ClassDefinition definition(String classKey, String name, ClassCategory category,
                                              int actionId, int price, int tier, int order) {
        return new ClassDefinition(
                ResourceLocation.fromNamespaceAndPath("tacticaltablet", classKey),
                Component.literal(name),
                classKey,
                category,
                actionId,
                price,
                tier,
                texture("classes/" + classKey + ".png"),
                order
        );
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath("tacticaltablet", "textures/gui/" + path);
    }
}
