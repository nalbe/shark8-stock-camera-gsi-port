# shark8-stock-camera-gsi-port - DKARCCamera for Blackview Shark8 (GSI)

Patched stock camera APK for MediaTek GSI (Shark8).
Restores **HDR**, **Night**, and **Panorama** modes on GSI where ArcSoft native libs are missing.

## What is this

Stock DKARCCamera uses ArcSoft HDR/Panorama/Night engine (libhdr_jni.so etc.).
On GSI, the system partition doesn't have these libraries and the app can't find them
because KernelSU overlay mounts are invisible to app processes (different mount namespace).

Solution: all required native libs are bundled **inside the APK** in `lib/arm64-v8a/` and loaded by the linker directly from the APK zip (no extraction needed).

## What was patched

### Smali patches (in `maind/`)

| File | Patch | Why |
|------|-------|-----|
| `com/mediatek/camera/common/utils/CameraUtil.smali` | `<clinit>`: forced `e=true` (`const/4 v0, 0x1`) | Feature flag for ArcSoft HDR support |
| `com/mediatek/camera/common/mode/photo/device/PhotoDeviceController.smali` | `<init>`: restored `if-eqz` for HDRDetectionEngine creation (removed bypass stub) | Engine now loads since libs are in APK |
| `com/mediatek/camera/common/mode/photo/device/PhotoDeviceController.smali` | `W()` method: added diagnostic log markers `RG_cfg`, `RG_cfg5` (can be removed for final build) | Debug session creation |
| `com/mediatek/camera/feature/mode/hdr/HDRCaptureResult.smali` | `f(IIII)`: added `onCaptureCompleted` log line with ev/exposureTime | Debug merge flow |
| `b3/a.smali` | `onImageAvailable`: added `RG_SUBMIT e= c=` logs before submit check | Debug submit condition |
| `androidx/appcompat/widget/j.smali` | `:pswitch_32f`: added `RG_J32F run merge entry` log | Debug merge execution |
| `e/b.smali` | `onClick` Google Lens branch: wrapped `startActivity` in try/catch for `ActivityNotFoundException` | Prevents crash when Google Lens app is not installed |

### Native libs in APK (`addlib/arm64-v8a/`)

| Library | Source | Purpose |
|---------|--------|---------|
| `libhdr_jni.so` | Stock system_ext/lib64 | ArcSoft HDR engine JNI bridge |
| `libarcsoft_hdr_detection.so` | Stock system_ext/lib64 | HDR scene detection |
| `libarcsoft_panorama.so` | Stock system_ext/lib64 | Panorama stitching engine |
| `libjni_burstpmk.so` | Stock system_ext/lib64 | Burst/panorama capture |
| `libhigh_dynamic_range.arcsoft.so` | Stock system_ext/lib64 | Core HDR processing |
| `libmpbase.so` | Stock system_ext/lib64 | ArcSoft dependency (MediaTek MP base) |

Stock libs extracted from `E:\shark8_super\MIO-KITCHEN-4.0.4-win\super\system\system\system_ext\lib64\`

## How to build

### Prerequisites

- Java 17+ (javac + java)
- Android SDK build-tools 34 (zipalign + apksigner)
- smali.jar (in project root)
- AOSP/stock DKARCCamera.apk (original, unsigned or self-signed)

### Build steps

```bash
# 1. Compile patched smali into classes.dex
java -jar smali.jar a maind -o build/classes-new.dex

# 2. Compile Repack.java
javac Repack.java

# 3. Repack APK (replaces dex, adds native libs from addlib/)
java -cp . Repack DKARCCamera.apk build/classes-new.dex build/DKARCCamera/classes2.dex DKARCCamera-raw.apk

# 4. Align and sign
zipalign -f -p 4 DKARCCamera-raw.apk DKARCCamera-align.apk
apksigner sign --ks dk.keystore --ks-pass pass:dkpass123 --ks-key-alias dk \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out DKARCCamera.apk DKARCCamera-align.apk
```

### Repack.java explained

Reads the original APK, strips all `.dex` and `META-INF/`, copies everything else,
then injects:
- `classes.dex` (patched smali)
- `classes2.dex` (BvProp framework patch)
- All `.so` files from `addlib/arm64-v8a/` into `lib/arm64-v8a/`

## Install

```bash
adb push DKARCCamera.apk /data/local/tmp/
adb shell pm install -r -d --user 0 /data/local/tmp/DKARCCamera.apk
```

## Known issues

- **Night mode quality is poor** - stock ArcSoft Night on this hardware is mediocre, not a patch issue
- **Google Lens button** - app is not installed on this device, now gracefully caught instead of crashing
- **Diagnostic markers** (`RG_cfg`, `RG_cfg5`, `RG_SUBMIT`, `RG_J32F`) still in code - remove before final release
