# -*- coding: utf-8 -*-
"""Prezentacja z przebiegu treningu: PPTX (python-pptx) oraz podgląd PNG i PDF (Pillow) z tej samej specyfikacji.

    python docs/prezentacja/deck.py [katalog_zrzutow] [katalog_zrzutow_steppera] [katalog_wyjsciowy]

Domyślnie czyta zrzuty z docs/screenshots/przebieg (z `tools/przebieg.py --tempo 3` i testu steppera)
i zapisuje PPTX oraz PDF (render z Pillow, do pokazu bez PowerPointa) do docs/prezentacja;
podgląd slajdów PNG ląduje w build/prezentacja.
"""
import glob, os, sys
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.oxml.ns import qn
from lxml import etree
from PIL import Image, ImageDraw, ImageFont

SHOTS = sys.argv[1] if len(sys.argv) > 1 else "docs/screenshots/przebieg"
EXTRA = sys.argv[2] if len(sys.argv) > 2 else SHOTS
OUT = sys.argv[3] if len(sys.argv) > 3 else "docs/prezentacja"
SCRATCH = os.path.join("build", "prezentacja")  # zmniejszone zrzuty i podgląd slajdów, nie trzymamy ich w docs
NAME = "Mysttic-Trainings-pierwszy-trening"
W, H = 13.333, 7.5
BG, INK, MUTED, FIRE, ON_FIRE, EMBER, CARD, FRAME, OUTLINE = "120809", "FBEDE8", "BAADAA", "FF6A21", "2A0B00", "FFC24A", "23100F", "4A1A18", "8A6660"
FONT = "Calibri"
FONT_FILES = {False: "C:/Windows/Fonts/calibri.ttf", True: "C:/Windows/Fonts/calibrib.ttf"}
SCALE = 120  # px na cal w podglądzie


def shot(pattern, base=SHOTS):
    m = sorted(glob.glob(os.path.join(base, pattern)))
    if not m:
        raise SystemExit(f"brak zrzutu {pattern} w {base}")
    return m[0]


def small(path):
    """Zrzut zmniejszony do 800 px szerokości, żeby plik prezentacji nie ważył kilkunastu MB."""
    os.makedirs(os.path.join(SCRATCH, "img"), exist_ok=True)
    dst = os.path.join(SCRATCH, "img", os.path.basename(path))
    if not os.path.exists(dst):
        im = Image.open(path).convert("RGB")
        im.resize((800, int(im.height * 800 / im.width)), Image.LANCZOS).save(dst, optimize=True)
    return dst


# ---- specyfikacja elementów --------------------------------------------------------------------

def text(x, y, w, h, s, size, color=INK, bold=False, align="left", anchor="top", spacing=None):
    return dict(t="text", x=x, y=y, w=w, h=h, s=s, size=size, color=color, bold=bold, align=align, anchor=anchor, spacing=spacing)


def bullets(x, y, w, h, items, size=15, color=INK, gap=9):
    return dict(t="bullets", x=x, y=y, w=w, h=h, items=items, size=size, color=color, gap=gap)


def rect(x, y, w, h, fill=CARD, line=None, radius=0.0, line_w=1.0):
    return dict(t="rect", x=x, y=y, w=w, h=h, fill=fill, line=line, radius=radius, line_w=line_w)


def oval(x, y, d, fill=FIRE):
    return dict(t="oval", x=x, y=y, w=d, h=d, fill=fill)


def img(path, x, y, w, h):
    return dict(t="img", path=path, x=x, y=y, w=w, h=h)


def ratio(path):
    im = Image.open(path)
    return im.width / im.height


def phone(e, path, x, y, h, caption=None):
    w = h * ratio(path)
    pad = 0.06
    e.append(rect(x - pad, y - pad, w + 2 * pad, h + 2 * pad, fill=CARD, line=FRAME, radius=0.14, line_w=1.25))
    e.append(img(small(path), x, y, w, h))
    if caption:
        e.append(text(x - 0.17, y + h + 0.12, w + 0.34, 0.55, caption, 11.5, MUTED, align="center"))
    return w


def phone_row(e, items, y, h, x0, x1, gap=0.38):
    ws = [h * ratio(p) for p, _ in items]
    total = sum(ws) + gap * (len(ws) - 1)
    x = x0 + (x1 - x0 - total) / 2
    for (p, c), w in zip(items, ws):
        phone(e, p, x, y, h, c)
        x += w + gap


