cmake_minimum_required(VERSION 3.20)
project(MobileControllerBackend CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Statically link C/C++ Runtime Library (/MT) so vc_redist is not required on target PCs
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")

include(FetchContent)

# Fetch ViGEmClient SDK automatically
FetchContent_Declare(
  ViGEmClient
  GIT_REPOSITORY https://github.com/ViGEm/ViGEmClient.git
  GIT_TAG        master
)
FetchContent_MakeAvailable(ViGEmClient)



# Add shared protocol headers to include path
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../shared)
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# For Winsock
link_libraries(ws2_32)

# Source files
set(SOURCES
    core/main.cpp
    backend.rc
)

add_executable(MobileControllerBackend ${SOURCES})
target_link_libraries(MobileControllerBackend PRIVATE ViGEmClient setupapi xinput winusb)