#include <iostream>
#include <thread>
#include <memory>
#include <atomic>
#include <array>
#include <map>
#include <mutex>
#include <string>
#include <sstream>
#include <ws2tcpip.h>
#include "../virtual-gamepad/NefariusBackend.h"
#include "../networking/ITransport.h"
#include "../networking/UdpTransport.h"
#include "../networking/AdbTcpTransport.h"

using namespace MobileController;

std::atomic<bool> g_Running{true};

// Handle Ctrl+C
BOOL WINAPI ConsoleHandler(DWORD signal) {
    if (signal == CTRL_C_EVENT) {
        std::cout << "\n[Main] Ctrl+C received, shutting down..." << std::endl;
        g_Running = false;
        return TRUE;
    }
    return FALSE;
}

struct DeviceSession {
    uint32_t deviceId;
    std::string ipStr;
    std::string wifiIp;
    Networking::TransportType activeTransport;
    int controllerId;
    std::array<uint32_t, 4> lastSequenceByTransport{};
    std::array<uint64_t, 4> lastPacketMsByTransport{};
    uint64_t lastPacketTimeMs;
    uint64_t lastActiveInputTimeMs;
    bool isNeutral;
    bool inputZeroed;
    bool isReconnecting = false;
    int batteryLevel = -1;
    int lastAvailableTransportsMask = 0;
    bool lastMouseLeftDown = false;
    bool lastMouseRightDown = false;
};

// Global transport toggles & slot policies
static bool g_enableWifi = true;
static bool g_enableUsb = true;
static bool g_autoRelease = true;
static uint64_t g_watchdogTimeoutMs = 15000;
static std::map<uint32_t, int> g_lastAssignedSlotByDeviceId;

