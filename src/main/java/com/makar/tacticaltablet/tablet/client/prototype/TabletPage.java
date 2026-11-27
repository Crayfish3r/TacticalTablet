package com.makar.tacticaltablet.tablet.client.prototype;

import java.util.List;

/** The single binding between model part names and client pages. No geometry lives here. */
enum TabletPage {
    PREPARATION("hardware_key_2", "key_index_2", "Подготовка к бою"),
    PROFILE("hardware_key_1", "key_index_1", "Личная статистика"),
    CONTRACT_TRACKER("hardware_key_0", "key_index_0", "Контрактный трекер"),
    SHOP("hardware_key_3", "key_index_3", "Магазин"),
    EMPTY_4("hardware_key_4", "key_index_4", ""),
    EMPTY_5("hardware_key_5", "key_index_5", "");

    static final List<TabletPage> PAGES = List.of(values());
    final String hardwareKey, indicator, title;

    TabletPage(String hardwareKey, String indicator, String title) {
        this.hardwareKey = hardwareKey; this.indicator = indicator; this.title = title;
    }

    static TabletPage forHardwareKey(String name) {
        for (TabletPage page : PAGES) if (page.hardwareKey.equals(name)) return page;
        return null;
    }
}
