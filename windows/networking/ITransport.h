#pragma once

#include <functional>
#include "../shared/protocol/controller_protocol.h"

namespace MobileController {
namespace Networking {

    enum class TransportType {
        UNKNOWN = 0,
        UDP = 1,
        USB_AOA = 2,
        BLUETOOTH_RFCOMM = 3
    };

    class ITransport {
    public:
        using PacketCallback = std::function<void(const Protocol::ControllerStatePacket&, uint64_t arrivalTimeMs, uint32_t senderIp, uint16_t senderPort, TransportType type)>;
        using DisconnectCallback = std::function<void(TransportType type)>;

        virtual ~ITransport() = default;

        // Initialize and start listening
        virtual bool Start(PacketCallback callback) = 0;

        virtual void SetDisconnectCallback(DisconnectCallback callback) {}

        // Send Rumble to the controller
        virtual void SendRumble(uint8_t largeMotor, uint8_t smallMotor) = 0;

        // Send a command to reload the profile
        virtual void SendReload(const std::string& slotId) = 0;

        // Stop the transport
        virtual void Stop() = 0;
    };

} // namespace Networking
} // namespace MobileController
