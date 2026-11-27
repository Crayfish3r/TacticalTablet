"""Generate layered SVG reference screens for the TacticalTablet Figma handoff.

Only the original tablet shell is raster. Panels, text, controls and lists remain
separate SVG elements with stable IDs. This is a design artifact, not game code.
"""

from __future__ import annotations

import base64
from html import escape
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent / "screens"
SHELLS = ROOT / "src/main/resources/assets/tacticaltablet/textures/gui"

COL = {
    "panel": "#12181D",
    "card": "#192228",
    "hover": "#223139",
    "selected": "#1D3533",
    "border": "#344249",
    "accent": "#59E0B7",
    "text": "#F2F7F8",
    "muted": "#A5B3B9",
    "warning": "#FFC857",
    "success": "#65D69C",
    "danger": "#FF657A",
}


class Svg:
    def __init__(self, width: int = 380, height: int = 220):
        self.width, self.height = width, height
        self.nodes: list[str] = []

    def rect(self, ident: str, x: int, y: int, w: int, h: int,
             fill: str, stroke: str | None = None, stroke_width: int = 1):
        border = f' stroke="{stroke}" stroke-width="{stroke_width}"' if stroke else ""
        self.nodes.append(
            f'<rect id="{escape(ident)}" x="{x}" y="{y}" width="{w}" '
            f'height="{h}" fill="{fill}"{border}/>'
        )

    def text(self, ident: str, value: str, x: int, y: int, size: int = 10,
             color: str = COL["text"], anchor: str = "start"):
        self.nodes.append(
            f'<text id="{escape(ident)}" x="{x}" y="{y}" font-family="Pixelify Sans, monospace" '
            f'font-size="{size}" fill="{color}" text-anchor="{anchor}">{escape(value)}</text>'
        )

    def shell(self, tier: str = "basic"):
        filename = {"basic": "tablet.png", "epic": "tablet_epic.png",
                    "legend": "tablet_legend.png"}[tier]
        encoded = base64.b64encode((SHELLS / filename).read_bytes()).decode("ascii")
        self.nodes.append(
            f'<image id="existing-shell-{tier}" x="0" y="0" width="380" height="220" '
            f'image-rendering="pixelated" href="data:image/png;base64,{encoded}"/>'
        )
        self.rect("screen-surface", 88, 8, 286, 204, COL["panel"])

    def tablet(self, title: str, active: int = 0, tier: str = "basic"):
        self.shell(tier)
        labels = ("КЛАССЫ", "МАГАЗИН", "VIP", "КЛАНЫ", "ПРОФИЛЬ")
        for index, label in enumerate(labels):
            y = 14 + 33 * index
            selected = index == active
            self.rect(f"nav-{index}", 12, y, 72, 28,
                      COL["selected"] if selected else COL["card"],
                      COL["accent"] if selected else COL["border"],
                      2 if selected else 1)
            self.text(f"nav-label-{index}", label, 48, y + 18, 10, anchor="middle")
        self.text("page-title", title, 94, 29, 14)
        self.rect("rtp-button", 286, 16, 78, 20, COL["card"], COL["border"])
        self.text("rtp-label", "RTP", 325, 30, 11, anchor="middle")
        self.rect("header-divider", 94, 40, 274, 1, COL["border"])

    def button(self, ident: str, label: str, x: int, y: int, w: int, h: int,
               state: str = "default", danger: bool = False):
        fill = COL["selected"] if state in ("selected", "selectedFocus") else (
            COL["hover"] if state in ("hover", "focus") else COL["card"])
        stroke = COL["danger"] if danger else (
            COL["accent"] if state in ("focus", "selectedFocus") else COL["border"])
        self.rect(ident, x, y, w, h, fill, stroke,
                  2 if state in ("focus", "selectedFocus") else 1)
        self.text(f"{ident}-label", label, x + w // 2, y + h // 2 + 4,
                  10, COL["danger"] if danger else COL["text"], "middle")

    def footer(self, value: str):
        self.text("footer", value, 94, 207, 9, COL["muted"])

    def finish(self, name: str):
        OUT.mkdir(parents=True, exist_ok=True)
        body = "\n".join(self.nodes)
        (OUT / name).write_text(
            f'<svg xmlns="http://www.w3.org/2000/svg" '
            f'xmlns:xlink="http://www.w3.org/1999/xlink" '
            f'width="{self.width}" height="{self.height}" '
            f'viewBox="0 0 {self.width} {self.height}">\n'
            f'<title>TacticalTablet: {escape(name)}</title>\n{body}\n</svg>\n',
            encoding="utf-8",
        )


