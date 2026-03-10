# Reflex LED Control

An Android application for remote control of LanBox-based DMX lighting systems. This app provides a modern, Jetpack Compose-based interface to manage RGB LED strips and lighting cues.

## Features

- **Real-time Connection**: Connect to LanBox hardware via IP address and password.
- **Cue List Management**: Browse and select available cue lists from the LanBox.
- **Playback Control**: Play, pause, and skip between steps in the active cue list.
- **Speed Control**: Adjust the playback speed of the current sequence.
- **RGB Strip Editor**:
    - Interactive LED grid editor.
    - Customizable color palette.
    - Real-time DMX preview of changes.
    - Shift patterns left/right across the LED strip.
    - Adjustable fixture settings (number of LEDs, DMX start channel).

## Technical Details

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: MVVM (Model-ViewModel)
- **Networking**: TCP Sockets for LanBox ASCII protocol communication.
- **Concurrency**: Kotlin Coroutines for non-blocking I/O operations.

## Setup

1.  Clone the repository.
2.  Open the project in Android Studio.
3.  Ensure your Android device is on the same network as the LanBox.
4.  Enter the LanBox IP address and password in the app settings.

## License

(c) 2023-2026 flowxperts GmbH
