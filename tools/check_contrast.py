#!/usr/bin/env python3
"""Sprawdza kontrast WCAG par kolorów we wszystkich schematach z ui/theme/Theme.kt (D-17, D-39) i tłach (D-50).

Uruchom: python tools/check_contrast.py
Od D-39 motywów jest kilka (Color.kt + Themes.kt), więc skrypt bierze każdy schemat, jaki znajdzie w Theme.kt:
nowy motyw nie wjedzie bez kontroli kompletu 35 ról.
Od D-50 każdy motyw ma też warianty tła (Backgrounds.kt). Gradient leży pod tymi samymi kartami i napisami
co `surface`, więc w każdym swoim punkcie musi trzymać pary, w których tłem jest `surface` albo `background`.
Punkty liczymy jak Android: liniowo w sRGB między równo rozłożonymi kolorami (tak samo ThemeContractTest).
Od D-51 jest też zdjęcie dnia: filtr kładzie każdy piksel zdjęcia na odcinku pasma (`PhotoBand`), a karty nad nim są
szkłem, czyli `onSurface` z alfą motywu. W każdym punkcie pasma tekst na tle i na szklanej karcie trzyma progi,
a karta odróżnia się od tła na 1,12:1.
Kod wyjścia 1, jeśli któraś para nie spełnia progu — do wpięcia w CI, gdyby motyw zaczął się psuć.

Progi: 4.5:1 dla tekstu, 3:1 dla elementów interfejsu (obrysy, ikony).
Ostatnie trzy pary to separacja planów (karta i pasek wobec tła) — to nie jest wymóg WCAG,
tylko próg czytelności warstw przyjęty dla tej aplikacji.
"""
import re
import sys
from pathlib import Path

THEME_DIR = Path(__file__).resolve().parent.parent / "app/src/main/java/pl/mysttic/trainings/ui/theme"
THEME = THEME_DIR / "Theme.kt"
BACKGROUNDS = THEME_DIR / "Backgrounds.kt"
PALETTE_FILES = (THEME_DIR / "Color.kt", THEME_DIR / "Themes.kt", BACKGROUNDS)

# Role, które tło ekranu zastępuje: pary z nimi jako tłem sprawdzamy w każdym punkcie gradientu.
BACKDROP_ROLES = ("surface", "background")
# Punktów na odcinek między dwoma kolorami gradientu; tyle samo bierze ThemeContractTest.
POINTS_PER_SEGMENT = 64
# Role kart, które nad zdjęciem dnia są szkłem (D-51), i próg odróżnienia szklanej karty od zdjęcia.
CARD_ROLES = ("surfaceContainer", "surfaceContainerHighest", "surfaceVariant")
GLASS_SEPARATION = 1.12

# (tekst, tło, próg, opis)
PAIRS = [
    ("onSurface", "surface", 4.5, "tekst na powierzchni"),
    ("onSurfaceVariant", "surface", 4.5, "tekst pomocniczy"),
    ("onSurfaceVariant", "surfaceVariant", 4.5, "tekst na surfaceVariant"),
    ("onBackground", "background", 4.5, "tekst na tle"),
    ("onSurface", "surfaceContainerHighest", 4.5, "tekst na karcie"),
    ("onSurface", "surfaceContainer", 4.5, "tekst na pasku nawigacji"),
    ("onSurface", "surfaceContainerHigh", 4.5, "tekst w dialogu"),
    ("onPrimary", "primary", 4.5, "tekst przycisku"),
    ("onPrimaryContainer", "primaryContainer", 4.5, "tekst na kontenerze primary"),
    ("onSecondaryContainer", "secondaryContainer", 4.5, "zaznaczony chip, aktywna zakładka"),
    ("onTertiaryContainer", "tertiaryContainer", 4.5, "tekst na kontenerze tertiary"),
    ("onErrorContainer", "errorContainer", 4.5, "tekst na kontenerze błędu"),
    ("onError", "error", 4.5, "tekst na kolorze błędu"),
    ("onSecondary", "secondary", 4.5, "tekst na secondary"),
    ("inverseOnSurface", "inverseSurface", 4.5, "tekst na inverseSurface"),
    ("primary", "surface", 4.5, "primary jako tekst"),
    ("primary", "surfaceContainerHighest", 4.5, "primary jako tekst na karcie"),
    ("primary", "background", 4.5, "primary na tle"),
    ("error", "surface", 4.5, "„Brakuje sprzętu”"),
    ("error", "surfaceContainerHighest", 4.5, "błąd na karcie"),
    ("secondary", "surfaceContainer", 4.5, "label aktywnej zakładki"),
    ("outline", "surface", 3.0, "obrys kontrolki"),
    ("outline", "surfaceContainerHighest", 3.0, "obrys na karcie"),
    ("primary", "surfaceContainer", 3.0, "ikona na pasku"),
    ("surfaceContainerHighest", "background", 1.12, "karta odróżniona od tła"),
    ("surfaceContainer", "background", 1.04, "pasek odróżniony od tła"),
    # „Żar to informacja” (D-32): tertiary niesie nagłówki sekcji, serię dni i podpowiedzi progresji
    ("tertiary", "surface", 4.5, "nagłówek sekcji, seria dni"),
    ("tertiary", "surfaceContainer", 4.5, "żar na karcie"),
    ("onTertiary", "tertiary", 4.5, "tekst na żarze („Zastosuj”)"),
    ("primary", "surfaceContainerHigh", 3.0, "wskaźnik nawigacji, pasek „to teraz”"),
]


