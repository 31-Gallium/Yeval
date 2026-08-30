!macro customHeader
  !define MUI_WELCOMEPAGE_TITLE "Yeval Setup"
  !define MUI_WELCOMEPAGE_TEXT "Welcome to Yeval Setup.$\r$\n$\r$\nTurn your smartphone into a virtual Xbox 360 controller for PC over Wi-Fi and USB with dynamic failover.$\r$\n$\r$\nClick Next to continue."
  !define MUI_FINISHPAGE_TITLE "Setup Complete"
  !define MUI_FINISHPAGE_TEXT "Yeval Dashboard and required virtual controller drivers have been installed on your PC.$\r$\n$\r$\nClick Finish to start Yeval."
!macroend

!macro customInstall
  DetailPrint "Checking for ViGEmBus Kernel Driver..."
  ReadRegStr $0 HKLM "SYSTEM\CurrentControlSet\Services\ViGEmBus" "ImagePath"
  ${If} $0 == ""
    DetailPrint "Installing ViGEmBus Kernel Driver..."
    SetOutPath "$INSTDIR\resources\prereqs"
    ExecWait '"$INSTDIR\resources\prereqs\ViGEmBusSetup.exe" /quiet /norestart' $1
    DetailPrint "ViGEmBus installation finished with exit code: $1"
  ${Else}
    DetailPrint "ViGEmBus Kernel Driver is already installed."
  ${EndIf}
!macroend

!macro customUnInstall
  DetailPrint "Cleaning up Yeval Dashboard temporary caches..."
  RMDir /r "$APPDATA\yeval-dashboard\Cache"
  RMDir /r "$APPDATA\yeval-dashboard\Code Cache"
  RMDir /r "$APPDATA\yeval-dashboard\DawnCache"
  RMDir /r "$APPDATA\yeval-dashboard\GPUCache"
!macroend
