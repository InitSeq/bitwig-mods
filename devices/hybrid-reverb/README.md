# Hybrid Reverb

A native Bitwig device (not a plug-in): two reverb engines, **Space** and **Algorithm**, chained or in parallel, with a wet EQ and an animated furnace display. Loaded with [Patchbay](https://github.com/shaboogen-dev/Patchbay)'s **Framework: Nitro Devices**. Not affiliated with Bitwig GmbH.

![Hybrid Reverb](../../previews/Hybrid%20Reverb.png)

*(Preview rendered outside Bitwig: Bitwig draws the knobs, buttons and menus itself.)*

Status: early. It loads in Bitwig Studio 6.1.7 (macOS); the Space engine, routing and EQ arrived in this version and are checked offline only so far.

## Controls

| Section | Controls |
|---|---|
| Routing | **Serial** (Space feeds Algorithm), **Parallel**, **Algorithm** only, **Space** only; **Blend** crossfades Space (0 %) to Algorithm (100 %) |
| Algorithm | Character: **Dark Hall**, **Quartz**, **Shimmer**, **Tides**, **Prism**. **Decay** 0.1–60 s, **Size**, **Damping**, **Mod**, **Shimmer** (Shimmer character), **Freeze** |
| Space | **IR**: Hall, Room, Chamber, Plate, Spring, Cathedral, Ambience. **IR Size** 25–200 % (stretches the space), **IR Attack** (fades the start in), **IR Decay** 10–100 % (shortens the tail) |
| Tone | **EQ** on/off, **Low Cut**, **High Cut** (12 dB/oct), **Low Shelf** (200 Hz) and **High Shelf** (5 kHz) ±15 dB |
| Output | **Predelay** up to 250 ms, **Width**, **Vintage** (Subtle to Extreme: rate, bit depth and bandwidth), **Dry/Wet**, **Send** (100 % wet, for a return track) |

**The look** is a 1970s studio unit: a dark enamel front plate with cream screen print and a printed scale round every knob, and a meter bridge with two backlit VU meters, input and reverb, whose needles swing with the levels. Two jewel lamps light up for Freeze and for the Shimmer algorithm.

## How the engines work

- **Algorithm:** an 8-line feedback delay network with Householder mixing, input diffusers, early reflections, damping, a 30 Hz high-pass and a soft limiter in the loop, modulation, and an octave shifter in the feedback path (Shimmer).
- **Space:** the seven impulse-response types of the Hybrid Reverb plug-in it comes from, rebuilt as a real-time structure instead of a convolution: each type's early-reflection cluster as taps, and its diffuse tail from a second 8-line network in which the low, mid and high bands decay with the type's own times, then tilted and band-limited like the IR. Spring adds a 2 kHz resonance and a dispersive echo every 56 ms; Cathedral its slap echoes. There is no IR loading.

## Install

1. Put `Hybrid Reverb.bwdevice` in a folder of your own.
2. In Patchbay, enable **Framework: Nitro Devices**, add that folder under **Tools → Device Folders**, and click **Load**.
3. Find **Hybrid Reverb** in Bitwig's device browser (Audio FX).

Built for Bitwig Studio 6.1.7. The DSP is stored as Nitro source, which Bitwig compiles when it loads the device. Projects that used the earlier name, Kiln, open with Hybrid Reverb: the device ID stayed the same.

## Build

| File | What |
|---|---|
| `hybrid_reverb.nitro` | The DSP, in Bitwig's Nitro language. Buffer sizes are `@…@` tokens that the build fills in |
| `furnace.sksl` | What moves on the meter bridge (an SkSL shader over a transparent background): the VU needles, backlight and lamps, fed by three display outputs of the DSP |
| `make_panel.py` | Draws the panel art (`panel_art.svg`: enamel front plate, screen print, knob scales, VU meter faces, lamps), renders it with headless Chrome to `panel.png` and wraps it in `panel.svg`, which the device shows under its controls. Reads the layout from the build script |
| `panel.svg`, `panel.png`, `panel_art.svg` | The panel, as built by `make_panel.py` |
| `build_hybrid_reverb.py` | The device description: controls, layout and look. Writes `out/Hybrid Reverb.bwdevice` and a copy here |
| `test/sim_hybrid_reverb.py` | Runs the Nitro text itself offline, sample by sample: decay times, predelay, Freeze, stability of every character and IR type, equal loudness of the IR types, IR Decay, routing and EQ (`SIM_OK`) |

It needs Python 3.10+ and two MIT-licensed projects, checked out in `vendor/` (or a folder named in `KILN_VENDOR`):

- [bo3K/bo-bitwig](https://github.com/bo3K/bo-bitwig) as `vendor/bo-bitwig`: its device generator, `tools/device-gen`, and its Nitro-to-Python transpiler for the test
- [blakebratcher/bitwig-nitro-tools](https://github.com/blakebratcher/bitwig-nitro-tools) as `vendor/bitwig-nitro-tools`: only its Nitro serializer is used

```sh
python3 build_hybrid_reverb.py
KILN_DEVICES=~/my-devices python3 build_hybrid_reverb.py   # also copy the build into a Patchbay device folder
```

The test needs numpy.

Thanks to bo3K for the device generator and its notes on Bitwig's device format.
