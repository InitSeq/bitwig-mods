"""Build "Spectral Resonator.bwdevice" (Coldforge) with bo3K's device generator.
python3 build.py  -> out/Spectral Resonator.bwdevice (and a copy next to this script)

Needs Python 3.10+ and two MIT-licensed checkouts, in CF_VENDOR (default: ./vendor):
  vendor/bo-bitwig            https://github.com/bo3K/bo-bitwig (the device generator)
  vendor/bitwig-nitro-tools   https://github.com/blakebratcher/bitwig-nitro-tools (its Nitro serializer)
CF_DEVICES: a Patchbay device folder to copy the build into (optional)."""
import os
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
VENDOR = Path(os.environ.get("CF_VENDOR", HERE / "vendor"))
sys.path.insert(0, str(VENDOR / "bitwig-nitro-tools" / "src"))
sys.path.insert(0, str(VENDOR / "bo-bitwig" / "tools" / "device-gen"))

from bwgen import Device, Domain, Origin, ButtonStyle  # noqa: E402

VERSION = 1  # metadata revision_no: raise it with each released change
COL = [76 + 14 * i for i in range(7)]   # knob columns on the right side


def spectral_resonator() -> Device:
    dev = Device(
        name="Spectral Resonator",
        creator="Coldforge",
        uuid_key="coldforge/anvil",       # stable UUID: projects keep finding the device
        revision=VERSION,
        nitro=(HERE / "spectral_resonator.nitro").read_text(),
        description="Spectral resonator: the input rings 32 tuned partials per channel (Hz, note or chord), "
                    "with Stretch, Shift, damping, Chorus/Wander/Grain modulation, Unison and Hold",
        tags="resonator spectral harmonic tuned metallic drone coldforge",
        panel_size=(176, 53),
        prevent_sleep=True,
        knob_pie_color=1,                 # orange arcs
    )

    dev.images.append(((HERE / "panel.svg").read_text(), (0, 0, 176, 53), 1000, 1.0, 1.0))
    dev.display((HERE / "spectral_resonator.sksl").read_text(), rect=(3, 3, 70, 47), inputs=["ui_a", "ui_b", "ui_c"],
                id="CF_SPECTRAL_RESONATOR_DISPLAY")

    # top: modulation type
    dev.choice("modtype", "Mod", ["None", "Chorus", "Wander", "Grain"], default=0, rect=(COL[0], 1, 98, 4),
               buttons=True, tooltip="None · Chorus: partials detune with an LFO · Wander: random drift · "
                                     "Grain: partials flicker in random bursts")
    # row 1: pitch and spectrum
    dev.param("freq", "Freq", 20, 5000, 220, unit_text="Hz", domain=Domain.CUBE_ROOT, knob=(COL[0], 8),
              id="FREQ")
    dev.param("note", "Note", 0, 127, 57, knob=(COL[1], 8))
    dev.param("stretch", "Stretch", -100, 100, 0, unit_text="%", origin=Origin.CENTER, knob=(COL[2], 8))
    dev.param("shift", "Shift", -48, 48, 0, unit_text="st", origin=Origin.CENTER, knob=(COL[3], 8))
    dev.param("oddeven", "Odd/Even", -100, 100, 0, unit_text="%", origin=Origin.CENTER, knob=(COL[4], 8))
    dev.param("unison", "Unison", 0, 100, 20, unit_text="%", knob=(COL[5], 8))
    dev.param("decay", "Decay", 0.01, 30, 1.5, unit_text="s", domain=Domain.CUBE_ROOT, knob=(COL[6], 8))
    # row 2: damping, modulation, levels
    dev.param("hfdamp", "HF Damp", 0, 100, 40, unit_text="%", knob=(COL[0], 25))
    dev.param("lfdamp", "LF Damp", 0, 100, 10, unit_text="%", knob=(COL[1], 25))
    dev.param("modamt", "Mod Amt", 0, 100, 40, unit_text="%", knob=(COL[2], 25))
    dev.param("modrate", "Mod Rate", 0.05, 20, 1.0, unit_text="Hz", domain=Domain.CUBE_ROOT, knob=(COL[3], 25))
    dev.param("drive", "Input", -24, 24, 0, unit_text="dB", origin=Origin.CENTER, knob=(COL[4], 25))
    dev.param("mix", "Mix", 0, 100, 50, unit_text="%", knob=(COL[6], 25))
    # bottom: pitch source, chord, hold
    dev.choice("pmode", "Pitch", ["Hz", "Note"], default=0, rect=(COL[0], 43, 26, 4), buttons=True,
               tooltip="Hz: the Freq knob sets the fundamental · Note: the Note knob (MIDI note) does")
    dev.choice("chord", "Chord", ["Single", "Octaves", "Fifths", "Major", "Minor", "Sus4"], default=0,
               rect=(COL[2], 42, 26, 4), tooltip="Three voices on the pitch: partials are shared between them")
    dev.toggle("hold", "Hold", default=False, button=(COL[4] + 1, 43, 14, 5), style=ButtonStyle.BUTTON,
               tooltip="Freezes the ringing partials and stops new input")
    return dev


def write(dev: Device):
    out = HERE / "out" / f"{dev.name}.bwdevice"
    out.parent.mkdir(exist_ok=True)
    out.write_bytes(dev.build())
    shutil.copy(out, HERE / out.name)
    if os.environ.get("CF_DEVICES"):
        shutil.copy(out, Path(os.environ["CF_DEVICES"]).expanduser() / out.name)
    print("BUILD_OK", out.name, out.stat().st_size, "bytes, uuid", dev.uuid)


if __name__ == "__main__":
    write(spectral_resonator())
