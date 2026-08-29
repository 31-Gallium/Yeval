#pragma once

#include "protocol/controller_protocol.h"
#include <string>
#include <functional>

namespace MobileController {
namespace VirtualGamepad {

    /**
     * Interface for the virtual controller backend.
     * This abstracts the actual emulation (e.g., Nefarius VirtualPad, Microsoft VHF)
     * from the input processing engine.
     */
    class IVirtualGamepadBackend {
    public:
        virtual ~IVirtualGamepadBackend() = default;

        /**
         * Initialize the virtual gamepad subsystem.
         */
        virtual bool Initialize() = 0;

        /**
         * Create a new virtual controller and plug it in.
         * If preferredSlot is provided (0-3), attempts to connect it to that specific slot.
         * Returns an internal ID (the slot index), or -1 on failure.
         */
        virtual int ConnectController(int preferredSlot = -1) = 0;

        /**
         * Unplug and destroy a specific virtual controller.
         */
        virtual void DisconnectController(int controllerId) = 0;

        /**
         * Checks if the given slot is available to be assigned to a virtual controller
         * (i.e. not occupied by a physical controller).
         */
        virtual bool IsSlotAvailableForVirtual(int slot) = 0;

        /**
         * Update the state of a connected controller.
         */
        virtual void UpdateState(int controllerId, const Protocol::ControllerStatePacket& state) = 0;

        /**
         * Shut down the subsystem.
         */
        virtual void Shutdown() = 0;

        using RumbleCallback = std::function<void(int controllerId, uint8_t largeMotor, uint8_t smallMotor)>;
        virtual void SetRumbleCallback(RumbleCallback cb) = 0;
    };

} // namespace VirtualGamepad
} // namespace MobileController