def header(e, overline, title):
    e.append(text(0.6, 0.42, 12.1, 0.32, overline.upper(), 11.5, EMBER, bold=True, spacing=2))
    e.append(text(0.6, 0.74, 12.1, 0.9, title, 30, INK, bold=True, anchor="top"))


def screens_slide(overline, title, items, points, notes, h=5.0):
    e = []
    header(e, overline, title)
    n = len(items)
    if n >= 4:
        phone_row(e, items, 1.78, 4.7, 0.6, 12.75)
    elif n == 3:
        phone_row(e, items, 1.8, h, 0.6, 8.2)
        e.append(bullets(8.7, 1.85, 4.05, 5.0, points))
    else:
        phone_row(e, items, 1.8, h, 0.6, 5.9)
        e.append(bullets(6.5, 1.85, 6.2, 5.0, points))
    if n >= 4 and points:
        e.append(text(0.6, 6.85, 12.1, 0.55, "  ·  ".join(points), 11.5, MUTED))
    return dict(elems=e, notes=notes)


def build(shots):
    S = []

    # 1. Tytuł
    e = [text(0.7, 0.85, 6.4, 0.32, "MYSTTIC TRAININGS  ·  PRZEBIEG NA EMULATORZE  ·  8 WRZEŚNIA 2026", 11.5, EMBER, bold=True, spacing=2),
         text(0.7, 1.35, 6.4, 2.9, "Od pierwszego uruchomienia do końca pierwszego treningu", 40, INK, bold=True),
         text(0.7, 4.45, 6.2, 1.9, "Prywatna aplikacja do treningów w domu: kreator układa cykl z kilku odpowiedzi, "
              "„Dziś” pokazuje jeden trening, player prowadzi przez serie i przerwy. "
              "Wersja rozwojowa po zmianie D-34, Pixel 9 Pro (API 37).", 16, MUTED)]
    phone_row(e, [(shots["dzis-plan"], None), (shots["seria-01"], None)], 0.85, 5.8, 7.3, 12.9)
    S.append(dict(elems=e, notes="Przebieg zebrany automatem tools/przebieg.py na emulatorze, w realnym tempie: 3 s na powtórzenie, "
                  "serie na czas i przerwy do końca. Ustawienia kreatora najniższe z możliwych."))

    # 2. Cztery etapy
    e = []
    header(e, "Plan pokazu", "Cztery etapy, jeden trening")
    cards = [
        ("Kreator planu", "Cztery kroki: sylwetka i poziom, cel, czas, sprzęt. Bez konta, bez sieci.", shots["kreator-1"]),
        ("Cykl i „Dziś”", "Trzydzieści dni jako kolejka treningów. Na ekranie zawsze tylko następny.", shots["dzis-plan"]),
        ("Trening w playerze", "Osiemnaście serii, ok. 15 min: odliczanie, powtórzenia, przerwy, sygnały.", shots["seria-01"]),
        ("Koniec i historia", "Ocena RPE, notatka, plan odhaczony, wpis w historii z wykresami.", shots["podsumowanie"]),
    ]
    cw, gap = 2.9, 0.3
    x = (W - (4 * cw + 3 * gap)) / 2
    for i, (h1, d, p) in enumerate(cards):
        e.append(rect(x, 1.8, cw, 4.85, fill=CARD, radius=0.18))
        e.append(oval(x + 0.25, 2.05, 0.42, FIRE))
        e.append(text(x + 0.25, 2.05, 0.42, 0.42, str(i + 1), 14, ON_FIRE, bold=True, align="center", anchor="middle"))
        e.append(text(x + 0.82, 2.03, cw - 1.0, 0.46, h1, 15, INK, bold=True, anchor="middle"))
        e.append(text(x + 0.25, 2.62, cw - 0.5, 1.1, d, 12, MUTED))
        ph = 2.8
        pw = ph * ratio(p)
        e.append(img(small(p), x + (cw - pw) / 2, 3.7, pw, ph))
        x += cw + gap
    e.append(text(0.6, 6.85, 12.1, 0.5, "Ustawienia kreatora: początkujący · kondycja · 2 dni w tygodniu · 15 min · bez sprzętu.   "
                  "Serie odhaczane w tempie 3 s na powtórzenie, serie na czas i przerwy do końca.", 11.5, MUTED))
    S.append(dict(elems=e, notes="Jeden przebieg od pustej aplikacji do wpisu w historii. Każdy etap ma osobny slajd ze zrzutami."))

    # 3. Start i krok 1
    S.append(screens_slide("Etap 1 · kreator", "Start: puste „Dziś” i pierwszy krok kreatora",
        [(shots["dzis-bez-planu"], "Pierwsze uruchomienie: „Dziś” bez planu"),
         (shots["kreator-1"], "Krok 1: wzrost, waga, poziom")],
        ["Bez planu ekran „Dziś” ma jeden przycisk: „Ułóż plan”. Nie ma listy treningów do przeglądania.",
         "Krok 1 zbiera wzrost, wagę i poziom. Poziom decyduje o liczbie powtórzeń, rund i długości przerw.",
         "Nowość: pasek z uchwytem. Tapnięcie albo przeciągnięcie ustawia wartość, przytrzymanie „−” lub „+” powtarza krok. Wcześniej 30 kg różnicy to było 60 tapnięć.",
         "BMI liczy się na bieżąco; przy wysokim plan omija skoki."],
        "Na zrzucie waga 61 kg po przytrzymaniu klawisza minus przez 1,3 s: czternaście kroków."))

    # 4. Kroki 2-4
    S.append(screens_slide("Etap 1 · kreator", "Cel, czas i sprzęt",
        [(shots["kreator-2"], "Krok 2: cel"),
         (shots["kreator-3"], "Krok 3: dni, długość treningu i cyklu"),
         (shots["kreator-4"], "Krok 4: sprzęt")],
        ["Cztery cele: siła (serie proste), kondycja i redukcja (obwody), mobilność (spokojne tempo, rozciąganie).",
         "Długość treningu ma górną granicę z poziomu: początkujący do 45 min, średniozaawansowany do 60, zaawansowany do 75. Dłuższej sesji nie da się wypełnić bez łamania widełek rund.",
         "Sprzęt to twardy filtr: plan nigdy nie zaproponuje ćwiczenia na coś, czego nie ma. Bez zaznaczenia układa trening z masy własnej ciała.",
         "Stopka podsumowuje na bieżąco: 9 treningów w 30 dni, 2 razy w tygodniu po 15 min."],
        "Kreator ma cztery kroki i zawsze pokazuje, ile treningów z tego wyjdzie."))

    # 5. Gotowy plan
    S.append(screens_slide("Etap 2 · cykl i Dziś", "Gotowy plan: cykl, „Dziś” i szczegóły treningu",
        [(shots["plan-po-kreatorze"], "Podgląd cyklu po ułożeniu"),
         (shots["dzis-plan"], "„Dziś” z następnym treningiem"),
         (shots["trening"], "Szczegóły: dwa bloki po 2 rundy")],
        ["Cykl to kolejka, nie kalendarz: dzień jest odhaczony przez ukończony trening, niezależnie od daty.",
         "„Dziś” pokazuje wyłącznie następną pozycję z kolejki z przyciskiem Start.",
         "„Całe ciało A”: 9 ćwiczeń w dwóch blokach po 2 rundy, 18 serii, ok. 15 min. Rundy trzymają się poziomu, czas wypełniają kolejne ćwiczenia i przerwy (D-34).",
         "Poziom można zmienić w każdej chwili, także tutaj: player przeskaluje cele."],
        "Przed D-34 ten sam profil dostawał dwa ćwiczenia po sześć i siedem rund."))

    # 6. Player
    S.append(screens_slide("Etap 3 · player", "Player: odliczanie, seria, przerwa, seria na czas",
        [(shots["odliczanie"], "Odliczanie"),
         (shots["seria-01"], "Seria 1: 11 powtórzeń"),
         (shots["przerwa-02"], "Przerwa i zapowiedź"),
         (shots["seria-10"], "Seria na czas")],
        ["Liczby pod czytanie z półtora metra", "„Zrobione” zapisuje faktyczną liczbę powtórzeń, „−” i „+” ją poprawiają",
         "Przerwę można wydłużyć o 15 s albo pominąć", "Sygnały dźwiękiem i wibracją, powiadomienie na blokadzie"],
        "Player żyje w usłudze na pierwszym planie, więc zgaszony ekran nie przerywa treningu."))

    # 7. Rundy i bloki
    S.append(screens_slide("Etap 3 · player", "Rundy, bloki i ostatnia seria",
        [(shots["przerwa-06"], "Przerwa po pierwszej rundzie"),
         (shots["przerwa-11"], "Przerwa między blokami"),
         (shots["seria-18"], "Seria 18 z 18")],
        ["Przerwa po ćwiczeniu 15 s, po rundzie 50 s, między blokami minuta. Wszystko po skalowaniu na poziom początkujący.",
         "Pasek u góry pokazuje postęp całego treningu, nagłówek numer serii.",
         "Trening dłuższy niż sześć ćwiczeń jest dzielony na bloki; tu 5 i 4 ćwiczenia.",
         "Wstecz cofa do poprzedniej serii, Pauza zatrzymuje zegar, Przerwij kończy z zapisem tego, co zrobione."],
        "Zrzuty z jednego przebiegu w realnym tempie, dlatego zegar w nagłówku rośnie."))

    # 8. Koniec treningu
    S.append(screens_slide("Etap 4 · podsumowanie", "Koniec treningu: podsumowanie, ocena, „Dziś” po treningu",
        [(shots["podsumowanie"], "Podsumowanie treningu"),
         (shots["podsumowanie-rpe"], "Ocena RPE od 6 do 10 i notatka"),
         (shots["dzis-po-treningu"], "„Dziś” po treningu")],
        ["Podsumowanie liczy serie, powtórzenia i sekundy pracy na czas; przy kolejnym wykonaniu pokaże porównanie.",
         "RPE w skali od 6 do 10 i notatka trafiają do historii.",
         "Po zakończeniu „Dziś” pokazuje już „Całe ciało B”, seria dni rośnie do jednego, a ostatni czas jest na liczniku.",
         "Kolejny trening można zacząć od razu: kolejka nie pilnuje dat."],
        "Nagłówek podsumowania podaje czas od startu do końca."))

    # 9. Plan i historia
    S.append(screens_slide("Etap 4 · plan i historia", "Plan i historia po pierwszym treningu",
        [(shots["plan"], "Cykl po pierwszym treningu"),
         (shots["historia"], "Historia z wykresami")],
        ["Odhaczone dni są przygaszone, odpoczynek celowo szary, następny trening ma własną kartę ze Startem.",
         "Historia zlicza serie, powtórzenia i czas w ruchu; filtry 7 dni, 30 dni, wszystko oraz ukończone i przerwane.",
         "Wykres wolumenu tygodniowego i siatka aktywności z dwunastu tygodni rysowane własnym kodem, bez bibliotek.",
         "Każdy wpis niesie poziom i RPE. Historia ma kopię zapasową do pliku JSON."],
        "Historia jest lokalna: bez konta, bez sieci, z eksportem do pliku."))

    # 10. D-34
    e = []
    header(e, "Zmiana z 8 września", "Co zmieniło D-34: objętość z poziomu, czas z liczby ćwiczeń")
    e.append(rect(0.6, 1.8, 6.3, 2.2, fill=CARD, radius=0.18))
    e.append(text(0.9, 1.95, 5.8, 0.4, "BYŁO", 11.5, EMBER, bold=True, spacing=2))
    e.append(bullets(0.9, 2.35, 5.8, 1.8, ["Dwa ćwiczenia po sześć i siedem rund: 48 pompek i 77 mostków pierwszego dnia",
                                           "Dłuższe sesje do ośmiu rund i ponad sto powtórzeń jednego ćwiczenia",
                                           "Wzrost i waga po jednym kroku na tapnięcie"], size=13.5, gap=6))
    e.append(rect(0.6, 4.3, 6.3, 2.25, fill=CARD, radius=0.18))
    e.append(text(0.9, 4.45, 5.8, 0.4, "JEST", 11.5, EMBER, bold=True, spacing=2))
    e.append(bullets(0.9, 4.85, 5.8, 1.6, ["Rundy i serie w widełkach poziomu: początkujący 2 do 3, średniozaawansowany 3 do 4, zaawansowany 3 do 5",
                                          "Czas wypełniają kolejne ćwiczenia (do 16, w blokach po 6) i przerwy; 9 ćwiczeń po 2 rundy na 15 min",
                                          "Pasek z uchwytem, przytrzymanie klawisza, długość sesji ograniczona poziomem"], size=13.5, gap=6))
    phone_row(e, [(shots["stepper"], "Wzrost i waga z przeciągnięcia"),
                  (shots["minuty"], "Poziom 1 przycina 75 do 45 min")], 1.8, 4.75, 7.3, 12.9)
    S.append(dict(elems=e, notes="Sprawdzone na całej siatce ustawień z prawdziwym seedem: zero chybień czasu, "
                  "najwięcej powtórzeń jednego ćwiczenia w treningu to 130 u zaawansowanego."))

    # 11. Stan i co dalej
    e = []
    header(e, "Stan po sprawdzeniu", "Co jest sprawdzone i co dalej")
    stats = [("168", "testów jednostkowych, zero błędów"), ("18", "serii w playerze bez pomyłki automatu"), ("15 min", "trening w realnym tempie, jak w planie")]
    x = 0.6
    for big, label in stats:
        e.append(rect(x, 1.8, 2.75, 1.7, fill=CARD, radius=0.18))
        e.append(text(x + 0.25, 1.9, 2.3, 0.95, big, 40, FIRE, bold=True, anchor="middle"))
        e.append(text(x + 0.25, 2.85, 2.3, 0.6, label, 12, MUTED))
        x += 2.95
    e.append(text(0.6, 3.85, 4.0, 0.4, "ZROBIONE", 11.5, EMBER, bold=True, spacing=2))
    e.append(bullets(0.6, 4.25, 4.0, 2.7, ["Build i testy zielone lokalnie i na CI", "Przebieg na emulatorze od pustej aplikacji do historii",
                                          "Przeciąganie, przytrzymanie i limit minut sprawdzone na ekranie"], size=13.5, gap=6))
    e.append(text(4.85, 3.85, 4.1, 0.4, "DO ZROBIENIA", 11.5, EMBER, bold=True, spacing=2))
    e.append(bullets(4.85, 4.25, 4.1, 2.7, ["Test na telefonie: czy 9 ćwiczeń po 2 rundy to trening, który da się zrobić",
                                           "Seed bez sprzętu nie ma ćwiczenia na plecy; kandydaci czekają na zdjęcia",
                                           "Scalenie do master i wydanie na stronę projektu"], size=13.5, gap=6))
    phone(e, shots["historia"], 10.1, 1.8, 4.85, "Historia po pierwszym treningu")
    S.append(dict(elems=e, notes="Kolejny krok to nowy cykl na telefonie z najniższymi ustawieniami i ocena, czy plan wygląda na wykonalny."))
    return S


