# Vigil

**Privacy-first baby monitor for Android**

Turn any spare Android phone into a secure baby monitor camera. Stream video to your iOS devices over your local network - no cloud, no subscriptions, no data collection.

## Features

- **RTSP Streaming** - Industry-standard protocol viewable in VLC or any RTSP client
- **AI Cry Detection** - On-device TensorFlow Lite model detects baby crying
- **Auto-Discovery** - iOS viewer app finds the camera automatically via Bonjour/mDNS
- **Night Mode** - Use the phone's flashlight for low-light monitoring
- **Background Operation** - Runs as a foreground service, works with screen off
- **Zero Cloud** - Everything stays on your local network

## Why Vigil?

Most baby monitors either cost hundreds of dollars or require cloud subscriptions that upload your nursery footage to third-party servers. Vigil uses a spare Android phone you already own and keeps all data on your local WiFi network.

| Feature | Cloud Monitors | Vigil |
|---------|---------------|-------|
| Monthly cost | $5-15/mo | Free |
| Privacy | Video uploaded | Local only |
| Setup | Account required | Plug and play |
| Works offline | No | Yes |

## Quick Start

### Requirements

- Android phone (Android 8.0+)
- Both devices on the same WiFi network

### Installation

1. Build from source (see below) or download from releases when available
2. Install on your spare Android phone
3. Grant camera and microphone permissions
4. Tap "Start Monitoring"

### Viewing the Stream

**On any device with VLC:**
```
rtsp://[phone-ip]:8554/
```

**On iOS (coming soon):**
The Vigil iOS companion app will auto-discover and connect to your camera.

## Architecture

```
┌─────────────────────────┐         ┌─────────────────────────┐
│   Android Camera Unit   │   LAN   │     Viewer Device       │
│                         │ ──────► │                         │
│  - CameraX capture      │  RTSP   │  - VLC / RTSP client    │
│  - RootEncoder RTSP     │ :8554   │  - iOS companion app    │
│  - TFLite cry detection │         │  - Any RTSP viewer      │
│  - mDNS broadcast       │         │                         │
└─────────────────────────┘         └─────────────────────────┘
```

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Camera | CameraX |
| Streaming | [RootEncoder](https://github.com/pedroSG94/RootEncoder) |
| Cry Detection | TensorFlow Lite + YAMNet |
| Discovery | JmDNS (mDNS/Bonjour) |
| DI | Hilt |

## Building from Source

```bash
# Clone the repository
git clone https://github.com/yourusername/vigil.git
cd vigil

# Build debug APK
./gradlew assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Configuration

Access settings through the gear icon to configure:

- **Resolution** - 480p, 720p, or 1080p
- **Framerate** - 15, 24, or 30 fps
- **Audio Bitrate** - 64, 128, or 256 kbps
- **Cry Detection** - Enable/disable with sensitivity control

## Troubleshooting

### Stream won't connect
- Ensure both devices are on the same WiFi network
- Check that port 8554 isn't blocked by your router
- Verify the IP address shown in the app

### High battery usage
- Lower resolution to 720p or 480p
- Reduce framerate to 15fps
- Keep the phone plugged in during overnight use

### Camera in use error
- Close other camera apps
- Restart the phone if issue persists

## Roadmap

- [x] Basic RTSP streaming
- [x] Foreground service
- [x] Settings UI
- [x] mDNS discovery
- [x] Cry detection model
- [ ] iOS companion app
- [ ] Two-way audio
- [ ] Night vision optimization
- [ ] Multiple camera support

## Contributing

Contributions are welcome! Please read our contributing guidelines before submitting PRs.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [RootEncoder](https://github.com/pedroSG94/RootEncoder) - RTSP streaming library
- [YAMNet](https://tfhub.dev/google/yamnet/1) - Audio classification model
- [JmDNS](https://github.com/jmdns/jmdns) - mDNS implementation for Java

---

**Vigil** - *Privacy-first baby monitoring*
