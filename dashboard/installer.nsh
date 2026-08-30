!macro customInstall
  DetailPrint "Checking for ViGEmBus Driver..."
  ReadRegStr $0 HKLM "SYSTEM\CurrentControlSet\Services\ViGEmBus" "ImagePath"
  ${If} $0 == ""
    DetailPrint "ViGEmBus not found. Installing ViGEmBus Kernel Driver silently..."
    SetOutPath "$INSTDIR\resources\prereqs"
    ExecWait '"$INSTDIR\resources\prereqs\ViGEmBusSetup.exe" /quiet /norestart' $1
    DetailPrint "ViGEmBus installation finished with exit code: $1"
  ${Else}
    DetailPrint "ViGEmBus Kernel Driver is already installed."
  ${EndIf}
!macroend
