param(
    [Alias('SkipBuild')][switch]$SkipTests,
    [switch]$ImageOnly,
    [Parameter(ValueFromRemainingArguments=$true)][string[]]$MavenArguments
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $mavenOptions = @('-B', '-ntp', '-Pjpackage')
    if ($SkipTests) { $mavenOptions += '-DskipTests' }
    if ($ImageOnly) { $mavenOptions += '-Djpackage.installer.skip=true' }
    if ($MavenArguments) { $mavenOptions += $MavenArguments }
    $mavenOptions += 'verify'
    & ./mvnw.cmd @mavenOptions
    if ($LASTEXITCODE -ne 0) { throw 'Maven application packaging failed' }
} finally { Pop-Location }
