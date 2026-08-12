param(
    [string]$SqlJdbcAuthDllPath
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$mavenWrapper = Join-Path $projectRoot "mvnw.cmd"

$dllCandidates = @(
    @(
        $SqlJdbcAuthDllPath,
        (Join-Path $projectRoot "native\sqlserver\mssql-jdbc_auth-13.2.1.x64.dll"),
        (Join-Path $env:USERPROFILE "Downloads\sqljdbc_13.2.1.0_fra\sqljdbc_13.2\fra\auth\x64\mssql-jdbc_auth-13.2.1.x64.dll"),
        (Join-Path $env:USERPROFILE "Downloads\sqljdbc_13.2.1.0_enu\sqljdbc_13.2\enu\auth\x64\mssql-jdbc_auth-13.2.1.x64.dll")
    ) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique
)

if (-not $dllCandidates) {
    throw @"
Impossible de trouver mssql-jdbc_auth-13.2.1.x64.dll.
Place la DLL dans l'un de ces emplacements puis relance ce script:
- $projectRoot\native\sqlserver\
- $env:USERPROFILE\Downloads\sqljdbc_13.2.1.0_fra\sqljdbc_13.2\fra\auth\x64\
- $env:USERPROFILE\Downloads\sqljdbc_13.2.1.0_enu\sqljdbc_13.2\enu\auth\x64\
"@
}

$dllPath = $dllCandidates[0]
$dllDirectory = Split-Path -Parent $dllPath
$pathEntries = $env:PATH -split ";"
$javaLibraryArgument = "-Djava.library.path=$dllDirectory"

if ($pathEntries -notcontains $dllDirectory) {
    $env:PATH = "$dllDirectory;$env:PATH"
}

$existingJavaToolOptions = $env:JAVA_TOOL_OPTIONS
if ([string]::IsNullOrWhiteSpace($existingJavaToolOptions)) {
    $env:JAVA_TOOL_OPTIONS = $javaLibraryArgument
}
elseif ($existingJavaToolOptions -notmatch "java\.library\.path=") {
    $env:JAVA_TOOL_OPTIONS = "$javaLibraryArgument $existingJavaToolOptions"
}

Write-Host "Using SQL Server auth DLL:" $dllPath
Set-Location $projectRoot
& $mavenWrapper spring-boot:run "-Dspring-boot.run.jvmArguments=$javaLibraryArgument"
