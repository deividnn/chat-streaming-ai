<#
.SYNOPSIS
    Instala e configura o Eclipse Temurin JDK 25 e o Kiri no Windows.

.DESCRIPTION
    1. Instala o Eclipse Temurin JDK 25 via winget (ou utiliza um diretório já extraído via -JdkPath).
    2. Configura JAVA_HOME e atualiza o PATH do usuário e da sessão atual.
    3. Baixa e instala o instalador .msi mais recente do Kiri (libracoder/kiri) no GitHub Releases.
    4. Executa 'kiri setup --use' para configurar o contexto do Kiri.

.PARAMETER JdkPath
    Caminho opcional de um JDK 25 já baixado/extraído localmente (evita novo download via winget).

.EXAMPLE
    .\scripts\setup-windows.ps1
.EXAMPLE
    .\scripts\setup-windows.ps1 -JdkPath "C:\caminho\para\jdk-25"
#>
param(
    [string]$JdkPath = ""
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Add-ToUserPath {
    param([string]$Directory)
    if ([string]::IsNullOrWhiteSpace($Directory) -or -not (Test-Path $Directory)) {
        return
    }

    $currentUserPath = [Environment]::GetEnvironmentVariable("Path", "User")
    $entries = @()
    if (-not [string]::IsNullOrWhiteSpace($currentUserPath)) {
        $entries = $currentUserPath -split ";" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    }

    if ($entries -notcontains $Directory) {
        # Coloca o diretório no início para garantir prioridade sobre outras versões do Java
        $newPath = (@($Directory) + $entries) -join ";"
        [Environment]::SetEnvironmentVariable("Path", $newPath, "User")
        Write-Host "Adicionado ao PATH do usuário: $Directory" -ForegroundColor Green
    } else {
        Write-Host "Já presente no PATH do usuário: $Directory" -ForegroundColor Yellow
    }

    if (($env:PATH -split ";") -notcontains $Directory) {
        $env:PATH = "$Directory;$env:PATH"
    }
}

# ---------------------------------------------------------------------------
# 1. Instalar / localizar Eclipse Temurin JDK 25
# ---------------------------------------------------------------------------
Write-Step "Configurando Eclipse Temurin JDK 25"

if (-not [string]::IsNullOrWhiteSpace($JdkPath)) {
    if (-not (Test-Path (Join-Path $JdkPath "bin\java.exe"))) {
        throw "java.exe não encontrado em '$JdkPath\bin'. Verifique o parâmetro -JdkPath."
    }
    $resolvedJdkHome = (Resolve-Path $JdkPath).Path
} else {
    $existingJdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-25*" -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        Select-Object -First 1

    if ($existingJdk) {
        Write-Host "JDK 25 já encontrado em: $($existingJdk.FullName)" -ForegroundColor Yellow
        $resolvedJdkHome = $existingJdk.FullName
    } else {
        if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
            throw "winget não encontrado. Instale o App Installer na Microsoft Store ou informe -JdkPath."
        }

        Write-Host "Instalando EclipseAdoptium.Temurin.25.JDK via winget..."
        winget install --id EclipseAdoptium.Temurin.25.JDK -e --accept-package-agreements --accept-source-agreements

        $installedJdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-25*" -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            Select-Object -First 1

        if (-not $installedJdk) {
            throw "Instalação concluída, mas a pasta jdk-25* não foi encontrada em 'C:\Program Files\Eclipse Adoptium'."
        }
        $resolvedJdkHome = $installedJdk.FullName
    }
}

# Configura JAVA_HOME permanentemente para o usuário e na sessão atual
[Environment]::SetEnvironmentVariable("JAVA_HOME", $resolvedJdkHome, "User")
$env:JAVA_HOME = $resolvedJdkHome
Add-ToUserPath (Join-Path $resolvedJdkHome "bin")
Write-Host "JAVA_HOME configurado para: $resolvedJdkHome" -ForegroundColor Green

# ---------------------------------------------------------------------------
# 2. Instalar e configurar Kiri (container runtime compatível com Docker)
# ---------------------------------------------------------------------------
Write-Step "Configurando Kiri"

$kiriBinDir = Join-Path $env:LOCALAPPDATA "Kiri\bin"
$kiriCmd = Get-Command kiri -ErrorAction SilentlyContinue

if ($kiriCmd) {
    Write-Host "Kiri já está instalado em: $($kiriCmd.Source)" -ForegroundColor Yellow
} else {
    $arch = if ([Environment]::Is64BitOperatingSystem) {
        if ($env:PROCESSOR_ARCHITECTURE -eq "ARM64") { "aarch64|arm64" } else { "x86_64|x64|amd64" }
    } else {
        throw "O Kiri requer Windows 64 bits (x86_64 ou ARM64) com WSL 2."
    }

    Write-Host "Consultando última release do Kiri no GitHub (libracoder/kiri)..."
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/libracoder/kiri/releases/latest" -Headers @{ "User-Agent" = "chat-streaming-ai-setup" }
    $msiAsset = $release.assets | Where-Object { $_.name -match "\.msi$" -and $_.name -match $arch } | Select-Object -First 1

    if (-not $msiAsset) {
        $msiAsset = $release.assets | Where-Object { $_.name -match "\.msi$" } | Select-Object -First 1
    }

    if (-not $msiAsset) {
        throw "Nenhum instalador .msi encontrado na última release do Kiri. Baixe manualmente em https://github.com/libracoder/kiri/releases"
    }

    $msiPath = Join-Path $env:TEMP $msiAsset.name
    Write-Host "Baixando $($msiAsset.name)..."
    Invoke-WebRequest -Uri $msiAsset.browser_download_url -OutFile $msiPath

    Write-Host "Executando instalador MSI do Kiri..."
    Start-Process msiexec.exe -ArgumentList "/i `"$msiPath`" /passive /norestart" -Wait
    Remove-Item $msiPath -Force -ErrorAction SilentlyContinue
}

if (Test-Path $kiriBinDir) {
    Add-ToUserPath $kiriBinDir
}

# Configura o contexto padrão do Kiri para comandos docker/kiri
if (Get-Command kiri -ErrorAction SilentlyContinue) {
    Write-Host "Configurando contexto do Kiri (kiri setup --use)..."
    kiri setup --use
}

# ---------------------------------------------------------------------------
# 3. Validação final
# ---------------------------------------------------------------------------
Write-Step "Verificando instalação"
& (Join-Path $resolvedJdkHome "bin\java.exe") -version

if (Get-Command kiri -ErrorAction SilentlyContinue) {
    kiri --help | Select-Object -First 5
}

Write-Host "`nConcluído! Reabra o terminal para que JAVA_HOME e PATH atualizados entrem em vigor em novas sessões." -ForegroundColor Green