# ---- PPTX --------------------------------------------------------------------------------------

def rgb(h):
    return RGBColor.from_string(h)


def set_bullet(p, color):
    pPr = p._p.get_or_add_pPr()
    pPr.set("marL", str(int(Inches(0.22))))
    pPr.set("indent", str(-int(Inches(0.22))))
    for tag in ("a:buNone", "a:buChar", "a:buAutoNum"):
        for el in pPr.findall(qn(tag)):
            pPr.remove(el)
    buClr = etree.SubElement(pPr, qn("a:buClr"))
    etree.SubElement(buClr, qn("a:srgbClr")).set("val", color)
    etree.SubElement(pPr, qn("a:buChar")).set("char", "•")


def add_text(slide, el):
    box = slide.shapes.add_textbox(Inches(el["x"]), Inches(el["y"]), Inches(el["w"]), Inches(el["h"]))
    tf = box.text_frame
    tf.word_wrap = True
    tf.margin_left = tf.margin_right = tf.margin_top = tf.margin_bottom = 0
    tf.vertical_anchor = {"top": MSO_ANCHOR.TOP, "middle": MSO_ANCHOR.MIDDLE, "bottom": MSO_ANCHOR.BOTTOM}[el["anchor"]]
    p = tf.paragraphs[0]
    p.alignment = {"left": PP_ALIGN.LEFT, "center": PP_ALIGN.CENTER, "right": PP_ALIGN.RIGHT}[el["align"]]
    p.line_spacing = 1.08
    r = p.add_run()
    r.text = el["s"]
    f = r.font
    f.name, f.size, f.bold, f.color.rgb = FONT, Pt(el["size"]), el["bold"], rgb(el["color"])
    if el.get("spacing"):
        r._r.get_or_add_rPr().set("spc", str(int(el["spacing"] * 100)))


