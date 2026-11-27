"""Offline checks of kiln.nitro itself: the Nitro text is transpiled to Python (bo3K's nitro2py)
and run sample by sample. Needs numpy. python sim_kiln.py [quick] -> SIM_OK"""
import math
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
VENDOR = Path(__import__("os").environ.get("KILN_VENDOR", HERE.parent / "vendor"))
sys.path.insert(0, str(VENDOR / "bitwig-nitro-tools" / "src"))
sys.path.insert(0, str(VENDOR / "bo-bitwig" / "tools" / "device-gen"))
sys.path.insert(0, str(VENDOR / "bo-bitwig" / "devices" / "bo-lufs" / "source" / "lufs"))  # nitro2py (needs numpy)
import nitro2py  # noqa: E402

sys.path.insert(0, str(HERE.parent))
from build_kiln import nitro_text  # noqa: E402
Component, CODE = nitro2py.load(nitro_text())
FS = 48000
DEFAULTS = dict(predelay=0.0, decay=2.5, size=100.0, damping=40.0, mod=25.0, shimmer=50.0,
                lowcut=20.0, highcut=20000.0, width=100.0, mix=100.0, algo=0, vintage=0, freeze=False)
failures = []


def check(ok, msg):
    if not ok:
        failures.append(msg)
        print("FAIL", msg)


def run(signal_fn, seconds, changes=None, **params):
    c = Component(FS)
    c.reset()
    p = dict(DEFAULTS, **params)
    for k, v in p.items():
        setattr(c, k + "_", v)
    out = []
    n = int(seconds * FS)
    for i in range(n):
        if changes and i in changes:
            for k, v in changes[i].items():
                setattr(c, k + "_", v)
        s = signal_fn(i)
        c.in_ = [s[0], s[1], 0.0, 0.0]
        c.process()
        out.append((c.out_[0], c.out_[1]))
    return out, c


def impulse(i):
    return (1.0, 1.0) if i == 0 else (0.0, 0.0)


def energy(out, a, b):
    return sum(l * l + r * r for l, r in out[int(a * FS):int(b * FS)]) / max(1, int((b - a) * FS))


t0 = time.time()
quick = len(sys.argv) > 1

# 1. decay time: energy falls 60 dB over about the chosen decay (measured between 0.3 s and 1.3 s)
out, _ = run(impulse, 1.4, decay=1.0)
check(all(math.isfinite(l) and math.isfinite(r) for l, r in out), "decay: not finite")
e1, e2 = energy(out, 0.3, 0.4), energy(out, 1.2, 1.3)
drop = 10 * math.log10(e1 / max(e2, 1e-30))
print(f"decay 1.0 s: {drop:.1f} dB over 0.9 s (ideal {60 * 0.9:.0f} dB)")
check(35 < drop < 75, f"decay: {drop:.1f} dB over 0.9 s")

# 2. mix 0 passes the dry signal unchanged; predelay delays the wet onset
out, _ = run(lambda i: (math.sin(i * 0.05), math.cos(i * 0.03)), 0.05, mix=0.0)
check(max(abs(l - math.sin(i * 0.05)) for i, (l, r) in enumerate(out)) < 1e-4, "mix 0 is not dry")
out, _ = run(impulse, 0.2, predelay=100.0, damping=0.0)
first = next(i for i, (l, r) in enumerate(out) if abs(l) + abs(r) > 1e-4)
print(f"predelay 100 ms: first wet sample at {first / FS * 1000:.1f} ms")
check(first / FS > 0.095, "predelay: wet starts too early")

# 3. freeze holds the tail: energy stays within a few dB for a second
out, _ = run(impulse, 1.6, changes={int(0.2 * FS): {"freeze": True}}, decay=2.0)
ea, eb = energy(out, 0.5, 0.6), energy(out, 1.4, 1.5)
hold = 10 * math.log10(ea / max(eb, 1e-30))
print(f"freeze: {hold:.1f} dB lost from 0.5 s to 1.4 s")
check(abs(hold) < 6, f"freeze does not hold: {hold:.1f} dB")

# 4. every algorithm, extreme settings, loud noise in: finite and bounded
import random
random.seed(1)
for al in ([2] if quick else range(5)):
    out, c = run(lambda i: (random.uniform(-1, 1), random.uniform(-1, 1)), 0.6,
                 algo=al, decay=60.0, size=200.0, mod=100.0, shimmer=100.0, damping=0.0, vintage=al % 5, width=200.0)
    peak = max(max(abs(l), abs(r)) for l, r in out)
    print(f"algo {al}: peak {peak:.2f}")
    check(all(math.isfinite(l) and math.isfinite(r) for l, r in out), f"algo {al}: not finite")
    check(peak < 30, f"algo {al}: peak {peak:.1f}")
    check(c.ui_a_[2] == 60.0 and c.ui_b_[2] == float(al), f"algo {al}: display data")

print(f"{'SIM_OK' if not failures else 'SIM_FAILED'} in {time.time() - t0:.0f} s")
sys.exit(1 if failures else 0)
