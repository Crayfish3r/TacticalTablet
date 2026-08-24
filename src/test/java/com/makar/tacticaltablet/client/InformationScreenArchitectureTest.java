package com.makar.tacticaltablet.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InformationScreenArchitectureTest {

    private static final Path GUI =
            Path.of("src/main/java/com/makar/tacticaltablet/client/gui");

    @Test
    void informationScreenProvidesThreeScrollableTacticalSectionsAndCustomParentReturn()
            throws IOException {
        String screen = read(GUI.resolve("GuideScreen.java"));

        assertTrue(screen.contains("InformationContent.Section.values()"));
        assertTrue(screen.contains("TacticalButton.compact("));
        assertTrue(screen.contains("TacticalUi.withScissor("));
        assertTrue(screen.contains("public boolean mouseScrolled("));
        assertTrue(screen.contains("GLFW.GLFW_KEY_PAGE_DOWN"));
        assertTrue(screen.contains("Minecraft.getInstance().setScreen(parent)"));
        assertTrue(screen.contains("MenuTextureSet.BACKGROUND"));
    }

    @Test
    void authoredContentCoversServerModesRulesEconomyXpAndClassCatalogs() throws IOException {
        String content = read(GUI.resolve("InformationContent.java"));

        assertTrue(content.contains("case SERVER -> serverAndModes()"));
        assertTrue(content.contains("case RULES -> rules()"));
        assertTrue(content.contains("case ECONOMY_AND_CLASSES -> economyAndClasses()"));
        assertTrue(content.contains("Battle Royale"));
        assertTrue(content.contains("не менее двух игроков"));
        assertTrue(content.contains("пяти раундов"));
        assertTrue(content.contains("#статистика"));
        assertTrue(content.contains("Казуал"));
        assertTrue(content.contains("Хаос"));
        assertTrue(content.contains("Соревновательный"));
        assertTrue(content.contains("Правило «Анти-крот»"));
        assertTrue(IntStream.rangeClosed(1, 12)
                .allMatch(number -> content.contains("heading(\"" + number + ".")));
        assertTrue(content.contains("5 coins в Казуале и 8 coins в Хаосе"));
        assertTrue(content.contains("каждые две минуты"));
        assertTrue(content.contains("Победа в раунде: 10 XP"));
        assertTrue(content.contains("Создание собственного клана — 1000 личных coins"));
        assertTrue(content.contains("ClassTier.values()"));
        assertTrue(content.contains("ClassDefinitions.byCategory(ClassCategory.SHOP)"));
        assertTrue(content.contains("ShopClassCatalog.byClassKey"));
    }

    @Test
    void informationNavigationIsLocalizedAsInformation() throws IOException {
        String russian = read(Path.of(
                "src/main/resources/assets/tacticaltablet/lang/ru_ru.json"));
        String english = read(Path.of(
                "src/main/resources/assets/tacticaltablet/lang/en_us.json"));

        assertTrue(russian.contains("\"screen.tacticaltablet.main_menu.guide\": \"Информация\""));
        assertTrue(russian.contains("\"screen.tacticaltablet.information.server\": \"Сервер и режимы\""));
        assertTrue(russian.contains("\"screen.tacticaltablet.information.rules\": \"Правила\""));
        assertTrue(russian.contains("\"screen.tacticaltablet.information.economy_classes\": \"Экономика и классы\""));
        assertTrue(english.contains("\"screen.tacticaltablet.main_menu.guide\": \"Information\""));
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path).replace("\r\n", "\n");
    }
}
