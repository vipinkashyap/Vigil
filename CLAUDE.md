# Vigil - Open Source Baby Monitor

## Project Overview

Privacy-first baby monitor that turns a spare Android phone into a streaming camera unit, viewable from iOS devices. No subscriptions, no cloud, no data collection.

**Hardware Setup:**
- Camera Unit: Galaxy Note 10 (Android 12) - spare phone
- Viewer: iPhones (family devices)
- Network: Local WiFi only (no internet required after setup)

## Architecture

```
┌─────────────────────────┐         ┌─────────────────────────┐
│   Android Camera Unit   │   LAN   │     iOS Viewer App      │
│   (This Repo - Kotlin)  │ ──────► │   (Separate Repo)       │
│                         │         │                         │
│  • CameraX capture      │  RTSP   │  • VLCKit playback      │
│  • RootEncoder RTSP     │ :8554   │  • Bonjour discovery    │
│  • Audio streaming      │         │  • Cry alert notifs     │
│  • TFLite cry detection │ ◄────── │  • Two-way audio        │
│  • mDNS broadcast       │  Audio  │  • Live Activities      │
│  • Foreground service   │ :8555   │                         │
└─────────────────────────┘         └─────────────────────────┘
```

## Tech Stack

| Layer      | Choice                  | Notes                           |
|------------|-------------------------|---------------------------------|
| Language   | Kotlin                  | 1.9.x                           |
| Min SDK    | 26 (Android 8.0)        | Note 10 runs Android 12         |
| Target SDK | 34 (Android 14)         |                                 |
| UI         | Jetpack Compose         | Material 3                      |
| Camera     | CameraX                 | 1.3.x                           |
| Streaming  | RootEncoder             | pedroSG94/RootEncoder 2.4.x     |
| DI         | Hilt                    | 2.50                            |
| Async      | Coroutines + Flow       | 1.7.x                           |
| ML         | TensorFlow Lite         | YAMNet for cry detection        |
| Discovery  | JmDNS                   | mDNS/Bonjour for auto-discovery |
| Settings   | DataStore               | Preferences                     |

## Project Structure

```
app/src/main/java/com/openbaby/monitor/
├── MainActivity.kt              # Entry point, permissions
├── MonitorApp.kt                # Hilt application
├── ui/
│   ├── theme/                   # Material 3 theming
│   ├── screens/
│   │   ├── HomeScreen.kt        # Status & start button
│   │   ├── MonitoringScreen.kt  # Live preview & controls
│   │   └── SettingsScreen.kt    # Configuration
│   ├── components/
│   │   ├── CameraPreview.kt     # CameraX preview composable
│   │   ├── AudioMeter.kt        # Visual audio level
│   │   └── StatusIndicator.kt   # Ready/not ready states
│   └── navigation/
│       └── NavGraph.kt          # Compose navigation
├── streaming/
│   ├── RtspStreamManager.kt     # RootEncoder RTSP server
│   ├── StreamConfig.kt          # Resolution, bitrate, etc.
│   └── ConnectionManager.kt     # Track connected viewers
├── service/
│   ├── MonitorService.kt        # Foreground service
│   ├── CameraController.kt      # Camera lifecycle
│   └── AudioAnalyzer.kt         # Audio level + cry detection
├── discovery/
│   └── MdnsPublisher.kt         # Bonjour/mDNS broadcast
├── ml/
│   └── CryDetector.kt           # TFLite inference
├── data/
│   ├── SettingsRepository.kt    # DataStore wrapper
│   └── MonitorState.kt          # App state
└── di/
    └── AppModule.kt             # Hilt modules
```

## Key Implementation Details

### RTSP Streaming (RootEncoder)

Using `RtspServerCamera2` from RootEncoder library:
- Built-in RTSP server on port 8554
- H.264 encoding with hardware acceleration
- Audio via AAC
- Stream URL: `rtsp://<device-ip>:8554/live`

```kotlin
// Core streaming setup
val rtspServer = RtspServerCamera2(surfaceView, connectChecker, port = 8554)
rtspServer.prepareVideo(1280, 720, 30, 2_000_000) // 720p, 30fps, 2Mbps
rtspServer.prepareAudio(44100, true, 128_000) // 44.1kHz stereo, 128kbps
rtspServer.startStream()
```

### Testing RTSP Stream

Once running, test with VLC on any device:
```bash
vlc rtsp://192.168.1.x:8554/live
```

Or on macOS:
```bash
ffplay rtsp://192.168.1.x:8554/live
```

### mDNS Discovery

Broadcasts service as `_vigil._tcp.local` so iOS app can auto-discover:
```kotlin
val serviceInfo = ServiceInfo.create(
    "_vigil._tcp.local.",
    "Vigil-${Build.MODEL}",
    8554,
    "path=/live"
)
jmdns.registerService(serviceInfo)
```

### Cry Detection

Using TensorFlow Lite with YAMNet model:
- Pre-trained on AudioSet (521 classes including "Baby crying")
- Runs inference on audio buffer every ~1 second
- Triggers notification to viewers when detected

## Development Milestones

### Milestone 1: Basic Streaming ✅ (Current)
- [x] Project structure
- [ ] Camera preview working
- [ ] RTSP server starts
- [ ] Viewable in VLC

### Milestone 2: Production Streaming
- [ ] Audio included in stream
- [ ] Night vision mode (torch + IR)
- [ ] Quality settings (480p/720p/1080p)
- [ ] Connection count display

### Milestone 3: Cry Detection
- [ ] TFLite model integration
- [ ] Local notification on cry
- [ ] Sensitivity settings
- [ ] Alert cooldown period

### Milestone 4: Network Features
- [ ] mDNS broadcast working
- [ ] Two-way audio (mic from viewer)
- [ ] Multiple viewer support

### Milestone 5: Polish
- [ ] Battery optimization
- [ ] Thermal management
- [ ] Crash reporting
- [ ] Overnight stability testing

## Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# View logs
adb logcat | grep -E "(Vigil|RootEncoder|CameraX)"

# Test RTSP stream
vlc rtsp://<phone-ip>:8554/live
```

## Common Issues

### "Camera in use by another app"
- Check no other camera apps are open
- Force stop any apps that might hold camera

### RTSP stream not connecting
- Ensure phone and viewer on same WiFi network
- Check firewall isn't blocking port 8554
- Verify IP address is correct

### High battery drain
- Reduce resolution to 720p
- Lower framerate to 15fps when possible
- Ensure screen is off (service runs in background)

## Related Repos

- **vigil-ios** (planned): Swift/SwiftUI viewer app for iPhone
- iOS app will use VLCKit for RTSP playback and Bonjour for discovery

## Resources

- [RootEncoder GitHub](https://github.com/pedroSG94/RootEncoder)
- [CameraX Documentation](https://developer.android.com/training/camerax)
- [TFLite Audio Classification](https://www.tensorflow.org/lite/examples/audio_classification/overview)
- [YAMNet Model](https://tfhub.dev/google/yamnet/1)

---

*Vigil v0.1 - Privacy-first baby monitoring*