def clan_info(s: Svg, owner: bool):
    s.button("back", "Назад", 94, 46, 76, 22)
    s.text("clan-name", "[СЕВ] Северный Экспедици…", 231, 62, 11,
           COL["warning"], "middle")
    s.text("role", "Статус: глава" if owner else "Статус: участник",
           94, 80, 10, COL["warning"] if owner else COL["success"])
    s.text("owner", "Глава: Happ", 94, 94)
    s.text("members-count", "Участников: 6/10", 94, 108)
    s.text("clan-coins", "КК: 28", 265, 108, 10, COL["success"])
    s.rect("members-box", 94, 120, 126, 58, COL["card"], COL["border"])
    s.text("members-title", "Участники", 98, 132)
    for i, name in enumerate(("Happ", "Negativchik", "ДлинноеИмяИгрока")):
        s.text(f"member-{i}", name, 98, 145 + i * 11, 9)
    s.rect("members-scroll-track", 216, 137, 3, 39, COL["border"])
    s.rect("members-scroll-thumb", 216, 137, 3, 20, COL["accent"])


def main():
    s = Svg()
    s.tablet("КЛАССЫ · ПРОКАЧКА")
    s.button("tab-select", "Выбор", 94, 46, 132, 20)
    s.button("tab-progression", "Прокачка", 232, 46, 136, 20, "selectedFocus")
    s.text("class-name", "Штурмовик", 125, 86, 12)
    s.rect("class-icon-plate", 96, 71, 24, 24, COL["text"])
    for i, tier in enumerate(("BASIC", "RARE", "EPIC", "LEGEND", "MONSTER")):
        s.rect(f"tier-{tier}", 94 + i * 54, 96, 52, 11,
               COL["selected"] if i == 4 else COL["card"])
        s.text(f"tier-label-{tier}", tier, 96 + i * 54, 105, 9)
    s.text("xp-total", "Всего: 2000 XP · максимальный уровень", 96, 119, 10)
    s.rect("xp-track", 96, 122, 270, 4, COL["border"])
    s.rect("xp-fill", 96, 122, 270, 4, COL["accent"])
    s.text("balance", "Баланс: 1367 монет", 96, 145, 10, COL["warning"])
    s.text("reason", "Максимальный уровень", 96, 159, 10, COL["muted"])
    s.button("upgrade-disabled", "Максимальный уровень", 94, 172, 274, 24)
    s.footer("Данные прокачки обновляются с сервера")
    s.finish("05-classes-progression.svg")

    s = Svg()
    s.tablet("КЛАНЫ", 3)
    names = ("[ZOV] Happ", "[HGPK] Negativchik",
             "[СЕВ] Северный Экспедици…", "[PMC] Wagner Group")
    for i, name in enumerate(names):
        s.button(f"clan-row-{i}", name, 94, 46 + i * 24, 264, 22,
                 "focus" if i == 2 else "default")
    s.rect("list-track", 364, 46, 3, 94, COL["border"])
    s.rect("list-thumb", 364, 46, 3, 48, COL["accent"])
    s.button("create", "Создать", 171, 172, 120, 24)
    s.footer("До 8 кланов · список прокручивается")
    s.finish("06-clans-list.svg")

    s = Svg()
    s.tablet("КЛАНЫ", 3)
    s.text("empty-title", "Кланов пока нет", 94, 73, 12)
    s.text("empty-detail", "Создай первый клан за 1000 монет", 94, 96, 10, COL["muted"])
    s.button("create", "Создать", 171, 172, 120, 24, "focus")
    s.footer("Пустой список")
    s.finish("07-clans-empty.svg")

    s = Svg()
    s.tablet("КЛАН · УЧАСТНИК", 3)
    clan_info(s, False)
    s.button("leave", "Выйти", 130, 178, 120, 24, danger=True)
    s.footer("Выход требует подтверждения")
    s.finish("08-clan-member.svg")

    s = Svg()
    s.tablet("КЛАН · ГЛАВА", 3)
    clan_info(s, True)
    s.rect("pending-box", 232, 120, 132, 58, COL["card"], COL["border"])
    s.text("pending-title", "Заявки", 236, 132)
    s.text("pending-name", "ИгрокДлинноеИмя…", 236, 145, 9)
    s.button("accept", "OK", 292, 151, 30, 14)
    s.button("reject", "X", 326, 151, 30, 14, danger=True)
    s.button("color", "Цвет 10КК", 130, 178, 120, 24)
    s.button("disband", "Распустить", 254, 178, 120, 24, danger=True)
    s.footer("Заявки и участники прокручиваются отдельно")
    s.finish("09-clan-owner.svg")

    s = Svg()
    s.tablet("НОВЫЙ КЛАН", 3)
    s.text("name-label", "Название", 82, 74)
    s.rect("name-field", 82, 78, 190, 20, COL["card"], COL["accent"], 2)
    s.text("name-placeholder", "Северный Экспедиционный", 86, 92, 10)
    s.text("tag-label", "Тег", 82, 108)
    s.rect("tag-field", 82, 112, 68, 20, COL["card"], COL["border"])
    s.text("tag-value", "СЕВ", 86, 126)
    s.text("color-label", "Цвет", 170, 108)
    for i, color in enumerate(("#FF5555", "#5599FF", "#55FF55", "#FFFF55",
                                "#FFA64D", "#FF66CC", "#B266FF", "#111111")):
        s.rect(f"color-{i}", 170 + i * 22, 112, 18, 18, color,
               COL["accent"] if i == 1 else COL["border"], 2 if i == 1 else 1)
    s.text("name-rule", "Название: 3–24 символа", 82, 145, 9, COL["muted"])
    s.text("tag-rule", "Тег: 1–4 символа", 82, 157, 9, COL["muted"])
    s.text("price", "Стоимость: 1000 монет", 208, 157, 9, COL["warning"])
    s.button("cancel", "Отмена", 70, 170, 96, 24)
    s.button("submit", "Создать", 214, 170, 96, 24, "focus")
    s.finish("10-clan-create.svg")

    s = Svg()
    s.tablet("ЦВЕТ КЛАНА", 3)
    s.text("preview", "[СЕВ] Северный Экспедици…", 190, 76, 12,
           COL["warning"], "middle")
    s.text("palette-label", "Свободные цвета", 106, 104)
    for i, color in enumerate(("#FF5555", "#5599FF", "#55FF55", "#FFFF55",
                                "#FFA64D", "#FF66CC", "#B266FF", "#111111")):
        s.rect(f"color-{i}", 106 + i * 24, 108, 18, 18, color,
               COL["accent"] if i == 2 else COL["border"], 2 if i == 2 else 1)
    s.text("price", "Стоимость: 10 КК", 106, 147, 10, COL["warning"])
    s.text("balance", "КК клана: 28", 106, 159, 10, COL["success"])
    s.button("cancel", "Отмена", 70, 170, 96, 24)
    s.button("submit", "Изменить", 214, 170, 96, 24, "focus")
    s.finish("11-clan-color.svg")

    for danger, filename in ((False, "12-confirm-purchase.svg"),
                             (True, "13-confirm-danger.svg")):
        s = Svg()
        s.tablet("МАГАЗИН" if not danger else "КЛАНЫ", 1 if not danger else 3)
        s.rect("modal-backdrop", 0, 0, 380, 220, "#05090D")
        s.rect("dialog-panel", 60, 42, 260, 136, COL["panel"],
               COL["danger"] if danger else COL["accent"])
        s.text("dialog-title", "Распустить клан?" if danger else "Купить класс?",
               74, 69, 14, COL["danger"] if danger else COL["accent"])
        s.text("dialog-body", "[ZOV] Happ" if danger else "Солдат · 50 монет",
               74, 95, 11)
        if not danger:
            s.text("balance", "У тебя: 1367 монет", 74, 112, 10, COL["muted"])
        else:
            s.text("danger-note", "Действие требует подтверждения", 74, 112, 10,
                   COL["muted"])
        s.button("cancel", "Отмена", 72, 142, 96, 24,
                 "focus" if danger else "default")
        s.button("confirm", "ОК" if danger else "Купить", 212, 142, 96, 24,
                 "default", danger)
        s.finish(filename)

    s = Svg(320, 180)
    s.rect("compact-surface", 0, 0, 320, 180, COL["panel"], COL["accent"])
    s.text("compact-title", "КЛАССЫ · компактный режим", 8, 18, 12)
    for i, name in enumerate(("КЛАССЫ", "МАГАЗИН", "VIP", "КЛАНЫ", "ПРОФИЛЬ")):
        s.button(f"compact-nav-{i}", name, 8 + i * 61, 24, 58, 20,
                 "selectedFocus" if i == 0 else "default")
    for i, name in enumerate(("Штурмовик", "Снайпер", "Разведчик")):
        s.button(f"compact-class-{i}", name, 8, 48 + i * 38, 298, 34)
    s.rect("compact-scrollbar", 309, 48, 3, 116, COL["accent"])
    s.text("compact-footer", "1:1 GUI px · корпус скрыт · список прокручивается",
           8, 176, 9, COL["muted"])
    s.finish("14-compact-320x180.svg")


if __name__ == "__main__":
    main()
