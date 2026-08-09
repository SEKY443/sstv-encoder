# sstv-encoder

[![](https://jitpack.io/v/SEKY443/sstv-encoder.svg)](https://jitpack.io/#SEKY443/sstv-encoder)
[![build](https://github.com/SEKY443/sstv-encoder/actions/workflows/build.yml/badge.svg)](https://github.com/SEKY443/sstv-encoder/actions/workflows/build.yml)

A Kotlin encoder that turns a picture into a **Slow-Scan Television** transmission — the analogue
mode radio amateurs have used to send images over HF since the 1950s.

Feed it pixels, get back 16-bit mono PCM. Play that out of a speaker and any SSTV decoder
(Robot36, MMSSTV, QSSTV, an FT-8xx radio) will draw your picture back.

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

It was pulled out of [SSTV Alarm](https://github.com/SEKY443/SSTVAlarm), an Android alarm clock that
transmits the day's forecast as SSTV and makes you decode it to turn the alarm off.

## Supported modes

| Mode | VIS | Size | Time on air |
|---|---|---|---|
| `MARTIN_M1` | 44 | 320 × 256 | 114.3 s |
| `SCOTTIE_S1` | 60 | 320 × 256 | 109.6 s |

Both send colour in green-blue-red order, but they lay the sync pulse out differently within a
line — Martin opens each line with it, Scottie puts it mid-line before the red sweep.

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
    implementation("com.github.SEKY443:sstv-encoder:v0.1.0")
}
```

<details>
<summary>Groovy DSL</summary>

```groovy
repositories { maven { url 'https://jitpack.io' } }
dependencies { implementation 'com.github.SEKY443:sstv-encoder:v0.1.0' }
```
</details>

Any git tag, branch (`main-SNAPSHOT`) or commit hash works as the version.

Prefer to build it yourself? `./gradlew publishToMavenLocal` puts
`io.github.seky443:sstv-encoder:0.1.0` in your local Maven repository, or use a composite build —
see [Using it from an Android app](#using-it-from-an-android-app).

## Usage

### The picture

The encoder takes raw pixels: an `IntArray` in row-major order, each entry packed as `0xAARRGGBB`.
The alpha byte is ignored. The array has to be exactly one frame — `SstvMode.pixelCount`, i.e.
320 × 256 = 81,920 for both modes.

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
- **Each scan line** is a 1200 Hz sync pulse, a black porch, and three colour sweeps separated by
  short black gaps.
- **Within a sweep** each pixel gets a slice of time at a frequency between 1500 Hz (black) and
  2300 Hz (white), linear in the 8-bit channel value.

Two details matter more than they look:

**Phase continuity.** Each tone picks up the phase the last one left off at. Restarting the sine
at zero every segment puts a click at every boundary and smears the decoded picture.

**Cumulative timing.** Sample counts come from total elapsed time, not from rounding each segment
independently. A Martin 1 frame is over 245,000 segments; rounding each one on its own drifts the
line timing far enough to shear the received picture.

## Building

```bash
./gradlew build                # compile and test
./gradlew publishToMavenLocal  # install io.github.seky443:sstv-encoder:0.1.0 locally
```

Requires a JDK 17 or newer to run Gradle. The Java 17 toolchain the build asks for is downloaded
automatically if you do not have it, and the library itself targets Java 11.

The tests measure the actual waveform rather than trusting the constants — they count zero
crossings to check that the leader is 1900 Hz, that the VIS bits spell out the right code, that
white is 2300 Hz and black is 1500 Hz, that mid-grey lands at 1901.6 Hz, and that the sweeps come
out in green-blue-red order. The published line timings are pinned to the spec to the nanosecond,
because a few milliseconds of drift per line is the difference between a picture and a smear.

## Releasing

JitPack builds from git tags, so a release is just a tag:

```bash
# bump `version` in build.gradle.kts to match, then
git tag v0.1.0 && git push origin v0.1.0
```

The first time, open <https://jitpack.io/#SEKY443/sstv-encoder> and hit *Get it* on the tag to
trigger the build. After that JitPack picks up new tags on demand.

## Licence

MIT — see [LICENSE](LICENSE).