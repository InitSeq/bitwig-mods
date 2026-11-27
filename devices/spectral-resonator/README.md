# Spectral Resonator

> **Work in progress: not yet tried in Bitwig.** It builds and passes the offline test, but nobody has loaded it in Bitwig yet. Don't use it in projects yet.

A native Bitwig device (not a plug-in): a spectral resonator. The input makes 32 tuned partials per channel ring, like struck metal. Loaded with [Patchbay](https://github.com/shaboogen-dev/Patchbay)'s **Framework: Nitro Devices**. Not affiliated with Bitwig GmbH.

## What it does

- **Pitch:** **Hz** (Freq knob) or **Note** (MIDI note knob), plus **Chord**: Single, Octaves, Fifths, Major, Minor, Sus4 (three voices share the partials)
- **Stretch** spreads or squeezes the partials, **Shift** moves them in semitones, **Odd/Even** makes the sound hollow (odd partials) or thins the odd partials
- **Decay** up to 30 s, **HF Damp** and **LF Damp** shorten high and low partials
- **Mod:** None, Chorus (LFO detune), Wander (random drift), Grain (partials flicker in random bursts), with **Mod Amt** and **Mod Rate**
- **Unison** detunes left against right, **Input** gain, **Mix** (equal power)
- **Hold** freezes the ringing partials and stops new input

Inside: two banks of 32 state-variable band-pass resonators, tuned at control rate. Their bandwidth follows the decay of each partial. The gain keeps the loudness about the same at any decay. A soft limiter sits on the output.

**The display** shows the partials as glowing bars on a log frequency axis. They glow hotter with the wet signal, sway with Chorus and Wander, and flicker with Grain. Sparks fly off the anvil face when the input hits. Hold turns everything ice blue.

The pitch comes from the knobs, not from MIDI: modulate **Note** or **Freq** with Bitwig's modulators (for example Keytrack) to play it.

## Install (once it is confirmed to load)

1. Put `Spectral Resonator.bwdevice` in a folder of your own.
2. In Patchbay, enable **Framework: Nitro Devices**, add that folder under **Tools → Device Folders**, and click **Load**.
3. Find **Spectral Resonator** in Bitwig's device browser (Audio FX).

Built for Bitwig Studio 6.1.7. The DSP is stored as Nitro source, which Bitwig compiles when it loads the device.

## Build

| File | What |
|---|---|
| `spectral_resonator.nitro` | The DSP, in Bitwig's Nitro language |
| `spectral_resonator.sksl` | The display (an SkSL shader), fed by three display outputs of the DSP |
| `panel.svg` | The panel background |
| `build.py` | The device description: controls, layout and look. Writes `out/Spectral Resonator.bwdevice` and a copy here |
| `test/sim.py` | Runs the Nitro code itself offline, sample by sample: tuning in Hz and Note mode, Shift, decay time, dry path, Hold, stability of every chord and modulation (`SIM_OK`) |

It needs Python 3.10+ and two MIT-licensed projects, checked out in `vendor/` (or a folder named in `CF_VENDOR`):

- [bo3K/bo-bitwig](https://github.com/bo3K/bo-bitwig) as `vendor/bo-bitwig`: its device generator, `tools/device-gen`, and its Nitro-to-Python transpiler for the test
- [blakebratcher/bitwig-nitro-tools](https://github.com/blakebratcher/bitwig-nitro-tools) as `vendor/bitwig-nitro-tools`: only its Nitro serializer is used

```sh
python3 build.py
CF_DEVICES=~/my-devices python3 build.py   # also copy the build into a Patchbay device folder
```

The test needs numpy.

Thanks to bo3K for the device generator and its notes on Bitwig's device format.
