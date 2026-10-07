$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/glass_environment.ps1"
$generatorFile = Join-Path $glassBuildDir 'generate_android_plugins.dart'
@'
import 'package:flutter_tools/src/cache.dart';
import 'package:flutter_tools/src/base/template.dart';
import 'package:flutter_tools/src/context_runner.dart';
import 'package:flutter_tools/src/flutter_plugins.dart';
import 'package:flutter_tools/src/isolated/mustache_template.dart';
import 'package:flutter_tools/src/project.dart';

Future<void> main(List<String> arguments) async {
  await runInContext<void>(() async {
    Cache.flutterRoot = arguments.single;
    await injectPlugins(FlutterProject.current(), releaseMode: true, androidPlatform: true);
  }, overrides: {TemplateRenderer: () => const MustacheTemplateRenderer()});
}
'@ | Set-Content -LiteralPath $generatorFile -Encoding utf8
& rtk proxy "$env:FLUTTER_ROOT/bin/cache/dart-sdk/bin/dart.exe" "--packages=$env:FLUTTER_ROOT/packages/flutter_tools/.dart_tool/package_config.json" $generatorFile $env:FLUTTER_ROOT
if ($LASTEXITCODE -ne 0) { throw 'Android plugin registration generation failed' }
if (-not (Test-Path -LiteralPath (Join-Path $projectDir 'android/app/src/main/java/io/flutter/plugins/GeneratedPluginRegistrant.java'))) {
    throw 'Android plugin registrant was not generated'
}
