#!/usr/bin/env bash
#
# deploy.sh - Compila el backend y despliega el jar en un servidor remoto.
#
# Uso: ./deploy.sh
# El script pedirá la IP, el usuario y la contraseña SSH del servidor.
# La misma contraseña se usa para sudo al reiniciar el servicio.
# Requiere: maven (para compilar) y, opcionalmente, sshpass (para enviar la
# contraseña de forma no interactiva). Si no hay sshpass, se usa scp/ssh
# interactivo y la contraseña se pide en el prompt del sistema.
#
set -euo pipefail

# ---------- Configuración (editar si es necesario) ----------
REMOTE_DIR="/home/juanito/rest"   # carpeta destino del jar en el servidor
SERVICE_NAME="personal-sids.service"                   # opcional: nombre del servicio systemd a reiniciar
# ------------------------------------------------------------

ROJO='\033[0;31m'
VERDE='\033[0;32m'
AMARILLO='\033[1;33m'
SIN_COLOR='\033[0m'

BACKEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

info()  { echo -e "${VERDE}[INFO]${SIN_COLOR} $*"; }
warn()  { echo -e "${AMARILLO}[WARN]${SIN_COLOR} $*"; }
error() { echo -e "${ROJO}[ERROR]${SIN_COLOR} $*"; }

if ! command -v mvn >/dev/null 2>&1; then
    error "Maven no está instalado (comando 'mvn' no encontrado)."
    exit 1
fi

SSHPASS_AVAILABLE=0
if command -v sshpass >/dev/null 2>&1; then
    SSHPASS_AVAILABLE=1
else
    warn "sshpass no está instalado; se usará scp/ssh interactivo."
    warn "En Debian/Ubuntu puedes instalarlo con: sudo apt install sshpass"
fi

# ---------------- Pedir datos del servidor ----------------
echo "=== Deploy Personal SIDS ==="
echo
read -r -p "IP del servidor: " SERVER_IP
read -r -p "Usuario SSH:      " SSH_USER
read -r -s -p "Contraseña SSH:   " SSH_PASS
echo

if [ -z "$SERVER_IP" ] || [ -z "$SSH_USER" ]; then
    error "La IP y el usuario son obligatorios."
    exit 1
fi

SSH_OPTS="-o ConnectTimeout=10 -o StrictHostKeyChecking=accept-new"
DEST="$SSH_USER@$SERVER_IP"

ssh_run() {
    if [ "$SSHPASS_AVAILABLE" -eq 1 ]; then
        sshpass -p "$SSH_PASS" ssh $SSH_OPTS "$@"
    else
        ssh $SSH_OPTS "$@"
    fi
}

scp_run() {
    if [ "$SSHPASS_AVAILABLE" -eq 1 ]; then
        sshpass -p "$SSH_PASS" scp $SSH_OPTS "$@"
    else
        scp $SSH_OPTS "$@"
    fi
}

# Ejecuta un comando remoto con sudo, usando la misma contraseña SSH.
# El primer argumento es "destino" y el resto es el comando a ejecutar como root.
sudo_run() {
    local dest="$1"
    shift
    local remote_cmd="$*"
    if [ "$SSHPASS_AVAILABLE" -eq 1 ]; then
        # Se envía la contraseña por stdin para que sudo la lea con -S
        printf '%s\n' "$SSH_PASS" | sshpass -p "$SSH_PASS" ssh $SSH_OPTS "$dest" \
            "sudo -S bash -c '$remote_cmd'"
    else
        # Interactivo: se asigna un pty para que sudo pida la contraseña
        ssh $SSH_OPTS -t "$dest" "sudo bash -c '$remote_cmd'"
    fi
}

# ---------------- Compilar ----------------
info "Compilando el proyecto (mvn clean package -DskipTests)..."
cd "$BACKEND_DIR"
mvn -q clean package -DskipTests

JAR="$(ls -t target/*.jar 2>/dev/null | grep -v '\.original$' | head -n 1 || true)"
if [ -z "$JAR" ]; then
    error "No se encontró el jar generado en target/."
    exit 1
fi

# ---------------- Renombrar el jar (quitar la versión) ----------------
DEPLOY_JAR="target/personal-sids.jar"
if [ "$(basename "$JAR")" != "$(basename "$DEPLOY_JAR")" ]; then
    info "Renombrando $(basename "$JAR") -> $(basename "$DEPLOY_JAR") ..."
    rm -f "$DEPLOY_JAR"
    mv "$JAR" "$DEPLOY_JAR"
else
    info "El jar ya tiene el nombre final: $(basename "$DEPLOY_JAR")"
fi
JAR="$DEPLOY_JAR"
JAR_NAME="$(basename "$JAR")"
info "Jar a desplegar: $JAR"

# ---------------- Crear carpeta remota ----------------
info "Creando directorio remoto $REMOTE_DIR ..."
ssh_run "$DEST" "mkdir -p '$REMOTE_DIR'"

# ---------------- Subir el jar ----------------
info "Subiendo el jar a $DEST:$REMOTE_DIR/ ..."
scp_run "$JAR" "$DEST:$REMOTE_DIR/$JAR_NAME"

# ---------------- Reiniciar servicio (opcional) ----------------
if [ -n "$SERVICE_NAME" ]; then
    info "Reiniciando el servicio systemd '$SERVICE_NAME' (sudo)..."
    RESTART_CMD="systemctl restart $SERVICE_NAME && systemctl is-active $SERVICE_NAME"
    sudo_run "$DEST" "$RESTART_CMD"
    info "Servicio '$SERVICE_NAME' reiniciado."
else
    warn "SERVICE_NAME no configurado; el jar se copió pero no se reinició ningún servicio."
fi

echo
info "Deploy completado."
