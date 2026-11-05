#!/usr/bin/env python3
"""Sprawdza kontrast WCAG par kolorów we wszystkich schematach z ui/theme/Theme.kt (D-17, D-39).

Uruchom: python tools/check_contrast.py
Od D-39 motywów jest kilka (Color.kt + Themes.kt), więc skrypt bierze każdy schemat, jaki znajdzie w Theme.kt:
nowy motyw nie wjedzie bez kontroli kompletu 35 ról.
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
PALETTE_FILES = (THEME_DIR / "Color.kt", THEME_DIR / "Themes.kt")

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


def parse_theme(text, palette_text):
    """Zwraca [(nazwa schematu, {rola: '#RRGGBB'})] dla każdego wywołania light/darkColorScheme w Theme.kt.

    Role wskazują nazwane stałe z Color.kt i Themes.kt (np. `primary = FireDark`), więc najpierw czytamy paletę.
    """
    palette = {
        name: "#" + hex_value.upper()
        for name, hex_value in re.findall(r"val\s+(\w+)\s*=\s*Color\(0x[fF]{2}([0-9a-fA-F]{6})\)", palette_text)
    }
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


def luminance(hex_color):
    def channel(value):
        c = int(value, 16) / 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    h = hex_color.lstrip("#")
    r, g, b = channel(h[0:2]), channel(h[2:4]), channel(h[4:6])
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a, b):
    la, lb = luminance(a), luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


def main():
    palette_text = "\n".join(path.read_text(encoding="utf-8") for path in PALETTE_FILES if path.exists())
    schemes = parse_theme(THEME.read_text(encoding="utf-8"), palette_text)
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
    print(f"\nSchematów: {len(schemes)}, problemów: {failures}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
