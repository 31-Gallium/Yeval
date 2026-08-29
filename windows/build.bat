@echo off
setlocal

:: Find Visual Studio installation path
for /f "usebackq tokens=*" %%i in (`"%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do (
  set InstallDir=%%i
)

if "%InstallDir%"=="" (
  echo Error: Could not find Visual Studio installation with C++ tools.
  exit /b 1
)

:: Call the Developer Command Prompt script to set up MSVC and CMake on the PATH
call "%InstallDir%\Common7\Tools\VsDevCmd.bat" -arch=amd64 -host_arch=amd64

echo.
echo =======================================
echo Building Mobile Controller Backend...
echo =======================================
echo.

if not exist build mkdir build
cd build

:: Configure and build using CMake
cmake ..
if %ERRORLEVEL% neq 0 (
    echo CMake configuration failed!
    exit /b %ERRORLEVEL%
)

:: Move aside running executable if locked so linker can write new binary
if exist Release\MobileControllerBackend.exe (
    ren Release\MobileControllerBackend.exe MobileControllerBackend_old_%RANDOM%.exe >nul 2>&1
)

cmake --build . --config Release
if %ERRORLEVEL% neq 0 (
    echo Build failed!
    exit /b %ERRORLEVEL%
)

:: Clean up old renamed binaries
del Release\MobileControllerBackend_old_*.exe >nul 2>&1

echo.
echo Build succeeded! Executable is at windows\build\Release\MobileControllerBackend.exe
exit /b 0
