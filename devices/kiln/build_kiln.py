"""Build "Kiln.bwdevice" (Coldforge reverb) with bo3K's device generator.
python3 build_kiln.py          -> out/Kiln.bwdevice (and Kiln.bwdevice next to this script)
python3 build_kiln.py --probe  -> also two diagnostic variants (smaller buffers, with and without display)

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

VERSION = 2  # metadata revision_no: raise it with each released change

# delay buffer sizes in samples: lines, diffuser stages, early reflections and predelay, shifter
FULL = dict(LN=32768, DF=8192, ER=16384, SH=4096)
SMALL = dict(LN=8192, DF=2048, ER=4096, SH=2048)


def nitro_text(sizes=FULL) -> str:
    """kiln.nitro with its buffer sizes filled in."""
    tokens = {"@LN8@": 8 * sizes["LN"], "@LNM@": f'{sizes["LN"] - 8}.0', "@LN@": sizes["LN"],
              "@DF8@": 8 * sizes["DF"], "@DFM@": f'{sizes["DF"] - 8}.0', "@DF@": sizes["DF"],
              "@ER2@": 2 * sizes["ER"], "@ERM@": f'{sizes["ER"] - 8}.0', "@ER@": sizes["ER"],
              "@SHM@": f'{sizes["SH"] - 8}.0', "@SHW@": f'{sizes["SH"] - 96}.0', "@SH@": sizes["SH"]}
    text = (HERE / "kiln.nitro").read_text()
    for token, value in tokens.items():
        text = text.replace(token, str(value))
    assert "@" not in text, "a size token was not filled in"
    return text


def kiln(name="Kiln", uuid_key="coldforge/kiln", sizes=FULL, display=True, plain=False) -> Device:
  """plain: only the simplest controls (popup choices, linear knobs, one knob size), for finding a load problem."""
  dev = Device(
    name=name,
    creator="Coldforge",
    uuid_key=uuid_key,                # stable UUID: projects keep finding the device
    revision=VERSION,
    nitro=nitro_text(sizes),
    description="Algorithmic reverb: five characters, Decay up to 60 s, Freeze, Shimmer, tone, width and Vintage",
    tags="reverb hall plate shimmer freeze ambient coldforge",
    panel_size=(150, 53),
    prevent_sleep=True,
    knob_pie_color=1,                 # orange arcs
)

  # look: the steel panel under everything, the furnace display in its door
  dev.images.append(((HERE / "panel.svg").read_text(), (0, 0, 150, 53), 1000, 1.0, 1.0))
  if display:
      dev.display((HERE / "furnace.sksl").read_text(), rect=(3, 3, 74, 47), inputs=["ui_a", "ui_b", "ui_c"], id="KILN_FURNACE")

  # right side: the character on top, two rows of knobs, then Freeze and Vintage
  dev.choice("algo", "Character", ["Ember", "Glass", "Halo", "Drift", "Shard"], default=0, rect=(80, 1, 68, 4),
             buttons=not plain, tooltip="Ember: dark hall · Glass: bright, early reflections · Halo: octave shimmer · "
                                   "Drift: slow waves · Shard: short and metallic")
  dev.param("decay", "Decay", 0.1, 60, 2.5, unit_text="s", domain=(Domain.LINEAR if plain else Domain.LOG), knob=(80, 8), knob_size=None if plain else 3)
  dev.param("size", "Size", 0, 200, 100, unit_text="%", knob=(94, 8))
  dev.param("damping", "Damping", 0, 100, 40, unit_text="%", knob=(108, 8))
  dev.param("mod", "Mod", 0, 100, 25, unit_text="%", knob=(122, 8))
  dev.param("shimmer", "Shimmer", 0, 100, 50, unit_text="%", knob=(136, 8))
  dev.param("predelay", "Predelay", 0, 250, 0, unit_text="ms", domain=(Domain.LINEAR if plain else Domain.CUBE_ROOT), knob=(80, 25))
  dev.param("lowcut", "Low Cut", 20, 1000, 60, unit_text="Hz", domain=(Domain.LINEAR if plain else Domain.LOG), knob=(94, 25))
  dev.param("highcut", "High Cut", 1000, 20000, 12000, unit_text="Hz", domain=(Domain.LINEAR if plain else Domain.LOG), knob=(108, 25))
  dev.param("width", "Width", 0, 200, 100, unit_text="%", knob=(122, 25))
  dev.param("mix", "Mix", 0, 100, 35, unit_text="%", knob=(136, 25), knob_size=None if plain else 3)
  dev.toggle("freeze", "Freeze", default=False, button=(80, 43, 14, 5), style=ButtonStyle.BUTTON,
             tooltip="Holds the reverb tail and stops new input")
  dev.choice("vintage", "Vintage", ["Off", "Subtle", "Old", "Older", "Extreme"], default=0, rect=(97, 42, 22, 4),
             tooltip="Lower sample rate, bit depth and bandwidth of the reverb")

  return dev


def write(dev: Device):
    out = HERE / "out" / f"{dev.name}.bwdevice"
    out.parent.mkdir(exist_ok=True)
    out.write_bytes(dev.build())
    if dev.name == "Kiln":
        shutil.copy(out, HERE / out.name)   # the build kept in the repository
    if os.environ.get("KILN_DEVICES"):
        shutil.copy(out, Path(os.environ["KILN_DEVICES"]) / out.name)
    print("BUILD_OK", out.name, out.stat().st_size, "bytes, uuid", dev.uuid)


if __name__ == "__main__":
    write(kiln())
    if "--probe" in sys.argv:
        write(kiln("Kiln Probe A", "coldforge/kiln-probe-a", SMALL, display=False))
        write(kiln("Kiln Probe B", "coldforge/kiln-probe-b", SMALL, display=True))
        write(kiln("Kiln Probe C", "coldforge/kiln-probe-c", SMALL, display=False, plain=True))