int main() {
    std::cout << "Starting Mobile-to-PC Xbox Controller Backend (Multiplayer)..." << std::endl;

    if (!SetConsoleCtrlHandler(ConsoleHandler, TRUE)) {
        std::cerr << "[Main] Could not set control handler." << std::endl;
        return 1;
    }

    // 1. Initialize Virtual Gamepad Backend
    auto backend = std::make_unique<VirtualGamepad::NefariusBackend>();
    if (!backend->Initialize()) {
        std::cerr << "[Main] Failed to initialize virtual gamepad backend." << std::endl;
        return 1;
    }

    // State tracking for multiplayer
    std::map<uint32_t, DeviceSession> sessions;
    std::mutex sessionMutex;
    constexpr uint64_t NEUTRAL_TIMEOUT_MS = 300;

    LARGE_INTEGER qpcFreq;
    QueryPerformanceFrequency(&qpcFreq);



    // 2. Setup Transports
    std::vector<std::unique_ptr<Networking::ITransport>> transports;
    transports.push_back(std::make_unique<Networking::UdpTransport>(0));
    transports.push_back(std::make_unique<Networking::AdbTcpTransport>());

    // Command listener thread for IPC
    std::thread commandThread([&backend, &sessions, &sessionMutex, &transports, &qpcFreq]() {
        std::string line;
        while (g_Running && std::getline(std::cin, line)) {
            if (line.rfind("MOVE ", 0) == 0) { // starts with "MOVE "
                std::istringstream iss(line.substr(5));
                std::string targetKey;
                int newSlot;
                if (iss >> targetKey >> newSlot) {
                    std::lock_guard<std::mutex> lock(sessionMutex);
                    auto it = sessions.end();
                    for (auto i = sessions.begin(); i != sessions.end(); ++i) {
                        if (i->second.ipStr == targetKey || i->second.wifiIp == targetKey || std::to_string(i->first) == targetKey) {
                            it = i;
                            break;
                        }
                    }
                    if (it != sessions.end()) {
                        int oldSlot = it->second.controllerId;
                        if (oldSlot != newSlot) {
                            // Check if new slot is taken
                            bool taken = false;
                            for (auto const& [k, v] : sessions) {
                                if (v.controllerId == newSlot) {
                                    taken = true;
                                    break;
                                }
                            }
                            
                            if (!backend->IsSlotAvailableForVirtual(newSlot)) {
                                std::cout << "[Main] Cannot move: slot " << newSlot << " is occupied by System." << std::endl;
                            } else if (!taken) {
                                backend->DisconnectController(oldSlot);
                                int assigned = backend->ConnectController(newSlot);
                                if (assigned == newSlot) {
                                    it->second.controllerId = newSlot;
                                    std::cout << "[Main] Moved device " << targetKey << " to slot " << newSlot << std::endl;
                                } else {
                                    std::cout << "[Main] Failed to move device " << targetKey << " to slot " << newSlot << std::endl;
                                    sessions.erase(it);
                                }
                            } else {
                                std::cout << "[Main] Cannot move: slot " << newSlot << " is already taken." << std::endl;
                            }
                        }
                    }
                }
            } else if (line.rfind("RELOAD_DEVICE ", 0) == 0) {
            std::istringstream iss(line.substr(14));
            uint32_t targetDeviceId;
            std::string slotId;
            if (iss >> targetDeviceId >> slotId) {
                std::lock_guard<std::mutex> lock(sessionMutex);
                auto it = sessions.find(targetDeviceId);
                if (it != sessions.end()) {
                    auto& s = it->second;
                    size_t idx = 0;
                    if (s.activeTransport == Networking::TransportType::UDP) idx = 0;
                    else if (s.activeTransport == Networking::TransportType::USB_AOA) idx = 1;
                    else continue;

                    if (idx < transports.size()) {
                        transports[idx]->SendReload(slotId);
                        std::cout << "[Main] Sent reload command to device " << targetDeviceId << " (" << slotId << ")" << std::endl;
                    }
                }
            }
            } else if (line.rfind("SWAP ", 0) == 0) {
                std::istringstream iss(line.substr(5));
                std::string ip1, ip2;
                int slot1, slot2;
                if (iss >> ip1 >> slot1 >> ip2 >> slot2) {
                    std::lock_guard<std::mutex> lock(sessionMutex);
                    // Find both sessions
                    auto it1 = sessions.end(), it2 = sessions.end();
                    for (auto i = sessions.begin(); i != sessions.end(); ++i) {
                        if (i->second.ipStr == ip1 || i->second.wifiIp == ip1 || std::to_string(i->first) == ip1) it1 = i;
                        if (i->second.ipStr == ip2 || i->second.wifiIp == ip2 || std::to_string(i->first) == ip2) it2 = i;
                    }
                    if (it1 != sessions.end() && it2 != sessions.end()) {
                        // Disconnect both, reconnect in swapped order
                        backend->DisconnectController(it1->second.controllerId);
                        backend->DisconnectController(it2->second.controllerId);
                        int a1 = backend->ConnectController(slot1);
                        int a2 = backend->ConnectController(slot2);
                        if (a1 == slot1 && a2 == slot2) {
                            it1->second.controllerId = slot1;
                            it2->second.controllerId = slot2;
                            std::cout << "[Main] Swapped device " << ip1 << " to slot " << slot1 << " and " << ip2 << " to slot " << slot2 << std::endl;
                        } else {
                            std::cout << "[Main] Swap failed for " << ip1 << " and " << ip2 << std::endl;
                        }
                    }
                }
                } else if (line.rfind("TOGGLE ", 0) == 0) {
                std::istringstream iss(line);
                std::string cmd, type, valStr;
                if (iss >> cmd >> type >> valStr) {
                    bool val = (valStr == "1" || valStr == "true");
                    if (type == "wifi") {
                        g_enableWifi = val;
                        std::cout << "[Main] Wi-Fi transport enabled: " << val << std::endl;
                    } else if (type == "usb") {
                        g_enableUsb = val;
                        std::cout << "[Main] USB transport enabled: " << val << std::endl;
                    }
                    
                    // Handle any active sessions on the disabled transport
                    if (!val) {
                        std::lock_guard<std::mutex> lock(sessionMutex);
                        LARGE_INTEGER now;
                        QueryPerformanceCounter(&now);
                        uint64_t nowMs = (now.QuadPart * 1000) / qpcFreq.QuadPart;
                        
                        for (auto it = sessions.begin(); it != sessions.end(); ) {
                            DeviceSession& s = it->second;
                            Networking::TransportType disabledType = (type == "wifi") ? Networking::TransportType::UDP : Networking::TransportType::USB_AOA;
                            
                            if (s.activeTransport == disabledType) {
                                // Check if another enabled transport is currently alive
                                bool otherAlive = false;
                                for (size_t i = 1; i <= 3; i++) {
                                    if (i == static_cast<size_t>(disabledType)) continue;
                                    if (i == static_cast<size_t>(Networking::TransportType::UDP) && !g_enableWifi) continue;
                                    if (i == static_cast<size_t>(Networking::TransportType::USB_AOA) && !g_enableUsb) continue;
                                    
                                    if (s.lastPacketMsByTransport[i] > 0 && (nowMs - s.lastPacketMsByTransport[i] < 2500)) {
                                        otherAlive = true;
                                        s.activeTransport = static_cast<Networking::TransportType>(i);
                                        std::cout << "[Main] Device " << s.deviceId << " switched to transport " << i << " due to toggle." << std::endl;
                                        break;
                                    }
                                }
                                
                                if (!otherAlive) {
                                    if (g_autoRelease && g_watchdogTimeoutMs == 0) {
                                        std::cout << "[Main] Disconnecting device " << s.deviceId << " due to transport toggle (Instant Release)." << std::endl;
                                        std::cout << "[Watchdog] Connection timeout for slot " << s.controllerId << ". Disconnecting controller." << std::endl;
                                        backend->DisconnectController(s.controllerId);
                                        it = sessions.erase(it);
                                        continue;
                                    } else {
                                        if (!s.inputZeroed) {
                                            Protocol::ControllerStatePacket neutral{};
                                            neutral.magic = Protocol::PROTOCOL_MAGIC;
                                            neutral.version = Protocol::PROTOCOL_VERSION;
                                            backend->UpdateState(s.controllerId, neutral);
                                            s.inputZeroed = true;
                                        }
                                        if (!s.isReconnecting) {
                                            s.isReconnecting = true;
                                            std::cout << "[Main] Device " << s.deviceId << " status: reconnecting" << std::endl;
                                        }
                                    }
                                }
                            }
                            ++it;
                        }
                    }
                }
            } else if (line.rfind("CONFIG ", 0) == 0) {
                std::istringstream iss(line);
                std::string cmd, key, mode;
                uint64_t timeoutVal = 15000;
                if (iss >> cmd >> key >> mode) {
                    if (key == "release_mode") {
                        if (mode == "manual") {
                            g_autoRelease = false;
                            std::cout << "[Main] Slot release mode set to manual (indefinite hold)." << std::endl;
                        } else {
                            g_autoRelease = true;
                            if (iss >> timeoutVal) {
                                g_watchdogTimeoutMs = timeoutVal;
                            } else {
                                g_watchdogTimeoutMs = 15000;
                            }
                            std::cout << "[Main] Slot release mode set to auto (" << g_watchdogTimeoutMs << "ms)." << std::endl;
                        }
                    }
                }
            } else if (line.rfind("KICK ", 0) == 0 || line.rfind("DISCONNECT ", 0) == 0) {
                std::string targetKey = line.substr(line.find(' ') + 1);
                std::lock_guard<std::mutex> lock(sessionMutex);
                for (auto it = sessions.begin(); it != sessions.end(); ) {
                    bool match = (it->second.ipStr == targetKey || it->second.wifiIp == targetKey);
                    try {
                        uint32_t targetId = (uint32_t)std::stoul(targetKey);
                        if (it->second.deviceId == targetId) match = true;
                    } catch(...) {}
                    if (match) {
                        std::cout << "[Main] Disconnecting device " << it->second.deviceId << " (" << targetKey << ") on slot " << it->second.controllerId << std::endl;
                        std::cout << "[Watchdog] Connection timeout for slot " << it->second.controllerId << ". Disconnecting controller." << std::endl;
                        backend->DisconnectController(it->second.controllerId);
                        it = sessions.erase(it);
                    } else {
                        ++it;
                    }
                }
            } else if (line == "EXIT") {
                g_Running = false;
                break;
            }
        }
        // Do NOT set g_Running = false here. When Electron's stdin pipe closes
        // (e.g., during a restart or window close), the backend should keep running
        // until explicitly killed via signal or EXIT command.
    });
    commandThread.detach(); // Detach so it doesn't block shutdown

    std::atomic<uint8_t> slotLargeMotor[4]{0, 0, 0, 0};
    std::atomic<uint8_t> slotSmallMotor[4]{0, 0, 0, 0};
    uint64_t lastRumbleRefreshMs = 0;

    backend->SetRumbleCallback([&sessions, &sessionMutex, &transports, &slotLargeMotor, &slotSmallMotor](int controllerId, uint8_t largeMotor, uint8_t smallMotor) {
        if (controllerId >= 0 && controllerId < 4) {
            slotLargeMotor[controllerId] = largeMotor;
            slotSmallMotor[controllerId] = smallMotor;
        }
        std::lock_guard<std::mutex> lock(sessionMutex);
        for (auto const& [deviceId, s] : sessions) {
            if (s.controllerId == controllerId) {
                size_t idx = 0;
                if (s.activeTransport == Networking::TransportType::UDP) idx = 0;
                else if (s.activeTransport == Networking::TransportType::USB_AOA) idx = 1;
                else continue;
                
                if (idx < transports.size()) {
                    transports[idx]->SendRumble(largeMotor, smallMotor);
                }
            }
        }
    });

    auto packetHandler = [&backend, &sessions, &sessionMutex, &qpcFreq](const Protocol::ControllerStatePacket& packet, uint64_t /*transportArrivalTimeMs*/, uint32_t senderIp, uint16_t senderPort, Networking::TransportType type) {
        if (type == Networking::TransportType::UDP && !g_enableWifi) return;
        if (type == Networking::TransportType::USB_AOA && !g_enableUsb) return;

        LARGE_INTEGER now;
        QueryPerformanceCounter(&now);
        uint64_t arrivalTimeMs = (now.QuadPart * 1000) / qpcFreq.QuadPart;
        uint32_t deviceId = packet.deviceId;

        std::lock_guard<std::mutex> lock(sessionMutex);
        
        if (packet.flags & 0x8000) {
            auto it = sessions.find(deviceId);
            if (it != sessions.end()) {
                std::cout << "[Main] Graceful disconnect received from device " << deviceId << " on slot " << it->second.controllerId << std::endl;
                std::cout << "[Watchdog] Connection timeout for slot " << it->second.controllerId << ". Disconnecting controller." << std::endl;
                backend->DisconnectController(it->second.controllerId);
                sessions.erase(it);
            }
            return;
        }
        
        auto it = sessions.find(deviceId);
        if (it == sessions.end()) {
            // New connection (or reconnecting)
            int preferredSlot = -1;
            auto prefIt = g_lastAssignedSlotByDeviceId.find(deviceId);
            if (prefIt != g_lastAssignedSlotByDeviceId.end()) {
                int candidate = prefIt->second;
                bool taken = false;
                for (auto const& [k, v] : sessions) {
                    if (v.controllerId == candidate) { taken = true; break; }
                }
                if (!taken && backend->IsSlotAvailableForVirtual(candidate)) {
                    preferredSlot = candidate;
                }
            }

            int newId = -1;
            if (preferredSlot != -1) {
                newId = preferredSlot;
            } else {
                for (int i = 0; i < 4; ++i) {
                    if (!backend->IsSlotAvailableForVirtual(i)) continue;
                    
                    bool taken = false;
                    for (auto const& [k, v] : sessions) {
                        if (v.controllerId == i) {
                            taken = true;
                            break;
                        }
                    }
                    if (!taken) {
                        newId = i;
                        break;
                    }
                }
            }
            
            if (newId != -1) {
                g_lastAssignedSlotByDeviceId[deviceId] = newId;
                DeviceSession newSession = {};
                newSession.deviceId = deviceId;
                newSession.activeTransport = type;
                newSession.controllerId = newId;
                newSession.lastSequenceByTransport[static_cast<size_t>(type)] = packet.sequence;
                newSession.lastPacketTimeMs = arrivalTimeMs;
                newSession.lastActiveInputTimeMs = arrivalTimeMs;
                newSession.isNeutral = false;
                newSession.inputZeroed = false;
                newSession.isReconnecting = false;
                newSession.batteryLevel = packet.flags & 0xFF;
                
                newSession.lastPacketMsByTransport[static_cast<size_t>(type)] = arrivalTimeMs;
                newSession.lastAvailableTransportsMask = (1 << static_cast<size_t>(type));
                
                char ipStr[INET_ADDRSTRLEN] = "Unknown";
                if (type == Networking::TransportType::UDP && senderIp != 0) {
                    inet_ntop(AF_INET, &senderIp, ipStr, INET_ADDRSTRLEN);
                    newSession.ipStr = ipStr;
                    newSession.wifiIp = ipStr;
                } else if (type == Networking::TransportType::USB_AOA) {
                    newSession.ipStr = "USB";
                }

                sessions[deviceId] = newSession;
                std::cout << "[Main] New device connected from " << newSession.ipStr << " (ID: " << deviceId << "). Assigned to slot " << newId << std::endl;
                std::cout << "[Main] Device " << deviceId << " available transports: " << newSession.lastAvailableTransportsMask << std::endl;
                std::cout << "[Main] Device " << newSession.ipStr << " (ID: " << deviceId << ") battery updated: " << (packet.flags & 0xFF) << "%" << std::endl;
                std::cout << "[Main] Device " << deviceId << " status: active" << std::endl;
                
                backend->ConnectController(newId);
                
                // Apply the packet that created the session as well. Otherwise the
                // first input is discarded, which is particularly noticeable on a
                // newly established Bluetooth or USB connection.
                backend->UpdateState(newId, packet);
            } else {
                std::cout << "[Main] Connection rejected from device " << deviceId << ": server is full (4/4)" << std::endl;
                return;
            }
        } else {
            // Existing connection
            DeviceSession& s = it->second;
            
            const auto transportIndex = static_cast<size_t>(type);
            if (transportIndex >= s.lastSequenceByTransport.size()) return;
            
            s.lastPacketMsByTransport[transportIndex] = arrivalTimeMs;
            s.lastPacketTimeMs = arrivalTimeMs;
            
            if (s.isReconnecting) {
                s.isReconnecting = false;
                std::cout << "[Main] Device " << s.deviceId << " status: active" << std::endl;
            }
            
            int currentMask = 0;
            for (size_t i = 1; i <= 3; i++) {
                if (s.lastPacketMsByTransport[i] > 0 && (arrivalTimeMs - s.lastPacketMsByTransport[i] < 2500)) {
                    currentMask |= (1 << i);
                }
            }
            if (currentMask != s.lastAvailableTransportsMask) {
                s.lastAvailableTransportsMask = currentMask;
                std::cout << "[Main] Device " << deviceId << " available transports: " << currentMask << std::endl;
            }

            // Validate a transport's stream before allowing it to become active.
            uint32_t& lastSequence = s.lastSequenceByTransport[transportIndex];
            if (packet.sequence <= lastSequence) {
                // If sequence difference is small, it's out of order / duplicate.
                // If difference is large (>1000) or sequence wrapped/reset on reconnect, accept it.
                if ((lastSequence - packet.sequence) < 1000 && lastSequence < 0xFFFFFF00) {
                    return; // Out of order or duplicate for this transport
                }
            }
            lastSequence = packet.sequence;

            auto getPriority = [](Networking::TransportType t) {
                switch(t) {
                    // The active path is selected strictly by latency preference.
                    case Networking::TransportType::USB_AOA: return 3;
                    case Networking::TransportType::UDP: return 2;
                    default: return 0;
                }
            };

            if (type != s.activeTransport) {
                LARGE_INTEGER now;
                QueryPerformanceCounter(&now);
                uint64_t currentTime = (now.QuadPart * 1000) / qpcFreq.QuadPart;
                if (getPriority(type) > getPriority(s.activeTransport) || (currentTime - s.lastPacketTimeMs) > 50) {
                    s.activeTransport = type;
                    std::cout << "[Main] Device " << deviceId << " switched to transport " << (int)type << std::endl;
                } else if (getPriority(type) < getPriority(s.activeTransport)) {
                    return; // Drop packet, active transport is higher priority and healthy
                }
            }
            
            s.inputZeroed = false;
            
            int incomingBattery = packet.flags & 0xFF;
            if (incomingBattery != s.batteryLevel && incomingBattery >= 0 && incomingBattery <= 100) {
                s.batteryLevel = incomingBattery;
                std::cout << "[Main] Device " << s.ipStr << " (ID: " << deviceId << ") battery updated: " << incomingBattery << "%" << std::endl;
            }
            
            if (type == Networking::TransportType::UDP && senderIp != 0) {
                char ipBuf[INET_ADDRSTRLEN];
                inet_ntop(AF_INET, &senderIp, ipBuf, INET_ADDRSTRLEN);
                s.ipStr = ipBuf;
                if (s.wifiIp.empty()) s.wifiIp = ipBuf;
            } else if (type == Networking::TransportType::USB_AOA) {
                s.ipStr = "USB";
            }

            // Check for Mouse Touchpad Events
            if (packet.flags & 0x00010000) { // FLAG_MOUSE_EVENT
                INPUT input = {0};
                input.type = INPUT_MOUSE;
                input.mi.dx = packet.rightStickX;
                input.mi.dy = packet.rightStickY;
                input.mi.dwFlags = MOUSEEVENTF_MOVE;
                
                bool leftDown = (packet.flags & 0x00020000) != 0;
                if (leftDown && !s.lastMouseLeftDown) {
                    input.mi.dwFlags |= MOUSEEVENTF_LEFTDOWN;
                } else if (!leftDown && s.lastMouseLeftDown) {
                    input.mi.dwFlags |= MOUSEEVENTF_LEFTUP;
                }
                s.lastMouseLeftDown = leftDown;

                bool rightDown = (packet.flags & 0x00040000) != 0;
                if (rightDown && !s.lastMouseRightDown) {
                    input.mi.dwFlags |= MOUSEEVENTF_RIGHTDOWN;
                } else if (!rightDown && s.lastMouseRightDown) {
                    input.mi.dwFlags |= MOUSEEVENTF_RIGHTUP;
                }
                s.lastMouseRightDown = rightDown;

                SendInput(1, &input, sizeof(INPUT));

                // Feed neutral right-stick to gamepad so camera doesn't spin while moving mouse
                Protocol::ControllerStatePacket gamepadPacket = packet;
                gamepadPacket.rightStickX = 0;
                gamepadPacket.rightStickY = 0;
                backend->UpdateState(s.controllerId, gamepadPacket);
            } else {
                if (s.lastMouseLeftDown) {
                    INPUT input = {0}; input.type = INPUT_MOUSE; input.mi.dwFlags = MOUSEEVENTF_LEFTUP; SendInput(1, &input, sizeof(INPUT));
                    s.lastMouseLeftDown = false;
                }
                if (s.lastMouseRightDown) {
                    INPUT input = {0}; input.type = INPUT_MOUSE; input.mi.dwFlags = MOUSEEVENTF_RIGHTUP; SendInput(1, &input, sizeof(INPUT));
                    s.lastMouseRightDown = false;
                }
                backend->UpdateState(s.controllerId, packet);
            }
            
            bool inputIsNeutral = (packet.buttons == 0 &&
                packet.leftTrigger == 0 && packet.rightTrigger == 0 &&
                abs(packet.leftStickX) < 1000 && abs(packet.leftStickY) < 1000 &&
                abs(packet.rightStickX) < 1000 && abs(packet.rightStickY) < 1000);

            if (inputIsNeutral) {
                if (!s.isNeutral && (arrivalTimeMs - s.lastActiveInputTimeMs) > 5000) {
                    s.isNeutral = true;
                }
            } else {
                s.isNeutral = false;
                s.lastActiveInputTimeMs = arrivalTimeMs;
            }
        }
    };

    auto disconnectHandler = [&backend, &sessions, &sessionMutex](Networking::TransportType type) {
        std::lock_guard<std::mutex> lock(sessionMutex);
        LARGE_INTEGER now;
        LARGE_INTEGER freq;
        QueryPerformanceCounter(&now);
        QueryPerformanceFrequency(&freq);
        uint64_t nowMs = (now.QuadPart * 1000) / freq.QuadPart;

        for (auto it = sessions.begin(); it != sessions.end(); ) {
            DeviceSession& s = it->second;
            
            const auto droppedIndex = static_cast<size_t>(type);
            if (droppedIndex < s.lastPacketMsByTransport.size()) {
                s.lastPacketMsByTransport[droppedIndex] = 0;
            }

            int currentMask = 0;
            for (size_t i = 1; i <= 3; i++) {
                if (nowMs - s.lastPacketMsByTransport[i] < 1500) {
                    currentMask |= (1 << i);
                }
            }
            if (currentMask != s.lastAvailableTransportsMask) {
                s.lastAvailableTransportsMask = currentMask;
                std::cout << "[Main] Device " << s.deviceId << " available transports: " << currentMask << std::endl;
            }

            if (s.activeTransport == type) {
                // Check if another transport is currently alive
                bool otherAlive = false;
                for (size_t i = 1; i <= 3; i++) {
                    if (i != static_cast<size_t>(type) && (nowMs - s.lastPacketMsByTransport[i] < 1500)) {
                        otherAlive = true;
                        s.activeTransport = static_cast<Networking::TransportType>(i);
                        std::cout << "[Main] Device " << s.deviceId << " switched to transport " << i << std::endl;
                        break;
                    }
                }
                if (!otherAlive) {
                    if (g_autoRelease && g_watchdogTimeoutMs == 0) {
                        std::cout << "[Main] Transport disconnected for slot " << s.controllerId << " (Instant Release). Disconnecting controller." << std::endl;
                        std::cout << "[Watchdog] Connection timeout for slot " << s.controllerId << ". Disconnecting controller." << std::endl;
                        backend->DisconnectController(s.controllerId);
                        it = sessions.erase(it);
                        continue;
                    } else {
                        if (!s.inputZeroed) {
                            Protocol::ControllerStatePacket neutral{};
                            neutral.magic = Protocol::PROTOCOL_MAGIC;
                            neutral.version = Protocol::PROTOCOL_VERSION;
                            backend->UpdateState(s.controllerId, neutral);
                            s.inputZeroed = true;
                        }
                        if (!s.isReconnecting) {
                            s.isReconnecting = true;
                            std::cout << "[Main] Device " << s.deviceId << " status: reconnecting" << std::endl;
                        }
                    }
                }
            }
            ++it;
        }
    };

    for (auto& t : transports) {
        t->SetDisconnectCallback(disconnectHandler);
        if (!t->Start(packetHandler)) {
            std::cerr << "[Main] Failed to start a transport." << std::endl;
        }
    }

    std::cout << "[Main] Backend running. Press Ctrl+C to exit." << std::endl;

    // Main loop (Watchdog)
    while (g_Running) {
        {
            std::lock_guard<std::mutex> lock(sessionMutex);
            
            LARGE_INTEGER now;
            QueryPerformanceCounter(&now);
            uint64_t currentTime = (now.QuadPart * 1000) / qpcFreq.QuadPart;

            for (auto it = sessions.begin(); it != sessions.end(); ) {
                DeviceSession& s = it->second;
                if (currentTime >= s.lastPacketTimeMs) {
                    uint64_t diff = currentTime - s.lastPacketTimeMs;
                    
                    if (diff > 2500 && !s.isReconnecting) {
                        s.isReconnecting = true;
                        std::cout << "[Main] Device " << s.deviceId << " status: reconnecting" << std::endl;
                    }

                    if (g_autoRelease && ((g_watchdogTimeoutMs == 0 && diff > 500) || (g_watchdogTimeoutMs > 0 && diff > g_watchdogTimeoutMs))) {
                        std::cout << "[Watchdog] Connection timeout for slot " << s.controllerId << ". Disconnecting controller." << std::endl;
                        backend->DisconnectController(s.controllerId);
                        it = sessions.erase(it);
                        continue;
                    } else if (!s.isNeutral && s.lastPacketTimeMs > 0 && diff > NEUTRAL_TIMEOUT_MS && !s.inputZeroed) {
                        Protocol::ControllerStatePacket neutral{};
                        neutral.magic = Protocol::PROTOCOL_MAGIC;
                        neutral.version = Protocol::PROTOCOL_VERSION;
                        backend->UpdateState(s.controllerId, neutral);
                        s.inputZeroed = true;
                        std::cout << "[Watchdog] Inputs zeroed for slot " << s.controllerId 
                                  << " (no packets for " << diff << "ms)" << std::endl;
                    }
                }
                ++it;
            }

            // Periodic rumble refresh for active sustained game vibrations
            if (currentTime - lastRumbleRefreshMs >= 100) {
                lastRumbleRefreshMs = currentTime;
                for (auto const& [deviceId, s] : sessions) {
                    int cId = s.controllerId;
                    if (cId >= 0 && cId < 4) {
                        uint8_t l = slotLargeMotor[cId];
                        uint8_t sm = slotSmallMotor[cId];
                        if (l > 0 || sm > 0) {
                            size_t idx = 0;
                            if (s.activeTransport == Networking::TransportType::UDP) idx = 0;
                            else if (s.activeTransport == Networking::TransportType::USB_AOA) idx = 1;
                            else continue;
                            if (idx < transports.size()) {
                                transports[idx]->SendRumble(l, sm);
                            }
                        }
                    }
                }
            }
        }

        std::this_thread::sleep_for(std::chrono::milliseconds(50));
    }

    // Cleanup
    for (auto& t : transports) {
        t->Stop();
    }
    
    // Command thread cleanup is tricky because getline is blocking, but when process exits it dies.
    // For proper cleanup, we'd close stdin, but this is fine for PoC.

    backend->Shutdown();

    std::cout << "[Main] Shutdown complete." << std::endl;
    return 0;
}
