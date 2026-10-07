param([switch]$Prepare, [switch]$PatchPackages)
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path $PSScriptRoot -Parent
$glassBuildDir = Join-Path $projectDir '.build/glass'
$env:FLUTTER_ROOT = Join-Path $glassBuildDir 'flutter'
$env:PUB_CACHE = Join-Path $glassBuildDir 'pub-cache'
$env:JAVA_HOME = Join-Path $env:LOCALAPPDATA 'Android/jdk21'
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "$env:FLUTTER_ROOT/bin;$env:JAVA_HOME/bin;$env:PATH"
$env:PLAN_ID = '2026-10-07-android-liquid-glass'
$env:PWF_PLAN_ROOT = $projectDir

# Java's download clients do not inherit HTTP_PROXY automatically.
if ($env:HTTPS_PROXY) {
    $proxyUri = [Uri]$env:HTTPS_PROXY
    if ($proxyUri.Scheme -eq 'http') {
        $env:JAVA_TOOL_OPTIONS = "-Dhttp.proxyHost=$($proxyUri.Host) -Dhttp.proxyPort=$($proxyUri.Port) -Dhttps.proxyHost=$($proxyUri.Host) -Dhttps.proxyPort=$($proxyUri.Port)"
    }
}

if ($Prepare) {
    $patchNames = @('modal_barrier', 'text_selection', 'mouse_cursor', 'image_anim',
        'layout_builder', 'navigation_drawer', 'popup_menu', 'fab',
        'null_safety_for_selectable_region', 'selectable_region', 'editable_text',
        'text_field', 'scroll_position', 'scrollable', 'scrollable_gesture',
        'draggable_scrollable_sheet', 'scaffold', 'text', 'text_painter', 'sliver',
        'refresh_indicator', 'double_tap_gesture', 'bottom_sheet_android',
        'scroll_view', 'navigator')
    foreach ($patchName in $patchNames) {
        $patchPath = Join-Path $projectDir "lib/scripts/$patchName.patch"
        & rtk git -C $env:FLUTTER_ROOT apply --reverse --check $patchPath 2>$null
        if ($LASTEXITCODE -eq 0) { continue }
        & rtk git -C $env:FLUTTER_ROOT apply --check $patchPath
        if ($LASTEXITCODE -ne 0) { throw "SDK patch check failed: $patchName" }
        & rtk git -C $env:FLUTTER_ROOT apply $patchPath
        if ($LASTEXITCODE -ne 0) { throw "SDK patch failed: $patchName" }
    }
}

if ($PatchPackages) {
    $materialDir = Join-Path $env:PUB_CACHE 'hosted/pub.dev/material_ui-1.6.0'
    # A cache inside the workspace otherwise inherits PiliPlus's Git root, and
    # git apply silently skips package-relative paths outside that subdirectory.
    if (-not (Test-Path -LiteralPath (Join-Path $materialDir '.git'))) {
        & rtk git -C $materialDir init --quiet
        if ($LASTEXITCODE -ne 0) { throw 'Cannot isolate material package patches' }
    }
    $materialPatches = @('modal_barrier_material', 'navigation_drawer', 'popup_menu',
        'fab', 'text_field', 'scaffold', 'refresh_indicator', 'tabs', 'bottom_sheet_android')
    foreach ($patchName in $materialPatches) {
        $patchPath = Join-Path $projectDir "lib/scripts/material/$patchName.patch"
        & rtk git -C $materialDir apply --reverse --check $patchPath 2>$null
        if ($LASTEXITCODE -eq 0) { continue }
        & rtk git -C $materialDir apply --check $patchPath
        if ($LASTEXITCODE -ne 0) { throw "Material patch check failed: $patchName" }
        & rtk git -C $materialDir apply $patchPath
        if ($LASTEXITCODE -ne 0) { throw "Material patch failed: $patchName" }
    }
}
