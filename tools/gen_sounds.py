"""Generates the game's sound effects into app/src/main/res/raw (16-bit mono WAV).

Synthesized so the repo has no third-party audio licensing questions.
Replace with designed sounds later if wanted; keep the same file names.
Run: python3 tools/gen_sounds.py
"""
import math, os, random, struct, wave

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")


def write(name, samples):
    peak = max(1e-9, max(abs(s) for s in samples))
    gain = 0.85 / peak
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(s * gain * 32767)) for s in samples))


def env(t, attack, decay):
    return min(1.0, t / attack) * math.exp(-t / decay) if attack > 0 else math.exp(-t / decay)


def tone(freqs, dur, attack=0.004, decay=0.15, partials=(1.0, 0.3, 0.12)):
    n = int(RATE * dur)
    out = []
    for i in range(n):
        t = i / RATE
        s = 0.0
        for f in freqs:
            for k, a in enumerate(partials, start=1):
                s += a * math.sin(2 * math.pi * f * k * t)
        out.append(s * env(t, attack, decay))
    return out


def sweep(f0, f1, dur, decay):
    n = int(RATE * dur)
    out, ph = [], 0.0
    for i in range(n):
        t = i / RATE
        f = f0 + (f1 - f0) * (i / n)
        ph += 2 * math.pi * f / RATE
        out.append(math.sin(ph) * env(t, 0.003, decay))
    return out


def noise(dur, decay, lowpass=0.15, seed=1):
    random.seed(seed)
    n = int(RATE * dur)
    out, y = [], 0.0
    for i in range(n):
        y += lowpass * (random.uniform(-1, 1) - y)
        out.append(y * env(i / RATE, 0.002, decay))
    return out


def mix(*tracks):
    n = max(len(t) for t in tracks)
    return [sum(t[i] for t in tracks if i < len(t)) for i in range(n)]


def delay(track, seconds):
    return [0.0] * int(RATE * seconds) + track


os.makedirs(OUT, exist_ok=True)
write("sfx_pick", sweep(500, 900, 0.06, 0.03))
write("sfx_drop", mix(tone([220], 0.12, decay=0.04, partials=(1.0, 0.5)), noise(0.05, 0.01, 0.5)))
# Pop: bright bell chord; pitch is raised at playback for streaks.
write("sfx_pop", tone([659.25, 987.77], 0.6, decay=0.18, partials=(1.0, 0.4, 0.2, 0.1)))
write("sfx_invalid", tone([110, 116], 0.18, decay=0.08, partials=(1.0, 0.6)))
write("sfx_bomb", mix(noise(0.7, 0.2, 0.08, seed=3), sweep(160, 40, 0.5, 0.2)))
write("sfx_over", mix(tone([392], 0.5, decay=0.25), delay(tone([329.63], 0.5, decay=0.25), 0.18),
                      delay(tone([261.63], 0.9, decay=0.4), 0.36)))
write("sfx_win", mix(tone([523.25], 0.4, decay=0.2), delay(tone([659.25], 0.4, decay=0.2), 0.1),
                     delay(tone([783.99], 0.4, decay=0.2), 0.2), delay(tone([1046.5], 0.8, decay=0.35), 0.3)))
write("sfx_tick", tone([1200], 0.05, decay=0.015, partials=(1.0,)))
write("sfx_spawn", sweep(700, 350, 0.08, 0.04))
print("ok")
