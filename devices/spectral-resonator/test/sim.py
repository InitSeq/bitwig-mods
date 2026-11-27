"""Offline checks of spectral_resonator.nitro: the Nitro text is transpiled to Python (bo3K's nitro2py) and run
sample by sample. python3 sim.py (needs numpy)"""
import math
import os
import random
import sys
import time
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
DEV = HERE.parent
VENDOR = Path(os.environ.get("CF_VENDOR", DEV / "vendor"))
sys.path.insert(0, str(VENDOR / "bitwig-nitro-tools" / "src"))
sys.path.insert(0, str(VENDOR / "bo-bitwig" / "tools" / "device-gen"))
sys.path.insert(0, str(VENDOR / "bo-bitwig" / "devices" / "bo-lufs" / "source" / "lufs"))
import nitro2py  # noqa: E402

Component, CODE = nitro2py.load((DEV / "spectral_resonator.nitro").read_text())
FS = 48000
DEFAULTS = dict(freq=220.0, note=57.0, decay=1.5, hfdamp=40.0, lfdamp=10.0, stretch=0.0, shift=0.0, oddeven=0.0,
                unison=0.0, modamt=40.0, modrate=1.0, drive=0.0, mix=100.0, pmode=0, chord=0, modtype=0, hold=False)
failures = []


def check(ok, msg):
    if not ok:
        failures.append(msg)
        print("FAIL", msg)


def run(signal_fn, seconds, changes=None, **params):
    c = Component(FS)
    c.reset()
    for k, v in dict(DEFAULTS, **params).items():
        setattr(c, k + "_", v)
    n = int(seconds * FS)
    out = np.zeros((n, 2))
    for i in range(n):
        if changes and i in changes:
            for k, v in changes[i].items():
                setattr(c, k + "_", v)
        s = signal_fn(i)
        c.in_ = [s[0], s[1], 0.0, 0.0]
        c.process()
        out[i] = c.out_[0], c.out_[1]
    return out, c


def rms(x):
    return float(np.sqrt(np.mean(x ** 2)))


def tonal(x, f0, maxh=32):
    """share of the spectrum's energy within 2 % of the partials h * f0"""
    spec = np.abs(np.fft.rfft(x * np.hanning(len(x)))) ** 2
    f = np.fft.rfftfreq(len(x), 1 / FS)
    near = np.zeros_like(f, dtype=bool)
    for h in range(1, maxh + 1):
        near |= np.abs(f - h * f0) < max(3.0, 0.02 * f0)
    return float(spec[near].sum() / spec[f > 15].sum())


def peak_hz(x, lo=20, hi=20000):
    spec = np.abs(np.fft.rfft(x * np.hanning(len(x))))
    f = np.fft.rfftfreq(len(x), 1 / FS)
    m = (f > lo) & (f < hi)
    return f[m][np.argmax(spec[m])], spec, f


random.seed(1)
noise = [(random.uniform(-.5, .5), random.uniform(-.5, .5)) for _ in range(FS * 2)]
t0 = time.time()

# 1. noise in rings at the partials: strongest peak at the fundamental or a harmonic of 220 Hz
out, _ = run(lambda i: noise[i], 0.8)
f_pk, spec, f = peak_hz(out[int(0.3 * FS):, 0])
ratio = f_pk / 220.0
print(f"noise -> peak at {f_pk:.1f} Hz (x{ratio:.2f} of 220), wet rms {rms(out[int(.3 * FS):]):.3f} "
      f"vs in {rms(np.array(noise[:FS])):.3f}")
tn = tonal(out[int(0.3 * FS):, 0], 220.0)
print(f"   energy on the 220 Hz partials: {tn * 100:.0f} %")
check(tn > 0.7 and abs(ratio - round(ratio)) < 0.03, "noise: not ringing on the 220 Hz partials")
check(0.03 < rms(out[int(.3 * FS):]) < 1.5, "noise: wet level off")

# 2. note mode: note 69 -> partials of 440 Hz; shift +12 -> of 880 Hz
out, _ = run(lambda i: noise[i], 0.6, pmode=1, note=69.0, hfdamp=90.0)
f_pk, _, _ = peak_hz(out[int(0.3 * FS):, 0])
print(f"note 69 -> peak {f_pk:.1f} Hz")
check(tonal(out[int(0.3 * FS):, 0], 440.0) > 0.6, "note mode: not on 440 Hz partials")
out, _ = run(lambda i: noise[i], 0.6, pmode=1, note=69.0, shift=12.0, hfdamp=90.0)
f_pk, _, _ = peak_hz(out[int(0.3 * FS):, 0])
print(f"note 69 shift +12 -> peak {f_pk:.1f} Hz")
check(tonal(out[int(0.3 * FS):, 0], 880.0) > 0.6 and tonal(out[int(0.3 * FS):, 0], 440.0, 1) < 0.05,
      "shift: not on 880 Hz partials")

# 3. decay: an impulse rings about as long as Decay (energy drop over 0.5 s with decay 1 s, little damping)
out, _ = run(lambda i: (1.0, 1.0) if i == 0 else (0.0, 0.0), 1.0, decay=1.0, hfdamp=0.0, lfdamp=0.0)
e1 = rms(out[int(.1 * FS):int(.2 * FS)])
e2 = rms(out[int(.6 * FS):int(.7 * FS)])
drop = 20 * math.log10(e1 / max(e2, 1e-30))
print(f"decay 1 s: {drop:.1f} dB over 0.5 s (ideal 30)")
check(20 < drop < 40, f"decay: {drop:.1f} dB")

# 4. mix 0 is dry
out, _ = run(lambda i: (math.sin(i * .05), math.cos(i * .03)), 0.05, mix=0.0)
check(max(abs(out[i, 0] - math.sin(i * .05)) for i in range(len(out))) < 1e-4, "mix 0 is not dry")

# 5. hold keeps ringing after the input stops
out, _ = run(lambda i: noise[i] if i < int(.3 * FS) else (0.0, 0.0), 1.5, changes={int(.25 * FS): {"hold": True}},
             decay=0.5)
ha, hb = rms(out[int(.4 * FS):int(.5 * FS)]), rms(out[int(1.3 * FS):int(1.4 * FS)])
print(f"hold: {20 * math.log10(ha / max(hb, 1e-30)):.1f} dB lost over 0.9 s (level {ha:.3f})")
check(ha > 0.01 and abs(20 * math.log10(ha / max(hb, 1e-30))) < 3, "hold does not hold")

# 6. extremes: every chord and mod type, loud noise, long decay, no damping: finite and bounded
for ch in range(6):
    mt = ch % 4
    out, c = run(lambda i: (noise[i][0] * 2, noise[i][1] * 2), 0.4, chord=ch, modtype=mt, modamt=100.0,
                 modrate=20.0, decay=30.0, hfdamp=0.0, lfdamp=0.0, stretch=100.0 if ch % 2 else -100.0,
                 unison=100.0, drive=24.0, freq=20.0 + ch * 900)
    pk = float(np.max(np.abs(out)))
    print(f"chord {ch} mod {mt}: peak {pk:.2f}")
    check(np.all(np.isfinite(out)), f"chord {ch}: not finite")
    check(pk < 2.5, f"chord {ch}: peak {pk:.2f}")
    check(c.ui_b_[1] == float(ch), "display data")

print(f"{'SIM_OK' if not failures else 'SIM_FAILED'} in {time.time() - t0:.0f} s")
sys.exit(1 if failures else 0)
