# MeckChat Android

Native Android application for **MeckChat** — Global Encrypted Peer-to-Peer Communication Platform.

## Overview

MeckChat Android is built completely natively using modern Android toolchains, Kotlin, and Jetpack Compose. It is designed to establish direct, authenticated, and encrypted P2P connections to other MeckChat clients across platforms.

## Technology Stack

- **Language**: Kotlin 1.9+
- **UI Framework**: Jetpack Compose (Material Design 3)
- **Target OS**: Android 8.0+ (API level 26+), targeted at API 34
- **Build System**: Gradle with Kotlin DSL (`build.gradle.kts`)
- **Networking & VPN**: Android `VpnService` backend for WireGuard P2P tunnels
- **Architecture**: Clean Architecture / MVVM with unidirectional data flow (UDF)

## Repository Ecosystem

MeckChat consists of three dedicated native platform clients designed to operate over the unified MeckChat Protocol:

| Platform | Repository | Core Stack |
| :--- | :--- | :--- |
| **Android** | [meckchat-android](https://github.com/Daksh159357/meckchat-android) | Kotlin + Jetpack Compose |
| **Linux** | [meckchat-linux](https://github.com/Daksh159357/meckchat-linux) | C++20 + Qt 6 |
| **Windows** | [meckchat-windows](https://github.com/Daksh159357/meckchat-windows) | C# + WinUI 3 / .NET 8 |

## Protocol Compatibility

All three clients implement the shared MeckChat specification:
- Device Identity & Cryptographic Keys
- Presence Discovery & Pairing Handshake
- WireGuard Public Key & Virtual IP Exchange
- End-to-End Encrypted Messaging & P2P Data Channels

## Project Structure

```
meckchat-android/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/com/meckchat/android/
│       │   ├── MainActivity.kt
│       │   ├── MeckChatApp.kt
│       │   ├── service/MeckVpnService.kt
│       │   └── ui/
│       └── res/
├── gradle/wrapper/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK Platform 34

### Building
```bash
./gradlew build
```

### Running on Device / Emulator
```bash
./gradlew installDebug
```

## License
MIT License
