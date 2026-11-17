param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    if (-not $SkipBuild) {
        & ./mvnw.cmd -B -ntp verify
        if ($LASTEXITCODE -ne 0) { throw 'Maven verification failed' }
    }
    $packageInput = Join-Path $projectRoot ('target/package-input.' + [guid]::NewGuid())
    New-Item -ItemType Directory $packageInput | Out-Null
    try {
        Copy-Item target/SshAutoConnect-1.1-SNAPSHOT.jar $packageInput
        Copy-Item -Recurse target/lib (Join-Path $packageInput 'lib')
        $jpackage = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/jpackage.exe' } else { 'jpackage.exe' }
        & $jpackage --type app-image --name SSHAutoConnect --app-version 1.1.0 `
            --input $packageInput --main-jar SshAutoConnect-1.1-SNAPSHOT.jar `
            --main-class de.kneitzel.Launcher --dest target/dist `
            --java-options '--enable-native-access=ALL-UNNAMED'
        if ($LASTEXITCODE -ne 0) { throw 'jpackage failed' }
    } finally { Remove-Item -Recurse -Force $packageInput }
} finally { Pop-Location }
