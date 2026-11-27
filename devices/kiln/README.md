# Kiln

> **Work in progress: Kiln does not load in Bitwig yet.** It is registered and shows up in the browser, but inserting it fails. The cause is being narrowed down; don't use it in projects yet.

A native Bitwig device (not a plug-in): an algorithmic reverb by Coldforge, with an animated furnace display. Loaded with [Patchbay](https://github.com/shaboogen-dev/Patchbay)'s **Framework: Nitro Devices**. Not affiliated with Bitwig GmbH.

## What it does

- **Character:** Ember (dark hall), Glass (bright, rich early reflections), Halo (octave-up shimmer in the tail), Drift (slow, deep waves), Shard (short, sparse and metallic)
- **Decay** 0.1–60 s, **Size**, **Damping**, **Mod**, **Shimmer** (Halo only), **Predelay** up to 250 ms
- **Low Cut** and **High Cut** (12 dB/oct), **Width**, **Mix** (equal power)
- **Freeze** holds the tail; **Vintage** lowers sample rate, bit depth and bandwidth of the reverb

Inside: an 8-line feedback delay network with Householder mixing, input diffusers, early reflections, damping, modulation and an octave shifter in the loop.

**The display** is a kiln: its arch widens with Size, the decay curve stretches with Decay, embers rise with the level and sway with Mod, Damping turns the glow from white-hot to deep red, Freeze turns it ice blue, and Halo adds sparks in the arch.

Planned: a convolution stage ("Space") next to the algorithm, with routing and blend.

## Install (once it loads)

1. Put `Kiln.bwdevice` in a folder of your own.
2. In Patchbay, enable **Framework: Nitro Devices**, add that folder under **Tools → Device Folders**, and click **Load**.
3. Find **Kiln** in Bitwig's device browser (Audio FX).

Built for Bitwig Studio 6.1.7. The DSP is stored as Nitro source, which Bitwig compiles when it loads the device.

## Build

| File | What |
|---|---|
| `kiln.nitro` | The DSP, in Bitwig's Nitro language. Buffer sizes are `@…@` tokens that the build fills in |
| `furnace.sksl` | The display (an SkSL shader), fed by three display outputs of the DSP |
| `panel.svg` | The panel background |
| `build_kiln.py` | The device description: controls, layout and look. Writes `out/Kiln.bwdevice` and `Kiln.bwdevice` here |
| `test/sim_kiln.py` | Runs `kiln.nitro` itself offline, sample by sample: decay time, dry path, predelay, Freeze, stability of every character (`SIM_OK`) |

It needs Python 3.10+ and two MIT-licensed projects, checked out in `vendor/` (or a folder named in `KILN_VENDOR`):

- [bo3K/bo-bitwig](https://github.com/bo3K/bo-bitwig) as `vendor/bo-bitwig`: its device generator, `tools/device-gen`, and its Nitro-to-Python transpiler for the test
- [blakebratcher/bitwig-nitro-tools](https://github.com/blakebratcher/bitwig-nitro-tools) as `vendor/bitwig-nitro-tools`: only its Nitro serializer is used

```sh
python3 build_kiln.py            # add --probe for two diagnostic builds with smaller buffers
KILN_DEVICES=~/my-devices python3 build_kiln.py   # also copy the build into a Patchbay device folder
```

The test needs numpy.

Thanks to bo3K for the device generator and its notes on Bitwig's device format.
