#pragma once

#include <cstdint>

#pragma pack(push, 1)

namespace MobileController {
namespace Protocol {

    // Unique magic number to identify valid protocol packets
    constexpr uint32_t PROTOCOL_MAGIC = 0x4D435031; // 'MCP1'
    constexpr uint8_t PROTOCOL_VERSION = 1;

    // Button bitmasks compatible with standard XInput mapping
    enum class ButtonMask : uint16_t {
        DPAD_UP        = 0x0001,
        DPAD_DOWN      = 0x0002,
        DPAD_LEFT      = 0x0004,
        DPAD_RIGHT     = 0x0008,
        START          = 0x0010,
        BACK           = 0x0020,
        LEFT_THUMB     = 0x0040,
        RIGHT_THUMB    = 0x0080,
        LEFT_SHOULDER  = 0x0100,
        RIGHT_SHOULDER = 0x0200,
        GUIDE          = 0x0400,
        A              = 0x1000,
        B              = 0x2000,
        X              = 0x4000,
        Y              = 0x8000
    };

    // Main packet structure sent from Android to PC
    struct ControllerStatePacket {
        uint32_t magic;           // Always PROTOCOL_MAGIC
        uint8_t version;          // Protocol version
        uint32_t sequence;        // Incrementing sequence number
        uint32_t deviceId;        // Unique identifier for the sending device
        uint64_t timestamp;       // Timestamp (microseconds or milliseconds) from device
        
        uint16_t buttons;         // Bitmask of pressed buttons (ButtonMask)
        
        // Triggers: 0 to 255
        uint8_t leftTrigger;
        uint8_t rightTrigger;
        
        // Sticks: -32768 to 32767
        int16_t leftStickX;
        int16_t leftStickY;
        int16_t rightStickX;
        int16_t rightStickY;
        
        // Gyroscope/Accelerometer Data (Reserved for Milestone 7)
        float gyroX;
        float gyroY;
        float gyroZ;
        float accelX;
        float accelY;
        float accelZ;
        
        uint32_t flags;           // Additional state flags
    };

} // namespace Protocol
} // namespace MobileController

#pragma pack(pop)
