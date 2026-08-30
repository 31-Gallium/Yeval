!macro customHeader
  !define MUI_WELCOMEPAGE_TITLE "Yeval Setup"
  !define MUI_WELCOMEPAGE_TEXT "Welcome to Yeval Setup.$\r$\n$\r$\nTurn your smartphone into a virtual Xbox 360 controller for PC over Wi-Fi and USB with dynamic failover.$\r$\n$\r$\nClick Next to continue."
  !define MUI_FINISHPAGE_TITLE "Setup Complete"
  !define MUI_FINISHPAGE_TEXT "Yeval Dashboard and required virtual controller drivers have been installed on your PC.$\r$\n$\r$\nClick Finish to start Yeval."
!macroend

!macro customGUIInit
  ; Set Dark Obsidian Background and Frosted Silver text for the NSIS Window
  SetCtlColors $HWNDPARENT 0xE2E8F0 0x0D111A
  
  ; Find the inner installation dialog
  FindWindow $R0 "#32770" "" $HWNDPARENT
  ${If} $R0 != 0
    SetCtlColors $R0 0xE2E8F0 0x0D111A
    
    ; Find and style the progress bar (Class: msctls_progress32)
    FindWindow $R1 "msctls_progress32" "" $R0
    ${If} $R1 != 0
      ; Disable default green Windows theme on the progress bar so custom GDI colors apply
      System::Call 'uxtheme::SetWindowTheme(p $R1, w "", w "")'
      ; Set Progress Bar Fill to Frost White/Silver (0xF0E8E2)
      SendMessage $R1 0x0409 0 0x00F0E8E2
      ; Set Progress Bar Track Background to Dark Slate (0x2C201A)
      SendMessage $R1 0x2001 0 0x002C201A
    ${EndIf}

    ; Find and style the static text label ("Installing, please wait...")
    FindWindow $R2 "Static" "" $R0
    ${If} $R2 != 0
      SetCtlColors $R2 0xE2E8F0 0x0D111A
    ${EndIf}
  ${EndIf}
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
