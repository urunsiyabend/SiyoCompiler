$ErrorActionPreference = "Stop"

$Repo = "urunsiyabend/SiyoCompiler"
$InstallDir = "$env:USERPROFILE\.siyo"

Write-Host "Detected platform: windows-x64"

# Get latest release tag
$Release = Invoke-RestMethod "https://api.github.com/repos/$Repo/releases/latest"
$Tag = $Release.tag_name
$Version = $Tag.TrimStart("v")
Write-Host "Latest version: $Version"

# Download
$Archive = "siyo-$Version-windows-x64.zip"
$Url = "https://github.com/$Repo/releases/download/$Tag/$Archive"
$TmpDir = Join-Path $env:TEMP "siyo-install"
$TmpZip = Join-Path $TmpDir $Archive

if (Test-Path $TmpDir) { Remove-Item -Recurse -Force $TmpDir }
New-Item -ItemType Directory -Path $TmpDir | Out-Null

Write-Host "Downloading $Archive..."
Invoke-WebRequest -Uri $Url -OutFile $TmpZip -UseBasicParsing

# Extract
if (Test-Path $InstallDir) { Remove-Item -Recurse -Force $InstallDir }
Expand-Archive -Path $TmpZip -DestinationPath $TmpDir

# Move contents to install dir
$ExtractedDir = Join-Path $TmpDir "siyo-$Version-windows-x64"
Move-Item -Path $ExtractedDir -Destination $InstallDir
Remove-Item -Recurse -Force $TmpDir

# Add to User PATH
$BinDir = Join-Path $InstallDir "bin"
$RegKey = "HKCU:\Environment"
$CurrentPath = (Get-ItemProperty -Path $RegKey -Name Path -ErrorAction SilentlyContinue).Path

if ($CurrentPath -and $CurrentPath -notlike "*$BinDir*") {
    $NewPath = "$CurrentPath;$BinDir"
    Set-ItemProperty -Path $RegKey -Name Path -Value $NewPath
    Write-Host "  Added $BinDir to User PATH"
} elseif (-not $CurrentPath) {
    Set-ItemProperty -Path $RegKey -Name Path -Value $BinDir
    Write-Host "  Added $BinDir to User PATH"
} else {
    Write-Host "  $BinDir already in PATH"
}

# Notify shell of environment change
$HWND_BROADCAST = [IntPtr]0xffff
$WM_SETTINGCHANGE = 0x1a
Add-Type -Namespace Win32 -Name NativeMethods -MemberDefinition @"
[DllImport("user32.dll", SetLastError = true, CharSet = CharSet.Auto)]
public static extern IntPtr SendMessageTimeout(
    IntPtr hWnd, uint Msg, UIntPtr wParam, string lParam,
    uint fuFlags, uint uTimeout, out UIntPtr lpdwResult);
"@
$result = [UIntPtr]::Zero
[Win32.NativeMethods]::SendMessageTimeout($HWND_BROADCAST, $WM_SETTINGCHANGE, [UIntPtr]::Zero, "Environment", 2, 5000, [ref]$result) | Out-Null

Write-Host ""
Write-Host "Siyo $Version installed to $InstallDir"
Write-Host ""
Write-Host "Open a new terminal, then run:"
Write-Host "  siyoc new my-app"
