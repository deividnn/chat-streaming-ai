#!/usr/bin/env bash
# ==============================================================================
# Script de instalação e configuração do Eclipse Temurin JDK 25 e Kiri no Linux.
# Suporta distribuições baseadas em Debian/Ubuntu (apt) e Fedora/RHEL (dnf/yum).
# Uso: chmod +x scripts/setup-linux.sh && ./scripts/setup-linux.sh
# ==============================================================================
set -euo pipefail

step() {
    printf '\n==> %s\n' "$1"
}

# ------------------------------------------------------------------------------
# 1. Instalar Eclipse Temurin JDK 25
# ------------------------------------------------------------------------------
step "Instalando Eclipse Temurin JDK 25"

if command -v apt-get >/dev/null 2>&1; then
    sudo apt-get update
    sudo apt-get install -y wget curl apt-transport-https gnupg ca-certificates

    # Adiciona a chave GPG oficial do Eclipse Adoptium
    wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public \
        | gpg --dearmor \
        | sudo tee /etc/apt/trusted.gpg.d/adoptium.gpg >/dev/null

    # Detecta o codinome da distribuição (ex.: noble, jammy, bookworm)
    CODENAME="$(awk -F= '/^VERSION_CODENAME=/{print $2}' /etc/os-release | tr -d '"')"
    echo "deb https://packages.adoptium.net/artifactory/deb ${CODENAME} main" \
        | sudo tee /etc/apt/sources.list.d/adoptium.list >/dev/null

    sudo apt-get update
    sudo apt-get install -y temurin-25-jdk
elif command -v dnf >/dev/null 2>&1 || command -v yum >/dev/null 2>&1; then
    PKG_MGR="$(command -v dnf || command -v yum)"
    sudo "$PKG_MGR" install -y curl ca-certificates

    # Configura o repositório RPM oficial do Eclipse Adoptium
    cat <<'EOF' | sudo tee /etc/yum.repos.d/adoptium.repo >/dev/null
[Adoptium]
name=Adoptium
baseurl=https://packages.adoptium.net/artifactory/rpm/$releasever/$basearch
enabled=1
gpgcheck=1
gpgkey=https://packages.adoptium.net/artifactory/api/gpg/key/public
EOF

    sudo "$PKG_MGR" install -y temurin-25-jdk
else
    echo "Gerenciador de pacotes não suportado automaticamente (esperado apt-get, dnf ou yum)." >&2
    echo "Instale o Eclipse Temurin JDK 25 manualmente em https://adoptium.net/temurin/releases/?version=25" >&2
    exit 1
fi

# ------------------------------------------------------------------------------
# 2. Configurar JAVA_HOME e PATH globalmente (/etc/profile.d/java25.sh)
# ------------------------------------------------------------------------------
step "Configurando JAVA_HOME e PATH para o JDK 25"

JAVA_HOME_DIR="$(find /usr/lib/jvm -maxdepth 1 -type d -name 'temurin-25-jdk*' | head -n 1 || true)"

if [ -z "$JAVA_HOME_DIR" ]; then
    # Fallback usando o binário java resolvido no PATH
    JAVA_HOME_DIR="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
fi

# Define o JDK 25 como alternativa padrão no sistema (quando update-alternatives existir)
if command -v update-alternatives >/dev/null 2>&1 && [ -x "${JAVA_HOME_DIR}/bin/java" ]; then
    sudo update-alternatives --set java "${JAVA_HOME_DIR}/bin/java" 2>/dev/null || true
    sudo update-alternatives --set javac "${JAVA_HOME_DIR}/bin/javac" 2>/dev/null || true
fi

# Persiste JAVA_HOME e PATH para todas as sessões de shell
cat <<EOF | sudo tee /etc/profile.d/java25.sh >/dev/null
export JAVA_HOME="${JAVA_HOME_DIR}"
export PATH="\${JAVA_HOME}/bin:\${PATH}"
EOF
sudo chmod +x /etc/profile.d/java25.sh

export JAVA_HOME="${JAVA_HOME_DIR}"
export PATH="${JAVA_HOME}/bin:${PATH}"

echo "JAVA_HOME configurado para: ${JAVA_HOME}"

# ------------------------------------------------------------------------------
# 3. Instalar e configurar Kiri (container runtime compatível com Docker)
# ------------------------------------------------------------------------------
step "Instalando Kiri"

if command -v kiri >/dev/null 2>&1; then
    echo "Kiri já está instalado em: $(command -v kiri)"
else
    curl -fsSL https://runkiri.com/install.sh | bash
fi

# Adiciona o usuário atual ao grupo 'kiri' para executar containers sem sudo
if getent group kiri >/dev/null 2>&1; then
    sudo usermod -aG kiri "$USER"
    echo "Usuário '${USER}' adicionado ao grupo 'kiri'."
fi

# Configura o contexto padrão do Kiri
if command -v kiri >/dev/null 2>&1; then
    kiri setup --use || true
fi

# ------------------------------------------------------------------------------
# 4. Validação final
# ------------------------------------------------------------------------------
step "Verificando instalação"
java -version
if command -v kiri >/dev/null 2>&1; then
    kiri --help | head -n 5
fi

printf '\nConcluído! Execute "source /etc/profile.d/java25.sh" ou abra um novo terminal (ou faça logout/login para aplicar o grupo kiri).\n'