def add_bullets(slide, el):
    box = slide.shapes.add_textbox(Inches(el["x"]), Inches(el["y"]), Inches(el["w"]), Inches(el["h"]))
    tf = box.text_frame
    tf.word_wrap = True
    tf.margin_left = tf.margin_right = tf.margin_top = tf.margin_bottom = 0
    for i, item in enumerate(el["items"]):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.line_spacing = 1.1
        p.space_after = Pt(el["gap"])
        set_bullet(p, FIRE)
        r = p.add_run()
        r.text = item
        r.font.name, r.font.size, r.font.color.rgb = FONT, Pt(el["size"]), rgb(el["color"])


def add_shape(slide, el, kind):
    sh = slide.shapes.add_shape(kind, Inches(el["x"]), Inches(el["y"]), Inches(el["w"]), Inches(el["h"]))
    sh.shadow.inherit = False
    if el.get("fill"):
        sh.fill.solid()
        sh.fill.fore_color.rgb = rgb(el["fill"])
    else:
        sh.fill.background()
    if el.get("line"):
        sh.line.color.rgb = rgb(el["line"])
        sh.line.width = Pt(el.get("line_w", 1.0))
    else:
        sh.line.fill.background()
    if kind == MSO_SHAPE.ROUNDED_RECTANGLE:
        sh.adjustments[0] = min(0.5, el["radius"] / min(el["w"], el["h"]))
    sh.text_frame.text = ""


