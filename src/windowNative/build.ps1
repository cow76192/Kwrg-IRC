param([switch]$CompileOnly)
$ErrorActionPreference='Stop'
# Reproducible local build only. Minecraft launches the bundled window-capture.exe directly;
# piping binary capture through PowerShell's object output would corrupt frame bytes.
if(-not $CompileOnly){throw 'Use window-capture.exe for capture. This script only rebuilds it with -CompileOnly.'}
$taskCompiler=Join-Path $env:SystemRoot 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
$taskFramework=Split-Path -Parent $taskCompiler
$taskMetadata=Join-Path $env:SystemRoot 'System32\WinMetadata'
$taskSource=Join-Path $PSScriptRoot 'capture/window-capture.cs'
$taskScroll=Join-Path $PSScriptRoot 'input/window-scroll.cs'
$taskPipeline=Join-Path $PSScriptRoot 'capture/capture-pipeline.cs'
$taskOwned=Join-Path $PSScriptRoot 'capture/window-owned.cs'
$taskGamePointer=Join-Path $PSScriptRoot 'input/window-game-pointer.cs'
$taskKeyboard=Join-Path $PSScriptRoot 'input/window-keyboard.cs'
$taskMouse=Join-Path $PSScriptRoot 'input/window-mouse.cs'
$taskMediaSession=Join-Path $PSScriptRoot 'media/media-session.cs'
$taskMediaLog=Join-Path $PSScriptRoot 'media/playback-log.cs'
$taskOutput=Join-Path $PSScriptRoot '../client/resources/assets/kwrg/window/window-capture.exe'
New-Item -ItemType Directory -Path (Split-Path -Parent $taskOutput) -Force | Out-Null
$taskArguments = @(
    '/nologo', '/target:winexe', '/platform:x64', '/optimize+', '/unsafe+', '/codepage:65001'
    "/out:$taskOutput"
    "/reference:$taskMetadata\Windows.Graphics.winmd"
    "/reference:$taskMetadata\Windows.Foundation.winmd"
    "/reference:$taskMetadata\Windows.Media.winmd"
    "/reference:$taskFramework\System.Runtime.WindowsRuntime.dll"
    "/reference:$taskFramework\System.Runtime.dll"
    "/reference:$taskFramework\System.Runtime.InteropServices.WindowsRuntime.dll"
    $taskSource, $taskScroll, $taskPipeline, $taskOwned, $taskGamePointer, $taskKeyboard, $taskMouse, $taskMediaSession, $taskMediaLog
)
& $taskCompiler @taskArguments
if($LASTEXITCODE -ne 0){throw 'Capture helper compilation failed'}
