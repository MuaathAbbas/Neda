#!/usr/bin/env python3
"""Generate the bundled alarm sample. This is a short tone, not a recording."""

import math
import struct
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app" / "src" / "main" / "res" / "raw" / "alarm_sample.wav"


def main() -> None:
    framerate = 44100
    duration = 3.6
    count = int(framerate * duration)
    notes = (
        (0.00, 0.28, 523.25),
        (0.32, 0.60, 659.25),
        (0.64, 1.05, 783.99),
        (1.20, 1.48, 523.25),
        (1.52, 1.80, 659.25),
        (1.84, 2.40, 987.77),
        (2.55, 2.83, 783.99),
        (2.87, 3.50, 1046.50),
    )
    frames = bytearray()
    for index in range(count):
        t = index / framerate
        sample = 0.0
        for start, end, freq in notes:
            if start <= t < end:
                local = t - start
                attack = min(1.0, local / 0.02)
                release = min(1.0, (end - t) / 0.04)
                sample += math.sin(2 * math.pi * freq * t) * attack * release
        sample = max(-1.0, min(1.0, sample * 0.45))
        frames += struct.pack("<h", int(sample * 32767))

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUTPUT), "w") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(framerate)
        handle.writeframes(frames)


if __name__ == "__main__":
    main()
