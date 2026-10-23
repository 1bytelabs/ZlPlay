!macro customInstall
  FileOpen $0 "$INSTDIR\resources\installer-language.json" w
  FileWrite $0 '{$\"language$\":$\"$LANGUAGE$\"}'
  FileClose $0
!macroend