def write_pptx(slides, path):
    prs = Presentation()
    prs.slide_width, prs.slide_height = Inches(W), Inches(H)
    blank = prs.slide_layouts[6]
    for s in slides:
        slide = prs.slides.add_slide(blank)
        slide.background.fill.solid()
        slide.background.fill.fore_color.rgb = rgb(BG)
        for el in s["elems"]:
            t = el["t"]
            if t == "text":
                add_text(slide, el)
            elif t == "bullets":
                add_bullets(slide, el)
            elif t == "rect":
                add_shape(slide, el, MSO_SHAPE.ROUNDED_RECTANGLE if el["radius"] else MSO_SHAPE.RECTANGLE)
            elif t == "oval":
                add_shape(slide, el, MSO_SHAPE.OVAL)
            elif t == "img":
                slide.shapes.add_picture(el["path"], Inches(el["x"]), Inches(el["y"]), Inches(el["w"]), Inches(el["h"]))
        slide.notes_slide.notes_text_frame.text = s["notes"]
    prs.save(path)


# ---- podgląd Pillow ----------------------------------------------------------------------------

def px(v):
    return int(round(v * SCALE))


def font(size, bold):
    return ImageFont.truetype(FONT_FILES[bold], int(round(size * SCALE / 72)))


def wrap(draw, s, f, width_px):
    lines = []
    for para in s.split("\n"):
        words, line = para.split(), ""
        for w in words:
            cand = (line + " " + w).strip()
            if draw.textlength(cand, font=f) <= width_px or not line:
                line = cand
            else:
                lines.append(line)
                line = w
        lines.append(line)
    return lines


