#ifndef Payload
  #error Payload must be supplied
#endif
[Setup]
AppId=KreyliCoopAOH2
AppName=Kreyli Coop AOH2
AppVersion=1.1.16
AppPublisher=Kreyli
DefaultDirName={localappdata}\Programs\Kreyli Coop AOH2
DefaultGroupName=Kreyli Coop AOH2
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
DisableProgramGroupPage=yes
DisableWelcomePage=no
WizardStyle=modern
WizardImageFile=branding\wizard.bmp
SetupIconFile=branding\kreyli.ico
UninstallDisplayIcon={app}\Kreyli Coop AOH2.exe
OutputDir=output
OutputBaseFilename=Kreyli-Coop-AOH2-Setup
Compression=zip/1
SolidCompression=no
DiskSpanning=no
CloseApplications=no
SetupLogging=no
Uninstallable=yes
CreateUninstallRegKey=not IsTest
[Languages]
Name: russian; MessagesFile: compiler:Languages\Russian.isl
Name: english; MessagesFile: compiler:Default.isl
[Tasks]
Name: desktopicon; Description: Создать ярлык на рабочем столе; Flags: checkedonce; Check: not IsTest
[Files]
Source: "{#Payload}\*"; DestDir: "{app}"; Excludes: "saves\*,settings\*,coop-home\*,coop-tmp\*,packages\unpacked\*,packages\original-game.rar*,*.log,kreyli-boot.png,launcher-check.txt,launcher-profile.json,launcher-error.txt,game-location.json,manual-updates.flag"; Flags: ignoreversion recursesubdirs createallsubdirs
[Dirs]
Name: "{app}\saves"
Name: "{app}\coop-home"
Name: "{app}\coop-tmp"
[Icons]
Name: "{autodesktop}\Kreyli Coop AOH2 — Лаунчер"; Filename: "{app}\Kreyli Coop AOH2.exe"; WorkingDir: "{app}"; IconFilename: "{app}\kreyli.ico"; Tasks: desktopicon; Check: not IsTest
Name: "{autoprograms}\Kreyli Coop AOH2"; Filename: "{app}\Kreyli Coop AOH2.exe"; WorkingDir: "{app}"; IconFilename: "{app}\kreyli.ico"; Check: not IsTest
[Run]
Filename: "{app}\Kreyli Coop AOH2.exe"; Description: Запустить Kreyli Coop AOH2; Flags: postinstall nowait skipifsilent
[Code]
function IsTest: Boolean;
begin
  Result := ExpandConstant('{param:WORKSPACETEST|0}') = '1';
end;

function InitializeSetup: Boolean;
begin
  Result := True;
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  Result := '';
  if FileExists(ExpandConstant('{app}\game.jar')) and
     not FileExists(ExpandConstant('{app}\.kreyli-install')) then
    Result := 'Выберите отдельную папку Kreyli Coop AOH2. Установка поверх исходной игры запрещена.';
end;