def parse_palette(palette_text):
    """Nazwane stałe kolorów (`val FireDark = Color(0xFFFF6A21)`) jako {nazwa: '#RRGGBB'}."""
    return {
        name: "#" + hex_value.upper()
        for name, hex_value in re.findall(r"val\s+(\w+)\s*=\s*Color\(0x[fF]{2}([0-9a-fA-F]{6})\)", palette_text)
    }


def parse_theme(text, palette_text):
    """Zwraca [(nazwa schematu, {rola: '#RRGGBB'})] dla każdego wywołania light/darkColorScheme w Theme.kt.

    Role wskazują nazwane stałe z Color.kt i Themes.kt (np. `primary = FireDark`), więc najpierw czytamy paletę.
    """
    palette = parse_palette(palette_text)
    schemes = []
    for match in re.finditer(r"val\s+(\w+)\s*=\s*(light|dark)ColorScheme\(", text):
        start = match.end()
        depth, i = 1, start
        while depth:
            depth += {"(": 1, ")": -1}.get(text[i], 0)
            i += 1
        body = text[start:i - 1]
        roles = {
            role: palette[name]
            for role, name in re.findall(r"(\w+)\s*=\s*(\w+)\s*,", body)
            if name in palette
        }
        schemes.append((match.group(1), roles))
    return schemes


def parse_backgrounds(text, palette):
    """Zwraca [(motyw, id tła, ['#RRGGBB', ...])] z `enum class AppBackground` w Backgrounds.kt.

    Wpis wygląda tak: `FIRE_EMBER(AppTheme.FIRE, "zar", "Żar", listOf(FireEmberTop, FireEmberBottom))`;
    `emptyList()` to tło jednolite, czyli `surface` schematu, który i tak sprawdzają pary wyżej.
    """
    entries = re.findall(
        r"\w+\(\s*AppTheme\.(\w+),\s*\"([^\"]+)\",\s*\"[^\"]*\",\s*(?:emptyList\(\)|listOf\(([^)]*)\))\s*\)",
        text,
    )
    return [
        (theme, background_id, [palette[name.strip()] for name in stops.split(",") if name.strip()])
        for theme, background_id, stops in entries
    ]


def parse_photo_bands(text, palette):
    """[(motyw, id, '#low', '#high', alfa)] z wpisów zdjęcia dnia: `…, emptyList(), PhotoBand(Low, High, glass = 0.08f))`."""
    entries = re.findall(
        r"\w+\(\s*AppTheme\.(\w+),\s*\"([^\"]+)\",\s*\"[^\"]*\",\s*emptyList\(\),\s*"
        r"PhotoBand\(\s*(\w+),\s*(\w+),\s*glass\s*=\s*([0-9.]+)f\s*\)\s*\)",
        text,
    )
    return [(theme, bid, palette[low], palette[high], float(alpha)) for theme, bid, low, high, alpha in entries]


def parse_scheme_for(text):
    """Które schematy należą do których motywów: gałęzie `AppTheme.FIRE -> FireScheme` w `schemeFor`."""
    return dict(re.findall(r"AppTheme\.(\w+)\s*->\s*(\w+)", text))


def rgb(hex_color):
    h = hex_color.lstrip("#")
    return tuple(int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))


def luminance(color):
    """Względna jasność WCAG; kolor jako '#RRGGBB' albo krotka (r, g, b) w zakresie 0..1."""
    def channel(c):
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (channel(c) for c in (rgb(color) if isinstance(color, str) else color))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def quantize(color):
    """8 bitów na kanał: tyle ma ekran i tyle trzyma `Color` z Compose w sRGB, więc tak samo liczy ThemeContractTest."""
    return tuple(round(c * 255) / 255 for c in color)


def gradient_points(stops):
    """Punkty gradientu tak, jak rysuje go Android: liniowo w sRGB między równo rozłożonymi kolorami."""
    colors = [rgb(stop) for stop in stops]
    return [
        quantize(tuple(a[k] + (b[k] - a[k]) * i / POINTS_PER_SEGMENT for k in range(3)))
        for a, b in zip(colors, colors[1:])
        for i in range(POINTS_PER_SEGMENT + 1)
    ]


def as_hex(color):
    return "#" + "".join(f"{round(c * 255):02X}" for c in color)


def mix(under, over, alpha):
    """Kolor `over` z alfą położony na `under`, zmieszany w sRGB tak jak rysuje Android."""
    return quantize(tuple(u + (o - u) * alpha for u, o in zip(under, over)))


def contrast(a, b):
    la, lb = luminance(a), luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


