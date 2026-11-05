#!/usr/bin/env python3
"""Generuje krótkie próbki dźwiękowe playera do app/src/main/res/raw/ (M2: SoundPool zamiast ToneGenerator).

Uruchom: python tools/build_beeps.py
Wynik: beep_short.wav (odliczanie 3-2-1) i beep_long.wav (zmiana kroku). Mono, 16 bit, 44,1 kHz, sinus z krótkim fade.
Biblioteka standardowa, bez zależności.
"""
import math
import struct
import wave
from pathlib import Path

RATE = 44_100
OUT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "raw"


def tone(freq_hz: float, ms: int, gain: float = 0.6, fade_ms: int = 8) -> bytes:
    n = RATE * ms // 1000
    fade = RATE * fade_ms // 1000
    frames = []
    for i in range(n):
        env = 1.0
        if i < fade:
            env = i / fade
        elif i > n - fade:
            env = (n - i) / fade
        sample = gain * env * math.sin(2 * math.pi * freq_hz * i / RATE)
        frames.append(struct.pack("<h", int(sample * 32767)))
    return b"".join(frames)


def write(name: str, data: bytes) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / name
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(data)
    print(f"{path.relative_to(OUT.parent.parent.parent.parent.parent)}: {len(data) // 2} próbek")


if __name__ == "__main__":
    write("beep_short.wav", tone(1000, 120))
    # dłuższy sygnał: dwa tony, wyraźnie inny od odliczania
    write("beep_long.wav", tone(660, 220) + tone(880, 230))
