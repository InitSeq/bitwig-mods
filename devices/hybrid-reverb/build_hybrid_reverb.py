"""Build "Hybrid Reverb.bwdevice" with bo3K's device generator.
python3 build_hybrid_reverb.py          -> out/Hybrid Reverb.bwdevice (and a copy next to this script)
python3 build_hybrid_reverb.py --probe  -> also two diagnostic variants (smaller buffers, with and without display)

Needs Python 3.10+ and two MIT-licensed checkouts, in KILN_VENDOR (default: ./vendor):
  vendor/bo-bitwig            https://github.com/bo3K/bo-bitwig (the device generator)
  vendor/bitwig-nitro-tools   https://github.com/blakebratcher/bitwig-nitro-tools (its Nitro serializer)
KILN_DEVICES: a Patchbay device folder to copy the built devices into (optional)."""
import os
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
VENDOR = Path(os.environ.get("KILN_VENDOR", HERE / "vendor"))
sys.path.insert(0, str(VENDOR / "bitwig-nitro-tools" / "src"))
sys.path.insert(0, str(VENDOR / "bo-bitwig" / "tools" / "device-gen"))

from bwgen import Device, Domain, Origin, ButtonStyle  # noqa: E402

VERSION = 6  # metadata revision_no: raise it with each released change

# delay buffer sizes in samples: lines, diffuser stages, early reflections and predelay, shifter
FULL = dict(LN=32768, DF=8192, ER=16384, SH=4096, TP=32768)
SMALL = dict(LN=8192, DF=2048, ER=4096, SH=2048, TP=8192)


def nitro_text(sizes=FULL) -> str:
    """hybrid_reverb.nitro with its buffer sizes filled in."""
    tokens = {"@LN8@": 8 * sizes["LN"], "@LNM@": f'{sizes["LN"] - 8}.0', "@LN@": sizes["LN"],
              "@DF8@": 8 * sizes["DF"], "@DFM@": f'{sizes["DF"] - 8}.0', "@DF@": sizes["DF"],
              "@ER2@": 2 * sizes["ER"], "@ERM@": f'{sizes["ER"] - 8}.0', "@ER@": sizes["ER"],
              "@SHM@": f'{sizes["SH"] - 8}.0', "@SHW@": f'{sizes["SH"] - 96}.0', "@SH@": sizes["SH"],
              "@TP2@": 2 * sizes["TP"], "@TPM@": f'{sizes["TP"] - 8}.0', "@TP@": sizes["TP"]}
    text = (HERE / "hybrid_reverb.nitro").read_text()
    for token, value in tokens.items():
        text = text.replace(token, str(value))
    assert "@" not in text, "a size token was not filled in"
    return text


# layout in grid units (1 unit = 4 px): panel, display, and the sections (x, width) right of it
PANEL = (236, 53)
DISPLAY = (3, 3, 74, 42)
SECTIONS = {"algorithm": (80, 41), "space": (122, 41), "output": (164, 41), "tone": (206, 28)}