def draw_text(draw, el):
    f = font(el["size"], el["bold"])
    lines = wrap(draw, el["s"], f, px(el["w"]))
    lh = el["size"] * SCALE / 72 * 1.2
    total = lh * len(lines)
    y = px(el["y"]) + {"top": 0, "middle": (px(el["h"]) - total) / 2, "bottom": px(el["h"]) - total}[el["anchor"]]
    overflow = total > px(el["h"]) + 2
    for ln in lines:
        wpx = draw.textlength(ln, font=f)
        x = px(el["x"]) + {"left": 0, "center": (px(el["w"]) - wpx) / 2, "right": px(el["w"]) - wpx}[el["align"]]
        draw.text((x, y), ln, font=f, fill="#" + el["color"])
        y += lh
    return overflow


def draw_bullets(draw, el):
    f = font(el["size"], False)
    lh = el["size"] * SCALE / 72 * 1.15
    y = px(el["y"])
    indent = px(0.22)
    for item in el["items"]:
        draw.text((px(el["x"]), y), "•", font=f, fill="#" + FIRE)
        for ln in wrap(draw, item, f, px(el["w"]) - indent):
            draw.text((px(el["x"]) + indent, y), ln, font=f, fill="#" + el["color"])
            y += lh
        y += el["gap"] * SCALE / 72
    return y > px(el["y"] + el["h"]) + 2


