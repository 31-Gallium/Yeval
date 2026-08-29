#pragma once

#define WIN32_LEAN_AND_MEAN
#include <winsock2.h>
#include <ws2tcpip.h>
#include <iostream>
#include <functional>
#include <thread>
#include "../shared/protocol/controller_protocol.h"
#include "ITransport.h"

#pragma comment(lib, "ws2_32.lib")

namespace MobileController {
namespace Networking {

    class UdpTransport : public ITransport {
    public:
        UdpTransport(uint16_t port) 
            : m_port(port), m_socket(INVALID_SOCKET), m_running(false) {}

        ~UdpTransport() override {
            Stop();
        }

        bool Start(PacketCallback callback) override {
            m_callback = std::move(callback);
            
            WSADATA wsaData;
            if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
                std::cerr << "[UdpTransport] WSAStartup failed." << std::endl;
                return false;
            }

            m_socket = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
            if (m_socket == INVALID_SOCKET) {
                std::cerr << "[UdpTransport] Socket creation failed." << std::endl;
                WSACleanup();
                return false;
            }

            sockaddr_in serverAddr = {};
            serverAddr.sin_family = AF_INET;
            serverAddr.sin_addr.s_addr = INADDR_ANY;
            serverAddr.sin_port = htons(m_port);

            if (bind(m_socket, (sockaddr*)&serverAddr, sizeof(serverAddr)) == SOCKET_ERROR) {
                std::cerr << "[UdpTransport] Bind failed." << std::endl;
                closesocket(m_socket);
                WSACleanup();
                return false;
            }

            int addrLen = sizeof(serverAddr);
            if (getsockname(m_socket, (sockaddr*)&serverAddr, &addrLen) == 0) {
                m_port = ntohs(serverAddr.sin_port);
            }

            m_running = true;
            std::cout << "[Backend] [UdpTransport] Listening on UDP port " << m_port << std::endl;

            m_receiveThread = std::thread(&UdpTransport::ReceiveLoop, this);

            return true;
        }

        void Stop() override {
            m_running = false;
            if (m_socket != INVALID_SOCKET) {
                closesocket(m_socket);
                m_socket = INVALID_SOCKET;
            }
            if (m_receiveThread.joinable()) {
                m_receiveThread.join();
            }
            WSACleanup();
        }

        void SendRumble(uint8_t largeMotor, uint8_t smallMotor) override {
            if (m_lastClientAddr.sin_family == AF_INET) {
                char buf[6] = {'R', 'U', 'M', 'B', (char)largeMotor, (char)smallMotor};
                sockaddr_in targetAddr = m_lastClientAddr;
                targetAddr.sin_port = htons(14570);
                sendto(m_socket, buf, 6, 0, (sockaddr*)&targetAddr, sizeof(targetAddr));
            }
        }

        void SendReload(const std::string& slotId) override {
            if (m_lastClientAddr.sin_family == AF_INET) {
                std::string msg = "YEVAL_RELOAD:" + slotId;
                sockaddr_in targetAddr = m_lastClientAddr;
                targetAddr.sin_port = htons(14569);
                sendto(m_socket, msg.c_str(), msg.length(), 0, (sockaddr*)&targetAddr, sizeof(targetAddr));
            }
        }

    private:
        void ReceiveLoop() {
            Protocol::ControllerStatePacket packetBuffer;
            sockaddr_in clientAddr;
            int clientAddrLen = sizeof(clientAddr);

            while (m_running) {
                int bytesRead = recvfrom(m_socket, (char*)&packetBuffer, sizeof(packetBuffer), 0, (sockaddr*)&clientAddr, &clientAddrLen);
                
                // Capture accurate arrival time immediately after recvfrom unblocks
                uint64_t arrivalTimeMs = GetTickCount64();

                if (bytesRead == sizeof(Protocol::ControllerStatePacket)) {
                    m_lastClientAddr = clientAddr; // update last known client address
                    if (packetBuffer.magic == Protocol::PROTOCOL_MAGIC && packetBuffer.version == Protocol::PROTOCOL_VERSION) {
                        if (m_callback) {
                            m_callback(packetBuffer, arrivalTimeMs, clientAddr.sin_addr.s_addr, clientAddr.sin_port, TransportType::UDP);
                        }
                    } else {
                        std::cerr << "[UdpTransport] Received packet with invalid magic/version." << std::endl;
                    }
                } else if (bytesRead > 0) {
                    std::cerr << "[UdpTransport] Received malformed packet of size: " << bytesRead << std::endl;
                }
            }
        }

        uint16_t m_port;
        PacketCallback m_callback;
        SOCKET m_socket;
        sockaddr_in m_lastClientAddr{};
        bool m_running;
        std::thread m_receiveThread;
    };

} // namespace Networking
} // namespace MobileController
