# Mobile-to-PC Xbox Controller Architecture

## Overview
This document outlines the architecture for the mobile-to-PC Xbox controller application. The primary goal is to turn an Android phone into a low-latency virtual Xbox/XInput controller for Windows PCs.

## Core Components
1. **Windows C++ Backend:** Handles low-latency network communication, input processing, and virtual controller emulation.
2. **Android Application:** Acts as the primary user interface during gaming, reading touch/sensor inputs and sending them to the PC.
3. **Desktop Dashboard (Electron):** Optional management interface for configuring controllers, profiles, and backend settings via IPC.

## Dependencies and Rationale

### Nefarius VirtualPad (Virtual Gamepad Emulation Framework)
* **Dependency:** [Nefarius VirtualPad](https://github.com/nefarius/VirtualPad) (via `ViGEmClient` wrapper or direct integration).
* **Why we use it:** To create virtual Xbox 360 controllers on Windows so games natively recognize the input.
* **Alternative considered:** The original ViGEmBus. 
* **Maintenance status:** ViGEmBus is officially archived due to trademark disputes. VirtualPad is its modern, actively maintained successor by the same developer.
* **License:** MIT / Dual-licensed (check Nefarius official terms).
* **Potential replacement:** Microsoft's Virtual HID Framework (VHF) directly, but it requires significantly more boilerplate kernel-mode or UMDF driver coding.

### Network Protocol (UDP)
* **Dependency:** Native sockets (Winsock on Windows, `java.net.DatagramSocket` on Android).
* **Why we use it:** Low latency. Real-time controller inputs are ephemeral; if a packet is lost, we care more about the *next* state than retransmitting the lost one. TCP would introduce head-of-line blocking.
* **Alternative considered:** TCP or WebSockets.
* **Maintenance status:** Core OS networking libraries, extremely stable.
* **Potential replacement:** WebRTC data channels if traversing NAT, but for local Wi-Fi, raw UDP is best.

### Build System (CMake)
* **Dependency:** CMake 3.20+
* **Why we use it:** Industry standard for C++ projects, easily integrates with Visual Studio, Ninja, and Android NDK.

## Project Structure
```text
/mobile-controller
├── android/            # Android Kotlin/Java App
├── windows/            # C++ Core Backend
├── desktop/            # Electron Dashboard (Future)
├── shared/             # Protocol and schema definitions
├── tests/              # Automated testing
├── docs/               # Documentation
└── scripts/            # Build and utility scripts
```