def render_png(s, path):
    im = Image.new("RGB", (px(W), px(H)), "#" + BG)
    draw = ImageDraw.Draw(im)
    problems = []
    for el in s["elems"]:
        t = el["t"]
        box = [px(el["x"]), px(el["y"]), px(el["x"] + el["w"]), px(el["y"] + el["h"])]
        if t == "rect":
            draw.rounded_rectangle(box, radius=px(el["radius"]), fill="#" + el["fill"] if el.get("fill") else None,
                                   outline="#" + el["line"] if el.get("line") else None, width=2 if el.get("line") else 0)
        elif t == "oval":
            draw.ellipse(box, fill="#" + el["fill"])
        elif t == "img":
            pic = Image.open(el["path"]).convert("RGB").resize((px(el["w"]), px(el["h"])), Image.LANCZOS)
            im.paste(pic, (box[0], box[1]))
        elif t == "text":
            if draw_text(draw, el):
                problems.append(f"tekst wychodzi z ramki: {el['s'][:40]!r}")
        elif t == "bullets":
            if draw_bullets(draw, el):
                problems.append(f"punkty wychodzą z ramki: {el['items'][0][:40]!r}")
        if el["x"] < 0 or el["y"] < 0 or el["x"] + el["w"] > W + 0.01 or el["y"] + el["h"] > H + 0.01:
            problems.append(f"element poza slajdem: {t} {el['x']:.2f},{el['y']:.2f} {el['w']:.2f}x{el['h']:.2f}")
    im.save(path)
    return im, problems


def main():
    os.makedirs(OUT, exist_ok=True)
    shots = {
        "dzis-bez-planu": shot("01-dzis-bez-planu.png"), "kreator-1": shot("02-kreator-1-sylwetka.png"),
        "kreator-2": shot("03-kreator-2-cel.png"), "kreator-3": shot("04-kreator-3-czas.png"), "kreator-4": shot("05-kreator-4-sprzet.png"),
        "plan-po-kreatorze": shot("06-plan-po-kreatorze.png"), "dzis-plan": shot("07-dzis-plan.png"), "trening": shot("08-trening.png"),
        "odliczanie": shot("09-player-odliczanie.png"), "seria-01": shot("*-player-seria-01.png"), "przerwa-02": shot("*-player-przerwa-przed-02.png"),
        "seria-10": shot("*-player-seria-10-na-czas.png"), "przerwa-06": shot("*-player-przerwa-przed-06.png"),
        "przerwa-11": shot("*-player-przerwa-przed-11.png"), "seria-18": shot("*-player-seria-18.png"),
        "podsumowanie": shot("*-podsumowanie.png"), "podsumowanie-rpe": shot("*-podsumowanie-rpe.png"),
        "dzis-po-treningu": shot("*-dzis-po-treningu.png"), "plan": shot("*-plan.png"), "historia": shot("*-historia.png"),
        "stepper": shot("30-stepper-przeciagniecie.png", EXTRA), "minuty": shot("31-stepper-minuty-po-zmianie-poziomu.png", EXTRA),
    }
    slides = build(shots)
    write_pptx(slides, os.path.join(OUT, NAME + ".pptx"))
    pages = []
    for i, s in enumerate(slides, 1):
        im, problems = render_png(s, os.path.join(SCRATCH, f"podglad-{i:02d}.png"))
        pages.append(im)
        for p in problems:
            print(f"slajd {i}: {p}")
    pages[0].save(os.path.join(OUT, NAME + ".pdf"), save_all=True, append_images=pages[1:], resolution=SCALE)
    print(f"{len(slides)} slajdów -> {OUT}/{NAME}.pptx i .pdf, podgląd w {SCRATCH}/podglad-NN.png")


if __name__ == "__main__":
    main()