def hybrid_reverb(name="Hybrid Reverb", uuid_key="coldforge/kiln", sizes=FULL, display=True) -> Device:
    # uuid_key stays "coldforge/kiln" (only the ID is made from it): projects made with Kiln keep finding the device
    dev = Device(
        name=name,
        creator="InitSeq",
        uuid_key=uuid_key,
        revision=VERSION,
        nitro=nitro_text(sizes),
        description="Space engine (Hall, Room, Chamber, Plate, Spring, Cathedral, Ambience) and algorithmic reverb, "
                    "chained or in parallel, with wet EQ, width, Vintage and Freeze",
        tags="reverb hybrid hall room plate spring cathedral shimmer freeze ambient",
        panel_size=PANEL,
        prevent_sleep=True,
        knob_pie_color=6,                 # white arcs
    )

    # look: the panel art (rendered from panel_art.svg to panel.png, embedded in panel.svg) under everything,
    # the furnace display in its door
    dev.images.append(((HERE / "panel.svg").read_text(), (0, 0) + tuple(PANEL), 1000, 1.0, 1.0))
    if display:
        dev.display((HERE / "furnace.sksl").read_text(), rect=DISPLAY, inputs=["ui_a", "ui_b", "ui_c"],
                    id="KILN_FURNACE")

    # four framed sections, each a header (title + one control) and two rows of knobs
    k = lambda sec, col: SECTIONS[sec][0] + 2 + 13 * col
    r1, r2 = 10, 31
    a, sp, o, t = "algorithm", "space", "output", "tone"
    dev.choice("algo", "Algorithm", ["Dark Hall", "Quartz", "Shimmer", "Tides", "Prism"], default=0,
               rect=(SECTIONS[a][0] + 20, 1, 20, 4))
    dev.param("decay", "Decay", 0.1, 60, 2.5, unit_text="s", domain=Domain.CUBE_ROOT, knob=(k(a, 0), r1))
    dev.param("size", "Size", 0, 200, 100, unit_text="%", knob=(k(a, 1), r1))
    dev.param("damping", "Damping", 0, 100, 40, unit_text="%", knob=(k(a, 2), r1))
    dev.param("modulation", "Mod", 0, 100, 25, unit_text="%", knob=(k(a, 0), r2))
    dev.param("shimmer", "Shimmer", 0, 100, 50, unit_text="%", knob=(k(a, 1), r2))
    dev.toggle("freeze", "Freeze", default=False, button=(k(a, 2), r2 + 3, 11, 5), tooltip="Holds the algorithm's tail")

    dev.choice("irtype", "IR", ["Hall", "Room", "Chamber", "Plate", "Spring", "Cathedral", "Ambience"], default=0,
               rect=(SECTIONS[sp][0] + 17, 1, 22, 4))
    dev.param("irsize", "IR Size", 25, 200, 100, unit_text="%", knob=(k(sp, 0), r1))
    dev.param("irattack", "Attack", 0, 100, 0, unit_text="%", knob=(k(sp, 1), r1))
    dev.param("irdecay", "IR Decay", 10, 100, 100, unit_text="%", knob=(k(sp, 2), r1))
    dev.param("blend", "Blend", 0, 100, 50, unit_text="%", knob=(k(sp, 0), r2))
    dev.choice("routing", "Routing", ["Serial", "Parallel", "Algorithm", "Space"], default=0,
               rect=(k(sp, 1), r2 + 3, 24, 4), tooltip="Serial: the space feeds the algorithm · Parallel: both from "
                                                       "the input · Algorithm or Space alone")

    dev.toggle("send", "Send", default=False, button=(SECTIONS[o][0] + 28, 1, 11, 4), tooltip="100 % wet, for a return track")
    dev.param("predelay", "Predelay", 0, 250, 0, unit_text="ms", domain=Domain.CUBE_ROOT, knob=(k(o, 0), r1))
    dev.param("width", "Width", 0, 200, 100, unit_text="%", knob=(k(o, 1), r1))
    dev.param("mix", "Dry/Wet", 0, 100, 35, unit_text="%", knob=(k(o, 2), r1))
    dev.choice("vintage", "Vintage", ["Vintage: Off", "Subtle", "Old", "Older", "Extreme"], default=0,
               rect=(k(o, 0), r2 + 3, 37, 4), tooltip="Lower sample rate, bit depth and bandwidth of the reverb")

    dev.toggle("eqon", "EQ", default=True, button=(SECTIONS[t][0] + 16, 1, 10, 4), tooltip="The wet EQ on or off")
    dev.param("lowcut", "Low Cut", 20, 1000, 60, unit_text="Hz", domain=Domain.CUBE_ROOT, knob=(k(t, 0), r1))
    dev.param("highcut", "High Cut", 1000, 20000, 12000, unit_text="Hz", domain=Domain.CUBE_ROOT, knob=(k(t, 1), r1))
    dev.param("lowshelf", "Lo Shelf", -15, 15, 0, unit_text="dB", origin=Origin.CENTER, knob=(k(t, 0), r2))
    dev.param("highshelf", "Hi Shelf", -15, 15, 0, unit_text="dB", origin=Origin.CENTER, knob=(k(t, 1), r2))
    return dev


def write(dev: Device):
    out = HERE / "out" / f"{dev.name}.bwdevice"
    out.parent.mkdir(exist_ok=True)
    out.write_bytes(dev.build())
    if dev.name == "Hybrid Reverb":
        shutil.copy(out, HERE / out.name)   # the build kept in the repository
    if os.environ.get("KILN_DEVICES"):
        shutil.copy(out, Path(os.environ["KILN_DEVICES"]) / out.name)
    print("BUILD_OK", out.name, out.stat().st_size, "bytes, uuid", dev.uuid)


if __name__ == "__main__":
    write(hybrid_reverb())
    if "--probe" in sys.argv:
        write(hybrid_reverb("Hybrid Reverb Probe", "coldforge/hybrid-reverb-probe", SMALL, display=False))