def check_backgrounds(theme_text, palette_text, schemes):
    """Każdy gradient z Backgrounds.kt wobec ról swojego motywu; zwraca liczbę złamanych par."""
    if not BACKGROUNDS.exists():
        return 0
    backgrounds = parse_backgrounds(BACKGROUNDS.read_text(encoding="utf-8"), parse_palette(palette_text))
    owner = parse_scheme_for(theme_text)
    by_name = dict(schemes)
    pairs = [pair for pair in PAIRS if pair[1] in BACKDROP_ROLES]
    failures = 0
    print(f"\n=== Tła (D-50): pary na {' i '.join(BACKDROP_ROLES)} w każdym punkcie gradientu")
    for theme, background_id, stops in backgrounds:
        if len(stops) < 2:
            continue
        scheme_name = owner.get(theme)
        scheme = by_name.get(scheme_name)
        if scheme is None:
            print(f"  BRAK SCHEMATU  {theme}/{background_id}")
            failures += 1
            continue
        worst = None
        for fg, bg, threshold, label in pairs:
            ratio, point = min(
                ((contrast(scheme[fg], point), point) for point in gradient_points(stops)),
                key=lambda item: item[0],
            )
            if ratio < threshold:
                failures += 1
                print(f"  BŁĄD {ratio:5.2f}:1 (min {threshold})  {theme.lower()}/{background_id}: {fg} na {as_hex(point)} — {label}")
            if worst is None or ratio / threshold < worst[0] / worst[1]:
                worst = (ratio, threshold, fg, point)
        ratio, threshold, fg, point = worst
        print(
            f"  {'ok  ' if ratio >= threshold else 'BŁĄD'} {theme.lower()}/{background_id} ({' → '.join(stops)}): "
            f"najciaśniej {fg} na {as_hex(point)} {ratio:.3f}:1 (min {threshold})"
        )
    return failures


def check_photo_bands(theme_text, palette_text, schemes):
    """Pasmo każdego zdjęcia dnia razem ze szklanymi kartami; zwraca liczbę złamanych par."""
    if not BACKGROUNDS.exists():
        return 0
    bands = parse_photo_bands(BACKGROUNDS.read_text(encoding="utf-8"), parse_palette(palette_text))
    owner = parse_scheme_for(theme_text)
    by_name = dict(schemes)
    failures = 0
    print("\n=== Zdjęcie dnia (D-51): pasmo zdjęcia i szklane karty w każdym punkcie")
    for theme, background_id, low, high, alpha in bands:
        scheme = by_name.get(owner.get(theme))
        if scheme is None:
            print(f"  BRAK SCHEMATU  {theme}/{background_id}")
            failures += 1
            continue
        on_surface = rgb(scheme["onSurface"])
        worst = None
        for point in gradient_points([low, high]):
            glass = mix(point, on_surface, alpha)
            checks = []
            for fg, bg, threshold, label in PAIRS:
                if bg in BACKDROP_ROLES and fg not in CARD_ROLES:
                    checks.append((contrast(scheme[fg], point), threshold, f"{fg} na tle {as_hex(point)}", label))
                elif bg in CARD_ROLES and fg not in CARD_ROLES + BACKDROP_ROLES:
                    checks.append((contrast(scheme[fg], glass), threshold, f"{fg} na szkle {as_hex(glass)}", label))
            checks.append((contrast(glass, point), GLASS_SEPARATION, f"szkło {as_hex(glass)} na {as_hex(point)}", "karta odróżniona od zdjęcia"))
            for ratio, threshold, where, label in checks:
                if ratio < threshold:
                    failures += 1
                    print(f"  BŁĄD {ratio:5.2f}:1 (min {threshold})  {theme.lower()}/{background_id}: {where} — {label}")
                if worst is None or ratio / threshold < worst[0] / worst[1]:
                    worst = (ratio, threshold, where)
        ratio, threshold, where = worst
        print(f"  {'ok  ' if ratio >= threshold else 'BŁĄD'} {theme.lower()}/{background_id} ({low} → {high}, szkło {alpha:g}): "
              f"najciaśniej {where} {ratio:.3f}:1 (min {threshold})")
    return failures


def main():
    palette_text = "\n".join(path.read_text(encoding="utf-8") for path in PALETTE_FILES if path.exists())
    theme_text = THEME.read_text(encoding="utf-8")
    schemes = parse_theme(theme_text, palette_text)
    if not schemes:
        print("Nie znalazłem ani jednego schematu w Theme.kt")
        return 1
    failures = 0
    for name, scheme in schemes:
        print(f"\n=== {name} ({len(scheme)} ról)")
        for fg, bg, threshold, label in PAIRS:
            if fg not in scheme or bg not in scheme:
                print(f"  BRAK ROLI  {fg} / {bg}")
                failures += 1
                continue
            ratio = contrast(scheme[fg], scheme[bg])
            ok = ratio >= threshold
            failures += not ok
            print(f"  {'ok  ' if ok else 'BŁĄD'} {ratio:5.2f}:1 (min {threshold})  {fg} na {bg} — {label}")
    failures += check_backgrounds(theme_text, palette_text, schemes)
    failures += check_photo_bands(theme_text, palette_text, schemes)
    print(f"\nSchematów: {len(schemes)}, problemów: {failures}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
