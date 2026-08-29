#pragma once

#include "IVirtualGamepadBackend.h"
#include <iostream>
#define WIN32_LEAN_AND_MEAN
#include <Windows.h>
#include <Xinput.h>
#pragma comment(lib, "xinput.lib")
#include <ViGEm/Client.h>

namespace MobileController {
namespace VirtualGamepad {

    // Static flag to suppress RUMBLE output during initialization.
    // ViGEm fires rumble callbacks immediately when controllers are plugged in,
    // which floods stdout and corrupts the Electron line parser.
    static inline bool s_initialized = false;

    class NefariusBackend : public IVirtualGamepadBackend {
    public:
        void SetRumbleCallback(RumbleCallback cb) override { m_rumbleCallback = std::move(cb); }

        static void CALLBACK ControllerNotification(
            PVIGEM_CLIENT Client,
            PVIGEM_TARGET Target,
            UCHAR LargeMotor,
            UCHAR SmallMotor,
            UCHAR LedNumber,
            LPVOID UserData
        ) {
            if (!s_initialized) return; // Suppress during init
            
            NefariusBackend* self = static_cast<NefariusBackend*>(UserData);
            if (self && self->m_rumbleCallback) {
                int cId = -1;
                for (int i = 0; i < 4; ++i) {
                    if (self->targets[i] == Target) { cId = i; break; }
                }
                if (cId != -1) {
                    self->m_rumbleCallback(cId, LargeMotor, SmallMotor);
                }
            }
        }

        NefariusBackend() : client(nullptr) {
            for (int i = 0; i < 4; ++i) {
                targets[i] = nullptr;
            }
        }
        
        ~NefariusBackend() override {
            Shutdown();
        }

        bool Initialize() override {
            client = vigem_alloc();
            if (client == nullptr) {
                std::cerr << "[ViGEm] Failed to allocate client." << std::endl;
                return false;
            }

            const auto retval = vigem_connect(client);
            if (!VIGEM_SUCCESS(retval)) {
                std::cerr << "[ViGEm] Failed to connect to bus driver. Ensure ViGEmBus is installed. Error: " << std::hex << retval << std::endl;
                vigem_free(client);
                client = nullptr;
                return false;
            }

            // Smart XInput Detection: Check which slots are already occupied by physical controllers.
            for (DWORD i = 0; i < 4; ++i) {
                XINPUT_STATE state;
                ZeroMemory(&state, sizeof(XINPUT_STATE));
                if (XInputGetState(i, &state) == ERROR_SUCCESS) {
                    std::cout << "[Main] System occupies XInput slot " << i << std::endl;
                    // targets[i] remains nullptr
                } else {
                    PVIGEM_TARGET t = vigem_target_x360_alloc();
                    auto res = vigem_target_add(client, t);
                    if (VIGEM_SUCCESS(res)) {
                        vigem_target_x360_register_notification(client, t, &ControllerNotification, this);
                        // Give Windows PnP manager time to enumerate
                        Sleep(200); 
                        
                        targets[i] = t;
                        std::cout << "[ViGEm] Connected virtual controller. Mapped to expected XInput Player " << (i + 1) << " (UI Slot " << i << ")." << std::endl;
                    } else {
                        std::cerr << "[ViGEm] Failed to plug in virtual controller. Error: " << std::hex << res << std::endl;
                        vigem_target_free(t);
                    }
                }
            }

            s_initialized = true;
            std::cout << "[ViGEm] Initialized virtual gamepad subsystem successfully." << std::endl;
            return true;
        }

        int ConnectController(int preferredSlot = -1) override {
            if (preferredSlot >= 0 && preferredSlot < 4 && targets[preferredSlot]) {
                // Send a dummy input pulse so HTML5 Gamepad API (browsers) detect the controller immediately
                XUSB_REPORT initReport = {};
                initReport.wButtons = XUSB_GAMEPAD_A;
                auto err = vigem_target_x360_update(client, targets[preferredSlot], initReport);
                if (!VIGEM_SUCCESS(err)) std::cerr << "[ViGEm] Failed dummy pulse (A): " << std::hex << err << std::endl;
                Sleep(200); // 200ms ensures it crosses browser requestAnimationFrame boundaries
                initReport.wButtons = 0;
                err = vigem_target_x360_update(client, targets[preferredSlot], initReport);
                if (!VIGEM_SUCCESS(err)) std::cerr << "[ViGEm] Failed dummy pulse (release): " << std::hex << err << std::endl;
                return preferredSlot;
            }
            return -1; 
        }

        void DisconnectController(int controllerId) override {
            // We no longer dynamically unplug controllers.
            // Just reset its state to neutral
            if (controllerId >= 0 && controllerId < 4 && targets[controllerId] != nullptr) {
                XUSB_REPORT report = {};
                vigem_target_x360_update(client, targets[controllerId], report);
                std::cout << "[ViGEm] Reset virtual controller on slot " << controllerId << " to neutral." << std::endl;
            }
        }

        bool IsSlotAvailableForVirtual(int slot) override {
            if (slot >= 0 && slot < 4) {
                return targets[slot] != nullptr;
            }
            return false;
        }

        void UpdateState(int controllerId, const Protocol::ControllerStatePacket& state) override {
            if (!client || controllerId < 0 || controllerId >= 4 || targets[controllerId] == nullptr) return;

            XUSB_REPORT report = {};
            report.wButtons = state.buttons;
            report.bLeftTrigger = state.leftTrigger;
            report.bRightTrigger = state.rightTrigger;
            report.sThumbLX = state.leftStickX;
            report.sThumbLY = state.leftStickY;
            report.sThumbRX = state.rightStickX;
            report.sThumbRY = state.rightStickY;
            
            static uint32_t lastLogTime = 0;
            uint32_t now = GetTickCount();
            if (state.buttons != 0 || state.leftStickX != 0 || state.leftStickY != 0) {
                if (now - lastLogTime > 1000) {
                    std::cout << "[Backend] Applied state to slot " << controllerId << " | Btn: " << state.buttons << " LX: " << state.leftStickX << " LY: " << state.leftStickY << std::endl;
                    lastLogTime = now;
                }
            }

            auto err = vigem_target_x360_update(client, targets[controllerId], report);
            if (!VIGEM_SUCCESS(err)) {
                std::cerr << "[ViGEm] UpdateState failed with error: " << std::hex << err << std::endl;
            }
        }

        void Shutdown() override {
            for (int i = 0; i < 4; ++i) {
                if (targets[i]) {
                    vigem_target_remove(client, targets[i]);
                    vigem_target_free(targets[i]);
                    targets[i] = nullptr;
                }
            }
            if (client) {
                vigem_disconnect(client);
                vigem_free(client);
                client = nullptr;
            }
            std::cout << "[ViGEm] Subsystem shut down." << std::endl;
        }
        
        bool IsSlotOwned(int slotIndex) const {
            if (slotIndex < 0 || slotIndex >= 4) return false;
            return targets[slotIndex] != nullptr;
        }

    private:
        PVIGEM_CLIENT client;
        PVIGEM_TARGET targets[4];
        RumbleCallback m_rumbleCallback;
    };

} // namespace VirtualGamepad
} // namespace MobileController

