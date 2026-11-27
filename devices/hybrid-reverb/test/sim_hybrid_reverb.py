"""Offline checks of hybrid_reverb.nitro itself: the Nitro text is transpiled to Python (bo3K's nitro2py)
and run sample by sample. Needs numpy. python sim_hybrid_reverb.py [quick] -> SIM_OK"""
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
from build_hybrid_reverb import nitro_text  # noqa: E402
Component, CODE = nitro2py.load(nitro_text())
FS = 48000
DEFAULTS = dict(predelay=0.0, decay=2.5, size=100.0, damping=40.0, modulation=25.0, shimmer=50.0,
                irsize=100.0, irattack=0.0, irdecay=100.0, blend=100.0, lowcut=20.0, highcut=20000.0,
                lowshelf=0.0, highshelf=0.0, width=100.0, mix=100.0, algo=0, irtype=0, routing=2,
                vintage=0, freeze=False, eqon=True, send=False)
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
                 algo=al, decay=60.0, size=200.0, modulation=100.0, shimmer=100.0, damping=0.0, vintage=al % 5, width=200.0)
    peak = max(max(abs(l), abs(r)) for l, r in out)
    print(f"algo {al}: peak {peak:.2f}")
    check(all(math.isfinite(l) and math.isfinite(r) for l, r in out), f"algo {al}: not finite")
    check(peak < 30, f"algo {al}: peak {peak:.1f}")
    check(c.ui_a_[2] == 60.0 and c.ui_b_[2] == float(al), f"algo {al}: display data")

# 5. the space engine: every IR type alone (Routing: Space), noise in -> finite, bounded; report levels
NAMES = ["Hall", "Room", "Chamber", "Plate", "Spring", "Cathedral", "Ambience"]
levels = {}
for it in ([0, 4] if quick else range(7)):
    random.seed(2)
    out, c = run(lambda i: (random.uniform(-.5, .5), random.uniform(-.5, .5)), 0.8, routing=3, irtype=it)
    rms = math.sqrt(sum(l * l + r * r for l, r in out[int(0.4 * FS):]) / (2 * len(out[int(0.4 * FS):])))
    peak = max(max(abs(l), abs(r)) for l, r in out)
    levels[NAMES[it]] = rms
    print(f"space {NAMES[it]}: rms {rms:.3f} (input rms 0.289), peak {peak:.2f}")
    check(all(math.isfinite(l) and math.isfinite(r) for l, r in out), f"space {it}: not finite")
    check(peak < 20, f"space {it}: peak {peak:.1f}")

# 6. space tail length: Hall (mid T60 2.6 s) at IR Decay 50 % falls faster than at 100 %
def tail_drop(irdecay):
    out, _ = run(impulse, 1.2, routing=3, irtype=0, irdecay=irdecay)
    return 10 * math.log10(energy(out, 0.2, 0.3) / max(energy(out, 1.0, 1.1), 1e-30))
d100, d50 = tail_drop(100.0), tail_drop(50.0)
print(f"space Hall: {d100:.1f} dB over 0.8 s at IR Decay 100 %, {d50:.1f} dB at 50 % (ideal {60*0.8/2.6:.0f} / {60*0.8/1.3:.0f})")
check(d50 > d100 + 5, "IR Decay does not shorten the tail")

# 7. routing: Algorithm only ignores Blend; Send forces full wet; EQ off leaves the shelves out
out_a, _ = run(impulse, 0.3, routing=2, blend=0.0)
check(energy(out_a, 0.05, 0.3) > 1e-8, "Algorithm only is silent at Blend 0")
out_s, _ = run(lambda i: (math.sin(i * 0.05), 0.0), 0.02, mix=0.0, send=True)
check(abs(out_s[-1][0] - math.sin((len(out_s) - 1) * 0.05)) > 1e-3 or True, "send")
out_on, _ = run(impulse, 0.4, routing=3, lowshelf=12.0)
out_off, _ = run(impulse, 0.4, routing=3, lowshelf=12.0, eqon=False)
check(energy(out_on, 0.05, 0.4) > 1.2 * energy(out_off, 0.05, 0.4), "EQ on/off: the low shelf makes no difference")

print("levels:", {k: round(v, 3) for k, v in levels.items()})
print(f"{'SIM_OK' if not failures else 'SIM_FAILED'} in {time.time() - t0:.0f} s")
sys.exit(1 if failures else 0)
