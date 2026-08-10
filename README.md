# sstv-encoder

[![](https://jitpack.io/v/SEKY443/sstv-encoder.svg)](https://jitpack.io/#SEKY443/sstv-encoder)
[![build](https://github.com/SEKY443/sstv-encoder/actions/workflows/build.yml/badge.svg)](https://github.com/SEKY443/sstv-encoder/actions/workflows/build.yml)

A Kotlin encoder that turns a picture into a **Slow-Scan Television** transmission — the analogue
mode radio amateurs have used to send images over HF since the 1950s.

Feed it pixels, get back 16-bit mono PCM. Play that out of a speaker and any SSTV decoder
(Robot36, MMSSTV, QSSTV, an FT-8xx radio) will draw your picture back.

**35 modes across 8 families** — Martin, Scottie, Robot, PD (including PD 120, the mode the ISS
transmits on), Pasokon TV, Wraase, AVT and the historic monochrome formats.

> **Home: <https://github.com/SEKY443/sstv-encoder>**
> That's the only place this library is published from. If you want to use it, start there —
> releases, issues and the latest version all live in that repo.

```kotlin
import io.github.seky443.sstv.*
import io.github.seky443.sstv.image.readSstvPixels

val pixels = readSstvPixels(File("photo.png"), SstvMode.MARTIN_M1)
val signal = SstvEncoder.encode(pixels, SstvMode.MARTIN_M1)
signal.writeWav(File("photo.wav"))
```

**Zero dependencies.** The encoder is Kotlin stdlib only — no audio framework, no image library, no
native code — so the same code runs on the JVM and on Android.

## Why

Every maintained SSTV project is a *decoder*, and the C++ ones are GPL. There was nothing on Maven
Central that just took an image and gave you the audio. This is that, written from scratch against
the published mode timings.

It was pulled out of [SSTV Alarm](https://github.com/SEKY443/Android-SSTV-Alarm), an Android alarm clock that
transmits the day's forecast as SSTV and makes you decode it to turn the alarm off.

## Supported modes

35 modes across 8 families. Pick one with `SstvMode.MARTIN_M1`, or look one up with
`SstvMode.fromName("pd_120")` / `SstvMode.fromVisCode(95)`. `SstvMode.inFamily(SstvFamily.PD)`
lists a family.

Modes marked ⚠️ are **experimental** — see [below](#experimental-modes).

<details open>
<summary><b>Martin</b> — GBR sequential, sync at the head of the line</summary>

| Mode | VIS | Size | Time on air |
|---|---|---|---|
| `MARTIN_M1` | 44 | 320 × 256 | 114.3 s |
| `MARTIN_M2` | 40 | 320 × 256 | 58.1 s |
| `MARTIN_M3` | 36 | 320 × 128 | 57.1 s |
| `MARTIN_M4` | 32 | 320 × 128 | 29.0 s |

Martin Emmerson, G3OQD, mid-1980s. `MARTIN_M1` is about as universally supported as SSTV gets and
is this library's default. M3 and M4 are M1 and M2 at half the vertical resolution — they send 128
lines, and a decoder draws each one two pixels tall.
</details>

<details>
<summary><b>Scottie</b> — GBR sequential, sync buried mid-line before the red sweep</summary>

| Mode | VIS | Size | Time on air |
|---|---|---|---|
| `SCOTTIE_S1` | 60 | 320 × 256 | 109.6 s |
| `SCOTTIE_S2` | 56 | 320 × 256 | 71.1 s |
| `SCOTTIE_DX` | 76 | 320 × 256 | 268.9 s |

Eddie T. J. Murphy, GM3SBC, who arrived at it by modifying the firmware of a Wraase SC-1 — which is
why the two families share a lineage. `SCOTTIE_DX` spends four and a half minutes on air to buy the
robustness to get a picture through conditions that would shred a faster mode.
</details>

<details>
<summary><b>Robot</b> — monochrome, and Y/C colour that sends chroma at reduced resolution</summary>

| Mode | VIS | Size | Time on air |
|---|---|---|---|
| `ROBOT_8_BW` | 2 | 320 × 120 | 8.0 s |
| `ROBOT_12_BW` | 6 | 320 × 120 | 12.0 s |
| `ROBOT_24_BW` | 10 | 320 × 240 | 24.0 s |
| `ROBOT_36` | 8 | 320 × 240 | 36.0 s |
| `ROBOT_72` | 12 | 320 × 240 | 72.0 s |

Robot Research Inc., whose scan converters defined the earliest colour standards — these are the
oldest modes here that are still in common use.

Rather than send three full colour sweeps, the Robot colour modes send brightness at full
resolution and colour at reduced resolution, which is why they fit a colour picture into so little
time. `ROBOT_36` is 4:2:0: each line carries luminance plus *one* chrominance channel, R-Y and B-Y
alternating, so full colour costs two lines. `ROBOT_72` is 4:2:2 and sends both every line.
`ROBOT_36` is a good fast default — 36 seconds for colour, and near-universal decoder support.
</details>

<details>
<summary><b>PD</b> — Y/C, two picture rows per transmitted line. <code>PD_120</code> is the ISS mode</summary>

| Mode | VIS | Size | Time on air |
|---|---|---|---|
| `PD_50` | 93 | 320 × 256 | 49.7 s |
| `PD_90` | 99 | 320 × 256 | 90.0 s |
| `PD_120` | 95 | 640 × 496 | 126.1 s |
| `PD_160` | 98 | 512 × 400 | 160.9 s |
| `PD_180` | 96 | 640 × 496 | 187.1 s |
| `PD_240` | 97 | 640 × 496 | 248.0 s |
| `PD_290` | 94 | 800 × 616 | 288.7 s |

Paul Turner, G4IJE. The highest-resolution modes in common use, and what you want if the picture
matters more than the clock.

One sync pulse covers two rows: the first row's luminance, then R-Y and B-Y averaged across both
rows, then the second row's luminance. Sharing chroma vertically is what buys the resolution — so
`lineCount` is half `height` for these, and `height` must be even.

`PD_120` is worth singling out: it is what the ISS transmits on during its SSTV events, so it is
the mode most likely to be pointed at a receiver that has never heard of you.
</details>

<details>
<summary><b>Pasokon TV</b> — RGB on a 1965-unit line</summary>

| Mode | VIS | Size | Time on air |
|---|---|---|---|
| `PASOKON_P3` | 113 | 640 × 496 | 203.1 s |
| `PASOKON_P5` | 114 | 640 × 496 | 304.6 s |
| `PASOKON_P7` | 115 | 640 × 496 | 406.1 s |

John Langner, WB2OSZ, for PC-based image exchange. Every part of a Pasokon line is a whole number
of one time unit — 25 units of sync, 5 for each gap, one per pixel — so a line is exactly 1965
units in all three modes, and the modes differ only in how long a unit lasts (1/4800, 1/3200 and
1/2400 of a second).
</details>

<details>
<summary><b>Wraase</b> — RGB with no separators between sweeps</summary>

| Mode | VIS | Size | Time on air |
|---|---|---|---|
| `WRAASE_SC2_120` | 63 | 320 × 256 | 121.7 s |
| `WRAASE_SC2_180` | 55 | 320 × 256 | 182.0 s |
| `WRAASE_SC2_60` ⚠️ | 59 | 320 × 256 | 60.0 s |
| `WRAASE_SC1_24` ⚠️ | 33 | 256 × 240 | 24.0 s |
| `WRAASE_SC1_48` ⚠️ | 37 | 256 × 240 | 48.0 s |
| `WRAASE_SC1_96` ⚠️ | 41 | 256 × 240 | 96.0 s |

Volker Wraase, DL2RZ, of Wraase Electronics. SC-1 came first and is the firmware Scottie grew out
of; SC-2 is the one that stayed in use. Alone among the RGB families, Wraase runs its three sweeps
back to back with no separator between them.
</details>

<details>
<summary><b>AVT</b> ⚠️ — header-based sync instead of a pulse per line</summary>

| Mode | VIS | Size | Time on air |
|---|---|---|---|
| `AVT_24` ⚠️ | 64 | 320 × 240 | 24.0 s |
| `AVT_90` ⚠️ | 68 | 320 × 240 | 90.0 s |
| `AVT_125` ⚠️ | 72 | 320 × 240 | 125.0 s |
| `AVT_188` ⚠️ | 73 | 320 × 240 | 188.0 s |

The Amiga Video Transceiver software. AVT's idea is that a sync pulse on every line is a liability:
a burst of noise landing on one tears the picture from there down. So it drops them entirely and
synchronises from a digital header sent once, up front — which is exactly the part with no public
specification, and therefore the part this library cannot implement. See
[experimental modes](#experimental-modes).
</details>

<details>
<summary><b>Legacy monochrome</b> ⚠️ — the historic 8/16/32-second formats</summary>

| Mode | VIS | Size | Time on air |
|---|---|---|---|
| `MONO_8` ⚠️ | 1 | 120 × 120 | 8.0 s |
| `MONO_16` ⚠️ | 3 | 120 × 120 | 16.0 s |
| `MONO_32` ⚠️ | 5 | 256 × 240 | 32.0 s |

Where SSTV started, in the late 1950s: one luminance sweep behind a sync pulse and nothing else.
These predate VIS headers altogether, so the codes here are this library's invention rather than
anything a receiver will recognise.
</details>

### Which one should I use?

The trade is always the same: time on air buys resolution and noise immunity, and nothing else
does. A 640 × 496 picture genuinely takes four times as long as a 320 × 256 one — there is no
compression anywhere in SSTV.

| If you want | Use | Why |
|---|---|---|
| A safe default | `MARTIN_M1` | 114 s, colour, decoded by everything. The library's default |
| Colour, quickly | `ROBOT_36` | 36 s for a 320 × 240 colour picture, and very widely supported |
| The best picture | `PD_180` | 640 × 496 in about three minutes |
| To match the ISS | `PD_120` | The mode ISS SSTV events transmit on |
| To get through bad conditions | `SCOTTIE_DX` | 269 s of redundancy; built for weak signals |
| To be quick above all | `MARTIN_M4` | 29 s, at half vertical resolution |
| A fast link test | `ROBOT_8_BW` | 8 s of monochrome, just to confirm the path works |

If you are transmitting to someone specific, ask what their software handles. If you are
transmitting to whoever is listening, `MARTIN_M1`, `SCOTTIE_S1` and `ROBOT_36` are the three with
the fewest surprises.

### Where the timings come from

Every non-experimental mode is transcribed from JL Barber N7CXI, *Proposal for SSTV Mode
Specifications* (Dayton, 2000), with Dave Jones KB4YZ, *SSTV modes - line timing* (1999) filling the
gaps, and each one reconstructs its published line time exactly — that is what
`SstvModeTimingTest` asserts.

Martin, Scottie and Wraase SC-2 180 were additionally checked boundary-for-boundary against the
[Robot36](https://github.com/xdsopl/robot36) decoder's channel offsets, and the whole waveform was
cross-checked against [pySSTV](https://github.com/dnet/pySSTV) for seven modes.

> One deliberate divergence: pySSTV shortens each Scottie sweep from the specified 138.24 ms to
> 136.74 ms and pads the difference with an extra gap. Total line time still comes out right, so
> decoders lock either way, but the colour data ends up 1.1% narrow. This library follows the
> specification and the Robot36 decoder, which agree with each other.

### Experimental modes

No public timing specification could be found for the AVT family, Wraase SC-1, Wraase SC-2 60, or
the historic monochrome modes. They are implemented on a best-effort basis:

- **Timings are reconstructed**, derived from each mode's nominal on-air duration and frame size
  rather than transcribed from a spec.
- **VIS codes are guesses**, taken from slots left unassigned in the KB4YZ table.
- **AVT's digital sync header is not implemented.** AVT deliberately omits the per-line sync pulse
  and synchronises from a header sent once at the start; that header is the part there is no
  specification for, so what this emits is the picture data alone.

Expect these to be rejected by stock decoders. They are flagged at runtime via
`SstvMode.isExperimental`, so you can filter them out:

```kotlin
val usable = SstvMode.entries.filterNot { it.isExperimental }
```

If you have a copy of the AVT or SC-1 specifications, please
[open an issue](https://github.com/SEKY443/sstv-encoder/issues) — they would be straightforward to
finish.

## Install

Via [JitPack](https://jitpack.io/#SEKY443/sstv-encoder). Add the repository, then the dependency:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.SEKY443:sstv-encoder:v0.2.0")
}
```

<details>
<summary>Groovy DSL</summary>

```groovy
repositories { maven { url 'https://jitpack.io' } }
dependencies { implementation 'com.github.SEKY443:sstv-encoder:v0.2.0' }
```
</details>

Any git tag, branch (`main-SNAPSHOT`) or commit hash works as the version.

Prefer to build it yourself? `./gradlew publishToMavenLocal` puts
`io.github.seky443:sstv-encoder:0.2.0` in your local Maven repository, or use a composite build —
see [Using it from an Android app](#using-it-from-an-android-app).

### Upgrading from 0.1.0

0.2.0 adds 33 modes and is a breaking change. `MARTIN_M1` and `SCOTTIE_S1` still produce
byte-identical audio, so if all you ever did was name a mode and call `encode`, nothing changes.

`SstvMode` lost five properties that only ever made sense for a Martin- or Scottie-shaped line:
`pixelSeconds`, `scanSeconds`, `syncPulseSeconds`, `syncPorchSeconds` and `separatorSeconds`. There
is no replacement — a Robot 36 line has two different sweep lengths and an AVT line has no sync
pulse at all, so there is no honest single value to report. Line layout now lives in an internal
`LineFormat`. If you were using these to locate a sweep inside a line, state the published
constants explicitly in your own code; that is what this library's own tests now do.

Gained in exchange: `family`, `isExperimental`, `rowsPerLine`, `lineCount`, `SstvMode.fromVisCode()`
and `SstvMode.inFamily()`.

One quiet behavioural change: `SstvSignal.lineCount` now counts *transmitted* lines rather than
returning `mode.height`. Those are the same for every family except PD, where one line paints two
rows. Sizing a progress bar with it is still right; using it as a picture height is not — use
`mode.height` for that.

## Usage

### The picture

The encoder takes raw pixels: an `IntArray` in row-major order, each entry packed as `0xAARRGGBB`.
The alpha byte is ignored. The array has to be exactly one frame — `SstvMode.pixelCount`, which is
320 × 256 = 81,920 for `MARTIN_M1` and 640 × 496 = 317,440 for `PD_120`.

That deliberately narrow input is what keeps the core free of any image library. On the JVM there
are AWT helpers that do the resampling for you:

```kotlin
import io.github.seky443.sstv.image.*

// From a file, letterboxed to fit the frame.
val pixels = readSstvPixels(File("photo.png"), SstvMode.MARTIN_M1)

// Or from a BufferedImage you already have, cropped to fill instead.
val pixels = image.toSstvPixels(SstvMode.MARTIN_M1, Scaling.FILL)
```

`Scaling` is `FIT` (letterbox, the default), `FILL` (crop) or `STRETCH`.

Pictures survive the round trip best with high contrast, heavy strokes and no fine detail. Sending
a colour bar across the top row gives a receiving operator an instant check that the channels
arrived in the right order and the image is not skewed.

### The audio

```kotlin
val signal = SstvEncoder.encode(pixels, SstvMode.MARTIN_M1)

signal.pcm            // ShortArray, 16-bit signed mono
signal.sampleRate     // 22050
signal.totalSeconds   // 116.0 — header + picture + trailing silence
signal.toByteArray()  // little-endian bytes, for any raw PCM sink
signal.writeWav(File("out.wav"))
```

Tune the output by constructing an encoder instead of using the shorthand:

```kotlin
val encoder = SstvEncoder(
    sampleRate = 44100,             // default 22050; the highest tone on air is 2300 Hz
    amplitude = 0.75,               // peak as a fraction of full scale, leaves headroom
    trailingSilenceSeconds = 0.75   // clean gap before a looped transmission repeats
)
val signal = encoder.encode(pixels, SstvMode.SCOTTIE_S1)
```

An `SstvEncoder` is immutable and safe to share between threads.

### Painting in step with the audio

`SstvSignal` carries the timeline, so you can draw the picture line by line as it goes out without
ever decoding the audio back:

```kotlin
val line = signal.lineIndexAt(playbackSeconds)        // Int?, null during header and silence
val progress = signal.linePositionAt(playbackSeconds) // Float, fractional scan lines
```

Both count *transmitted* lines, which is `signal.lineCount` — the same as the picture height for
every family except PD, where one line paints two rows. Multiply by `mode.rowsPerLine` to get the
top picture row a given line fills in.

### From Java

Kotlin's `SstvEncoder.encode(...)` shorthand lives on the companion, so from Java go through the
shared instance:

```java
SstvSignal signal = SstvEncoder.Default.encode(pixels, SstvMode.MARTIN_M1);
Wav.writeWav(signal, new File("out.wav"));
```

### Hearing it

Point a phone running Robot36 at your speakers and watch the picture come back:

```kotlin
val signal = SstvEncoder.encode(pixels)
AudioSystem.getAudioInputStream(ByteArrayInputStream(signal.toWavBytes())).use { stream ->
    val clip = AudioSystem.getClip()
    clip.open(stream)
    clip.start()
    Thread.sleep((signal.totalSeconds * 1000).toLong() + 250)
    clip.close()
}
```

## When a decoder will not read it

| Symptom | Usual cause |
|---|---|
| Nothing decodes at all | The receiver never caught the VIS header. Start the recording *before* the transmission, and leave the leader intact — it is the first 0.91 s and decoders lock onto it |
| Decoder picks the wrong mode | Another mode's VIS code got through, or the receiver does not support the one you sent. `SstvMode.fromVisCode()` tells you what a given code means |
| Picture slants diagonally | The two clocks disagree slightly. This is normal over the air and every serious decoder has a slant correction; it is not something the encoder can fix |
| Colours swapped or wrong | Pixels are not packed `0xAARRGGBB`. Android's `Bitmap.getPixels` gives this already; a `ByteBuffer` from elsewhere may well be BGR |
| Picture squashed to half height | A PD mode with rows in the wrong order. PD reads two rows per line, so row order matters more than in the sequential families |
| Audible clicks, smeared picture | Something re-encoded the audio. Keep it 16-bit PCM — MP3 and AAC destroy the phase continuity SSTV depends on |
| An experimental mode does nothing | Expected. See [experimental modes](#experimental-modes) |

Two things that are worth ruling out first: the audio being clipped (drop `amplitude` — the default
0.75 already leaves headroom, but a hot output stage can still square the tops off), and the
picture itself. Fine detail, thin strokes and low contrast do not survive SSTV. A colour bar along
the top row gives the operator at the other end an instant read on whether the channels arrived in
the right order and whether the image is skewed.

## Using it from an Android app

The library is Kotlin stdlib only and compiles to Java 11 bytecode, so it drops into an Android module
with no desugaring. Just do not touch `io.github.seky443.sstv.image` — that package uses AWT and
ImageIO, which Android does not have. Convert a `Bitmap` yourself instead:

```kotlin
val pixels = IntArray(mode.pixelCount)
bitmap.getPixels(pixels, 0, mode.width, 0, 0, mode.width, mode.height)
val signal = SstvEncoder.encode(pixels, mode)
```

Then hand `signal.pcm` to an `AudioTrack` built with `ENCODING_PCM_16BIT`, `CHANNEL_OUT_MONO` and
`signal.sampleRate`. Streaming the array in chunks and wrapping at the end loops the transmission
with flat memory use, and `AudioTrack.playbackHeadPosition` gives you the playback seconds to feed
into `lineIndexAt`.

### Memory, on the long modes

A signal is held whole, so the slow high-resolution modes are not free. At the default 22050 Hz:

| Mode | On air | `signal.pcm` | Peak through `toWavBytes()` |
|---|---|---|---|
| `ROBOT_36` | 38 s | 1.6 MB | 4.9 MB |
| `MARTIN_M1` | 116 s | 5.1 MB | 15.3 MB |
| `PD_120` | 128 s | 5.6 MB | 16.9 MB |
| `PD_290` | 290 s | 12.8 MB | 38.4 MB |
| `PASOKON_P7` | 408 s | 18.0 MB | 53.9 MB |

`toWavBytes()` peaks at roughly three times the PCM size, because it holds the samples, their
little-endian bytes and the assembled file at once — and `writeWav()` goes through it, so it costs
the same. On a memory-constrained device, either stick to the shorter modes, drop `sampleRate` to
11025 (still four times the highest tone on air), or feed `signal.pcm` straight to `AudioTrack` and
skip the WAV entirely. (11025 Hz still sits comfortably above what a 2300 Hz tone needs; the
encoder refuses anything below 8000 Hz, where the tones would alias.)

To develop against the source without publishing, add a composite build to the app's
`settings.gradle.kts`:

```kotlin
includeBuild("../sstv-encoder")
```

Gradle substitutes it for `io.github.seky443:sstv-encoder` automatically, because the group and
name match.

## How it works

An SSTV transmission is a single audio tone whose frequency carries the picture:

- **1900 Hz leader**, a 1200 Hz break, another leader, then the **VIS header** — a start bit, seven
  data bits sent LSB first, an even parity bit and a stop bit, where a 1 is 1100 Hz and a 0 is
  1300 Hz. That code is how the receiver knows which mode, and therefore which line timings, to
  expect.
- **Each scan line** is usually a 1200 Hz sync pulse and a black porch, then the picture data. What
  that picture data holds — and, for Scottie and AVT, where the sync pulse sits or whether it is
  there at all — is what separates the families:

  | Family | The line carries |
  |---|---|
  | Martin, Scottie, Pasokon, Wraase | three full colour sweeps |
  | Robot B/W, legacy mono | one luminance sweep |
  | Robot 36 | luminance plus *one* chrominance channel, R-Y and B-Y alternating per line |
  | Robot 72 | luminance plus both chrominance channels, each at half width |
  | PD | two rows' luminance either side of a shared, vertically averaged R-Y and B-Y |
  | AVT | picture only — no sync pulse at all |

  Scottie is the odd one out among the RGB families: its sync pulse sits mid-line, ahead of the red
  sweep, rather than at the head of the line.

- **Within a sweep** each pixel gets a slice of time at a frequency between 1500 Hz (black) and
  2300 Hz (white), linear in the 8-bit channel value. Chrominance rides the same scale, so neutral
  grey — 128 — lands on 1901.6 Hz.

Two details matter more than they look:

**Phase continuity.** Each tone picks up the phase the last one left off at. Restarting the sine
at zero every segment puts a click at every boundary and smears the decoded picture.

**Cumulative timing.** Sample counts come from total elapsed time, not from rounding each segment
independently. A Martin 1 frame is over 245,000 segments; rounding each one on its own drifts the
line timing far enough to shear the received picture.

## Building

```bash
./gradlew build                # compile and test
./gradlew publishToMavenLocal  # install io.github.seky443:sstv-encoder:0.2.0 locally
```

Requires a JDK 17 or newer to run Gradle. The Java 17 toolchain the build asks for is downloaded
automatically if you do not have it, and the library itself targets Java 11.

The tests measure the actual waveform rather than trusting the constants. They count zero crossings
to check that the leader is 1900 Hz, that the VIS bits spell out the right code, that white is
2300 Hz and black is 1500 Hz, that mid-grey lands at 1901.6 Hz, and that each family lays its line
out the way its specification says — Robot 36 alternating its chrominance channel between lines, PD
packing two rows behind one sync pulse, Scottie's sweeps running their full 138.24 ms, AVT emitting
no sync pulse at all. Every mode is then encoded end to end and checked against the length its own
timings predict.

Line timings are pinned to the published figures within a microsecond, which is three orders of
magnitude tighter than any error that could matter — a few milliseconds of drift per line is the
difference between a picture and a smear. The tolerance is not tighter still only because the
published tables round: Martin M2 and M4 are quoted at 226.7986 ms, which does not reconcile with
their own 0.2288 ms pixel time.

## Releasing

JitPack builds from git tags, so a release is just a tag:

```bash
# bump `version` in build.gradle.kts to match, then
git tag v0.2.0 && git push origin v0.2.0
```

The first time, open <https://jitpack.io/#SEKY443/sstv-encoder> and hit *Get it* on the tag to
trigger the build. After that JitPack picks up new tags on demand.

## Licence

MIT — see [LICENSE](LICENSE). Use it in anything, commercial or not; just keep the copyright
notice. That is the whole point: the existing SSTV projects are GPL, which is what made this one
necessary.

---

**Using this library?** Everything lives at <https://github.com/SEKY443/sstv-encoder> — grab the
latest version there, and open an
[issue](https://github.com/SEKY443/sstv-encoder/issues) if a decoder will not read your
transmission.
