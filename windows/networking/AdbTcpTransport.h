#pragma once

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <winsock2.h>
#include <ws2tcpip.h>
#include <iostream>
#include <functional>
#include <thread>
#include <atomic>
#include <vector>
#include "../shared/protocol/controller_protocol.h"
#include "ITransport.h"

#pragma comment(lib, "ws2_32.lib")

namespace MobileController {
namespace Networking {

    class AdbTcpTransport : public ITransport {
    public:
        AdbTcpTransport() = default;
        ~AdbTcpTransport() override { Stop(); }

        bool Start(PacketCallback callback) override {
            if (m_running.exchange(true)) return true;
            m_callback = std::move(callback);

            WSADATA wsaData;
            if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
                std::cerr << "[AdbTcpTransport] WSAStartup failed." << std::endl;
                m_running = false;
                return false;
            }

            m_listenSocket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
            if (m_listenSocket == INVALID_SOCKET) {
                std::cerr << "[AdbTcpTransport] Socket creation failed." << std::endl;
                WSACleanup();
                m_running = false;
                return false;
            }

            sockaddr_in serverAddr = {};
            serverAddr.sin_family = AF_INET;
            serverAddr.sin_addr.s_addr = INADDR_ANY;
            serverAddr.sin_port = htons(51230);

            if (bind(m_listenSocket, reinterpret_cast<sockaddr*>(&serverAddr), sizeof(serverAddr)) == SOCKET_ERROR) {
                // If 51230 is taken, fall back to dynamic port
                serverAddr.sin_port = htons(0);
                if (bind(m_listenSocket, reinterpret_cast<sockaddr*>(&serverAddr), sizeof(serverAddr)) == SOCKET_ERROR) {
                    std::cerr << "[AdbTcpTransport] Bind failed. Error: " << WSAGetLastError() << std::endl;
                    closesocket(m_listenSocket);
                    WSACleanup();
                    m_running = false;
                    return false;
                }
            }

            int addrLen = sizeof(serverAddr);
            int boundPort = 0;
            if (getsockname(m_listenSocket, (sockaddr*)&serverAddr, &addrLen) == 0) {
                boundPort = ntohs(serverAddr.sin_port);
            }

            if (listen(m_listenSocket, SOMAXCONN) == SOCKET_ERROR) {
                std::cerr << "[AdbTcpTransport] Listen failed." << std::endl;
                closesocket(m_listenSocket);
                WSACleanup();
                m_running = false;
                return false;
            }

            std::cout << "[Backend] [AdbTcpTransport] Listening for ADB forwarded TCP connections on 127.0.0.1:" << boundPort << std::endl;

            m_acceptThread = std::thread(&AdbTcpTransport::AcceptLoop, this);
            return true;
        }

        void Stop() override {
            if (!m_running.exchange(false)) return;

            if (m_listenSocket != INVALID_SOCKET) {
                closesocket(m_listenSocket);
                m_listenSocket = INVALID_SOCKET;
            }
            if (m_clientSocket != INVALID_SOCKET) {
                closesocket(m_clientSocket);
                m_clientSocket = INVALID_SOCKET;
            }

            if (m_acceptThread.joinable()) m_acceptThread.join();
            if (m_receiveThread.joinable()) m_receiveThread.join();
            
            WSACleanup();
        }

        void SendRumble(uint8_t largeMotor, uint8_t smallMotor) override {
            if (m_clientSocket != INVALID_SOCKET) {
                char buf[6] = {'R', 'U', 'M', 'B', (char)largeMotor, (char)smallMotor};
                send(m_clientSocket, buf, 6, 0);
            }
        }

        void SendReload(const std::string& slotId) override {
            if (m_clientSocket != INVALID_SOCKET) {
                std::string msg = "RELO:" + slotId;
                send(m_clientSocket, msg.c_str(), msg.length(), 0);
            }
        }

        void SetDisconnectCallback(DisconnectCallback callback) override {
            m_disconnectCallback = callback;
        }

    private:
        void AcceptLoop() {
            while (m_running) {
                sockaddr_in clientAddr{};
                int clientAddrLen = sizeof(clientAddr);
                SOCKET clientSocket = accept(m_listenSocket, reinterpret_cast<sockaddr*>(&clientAddr), &clientAddrLen);
                
                if (clientSocket == INVALID_SOCKET) {
                    if (m_running) {
                        std::cerr << "[AdbTcpTransport] Accept failed." << std::endl;
                    }
                    continue;
                }

                BOOL nodelay = TRUE;
                setsockopt(clientSocket, IPPROTO_TCP, TCP_NODELAY, reinterpret_cast<const char*>(&nodelay), sizeof(BOOL));

                std::cout << "[AdbTcpTransport] Device connected." << std::endl;

                if (m_clientSocket != INVALID_SOCKET) {
                    closesocket(m_clientSocket);
                }
                m_clientSocket = clientSocket;

                if (m_receiveThread.joinable()) {
                    m_receiveThread.join();
                }

                m_receiveThread = std::thread(&AdbTcpTransport::ReceiveLoop, this, clientSocket);
            }
        }

        void ReceiveLoop(SOCKET clientSocket) {
            Protocol::ControllerStatePacket packetBuffer{};
            int bytesAccumulated = 0;
            const int packetSize = sizeof(packetBuffer);
            
            while (m_running && clientSocket != INVALID_SOCKET) {
                int bytesRead = recv(clientSocket, reinterpret_cast<char*>(&packetBuffer) + bytesAccumulated, packetSize - bytesAccumulated, 0);
                
                if (bytesRead == SOCKET_ERROR || bytesRead == 0) {
                    std::cout << "[AdbTcpTransport] Device disconnected." << std::endl;
                    break;
                }

                if (bytesRead > 0) {
                    bytesAccumulated += bytesRead;
                    if (bytesAccumulated == packetSize) {
                        if (packetBuffer.magic == Protocol::PROTOCOL_MAGIC && packetBuffer.version == Protocol::PROTOCOL_VERSION && m_callback) {
                            m_callback(packetBuffer, GetTickCount64(), 0, 0, TransportType::USB_AOA);
                        }
                        bytesAccumulated = 0;
                    }
                }
            }
            
            if (m_clientSocket == clientSocket) {
                m_clientSocket = INVALID_SOCKET;
            }
            closesocket(clientSocket);

            if (m_disconnectCallback) {
                m_disconnectCallback(TransportType::USB_AOA);
            }
        }

        PacketCallback m_callback;
        DisconnectCallback m_disconnectCallback;
        std::atomic<bool> m_running{false};
        SOCKET m_listenSocket{INVALID_SOCKET};
        SOCKET m_clientSocket{INVALID_SOCKET};
        std::thread m_acceptThread;
        std::thread m_receiveThread;
    };

} // namespace Networking
} // namespace MobileController
