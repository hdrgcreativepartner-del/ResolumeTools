#define AppName "HDRG Audio Bridge"
#define AppVersion "1.3.0"
[Setup]
AppId={{49B2710B-5188-4620-ABA3-2FE8754ED840}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher=HDRG Creative Partner
DefaultDirName={autopf}\HDRG Audio Bridge
DefaultGroupName=HDRG Creative Partner
UninstallDisplayIcon={app}\HDRG Audio Bridge.exe
OutputDir=installer-output
OutputBaseFilename=HDRG_Audio_Bridge_Setup_v1.3.0
Compression=lzma2
SolidCompression=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=lowest
WizardStyle=modern
SetupLogging=yes
[Files]
Source: "..\dist\HDRG Audio Bridge.exe"; DestDir: "{app}"; Flags: ignoreversion
[Icons]
Name: "{group}\HDRG Audio Bridge"; Filename: "{app}\HDRG Audio Bridge.exe"
Name: "{autodesktop}\HDRG Audio Bridge"; Filename: "{app}\HDRG Audio Bridge.exe"; Tasks: desktopicon
[Tasks]
Name: desktopicon; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"
[Run]
Filename: "{app}\HDRG Audio Bridge.exe"; Description: "Launch HDRG Audio Bridge"; Flags: nowait postinstall skipifsilent
