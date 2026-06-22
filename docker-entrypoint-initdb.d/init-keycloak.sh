#!/bin/bash

# ============================================
# init-keycloak.sh
# Автоматическая настройка Keycloak для User Service
# Версия: 2.0 (для Keycloak 24+)
# ============================================

set -e  # Прерывать выполнение при ошибке

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Функции для цветного вывода
error() { echo -e "${RED}[ERROR]${NC} $1"; }
success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
info() { echo -e "${BLUE}[INFO]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARNING]${NC} $1"; }

# Настройки
KEYCLOAK_URL="http://localhost:7000"
REALM_NAME="charging-stations"
CLIENT_ID="user-service"
CLIENT_SECRET="change-me"  # Должен совпадать с ${KEYCLOAK_CLIENT_SECRET} в application.yml
ADMIN_USERNAME="admin"
ADMIN_PASSWORD="changeme"
FRONTEND_URL="http://localhost:3000"
USER_SERVICE_URL="http://localhost:8005"

# Проверка зависимостей
check_dependencies() {
    info "Проверка зависимостей..."

    if ! command -v curl &> /dev/null; then
        error "curl не установлен. Установите: sudo apt-get install curl"
        exit 1
    fi

    if ! command -v jq &> /dev/null; then
        error "jq не установлен. Установите: sudo apt-get install jq"
        exit 1
    fi

    success "Зависимости проверены"
}

# Ожидание запуска Keycloak
wait_for_keycloak() {
    info "Ожидание запуска Keycloak..."

    local max_retries=30
    local retry_count=0

    while [ $retry_count -lt $max_retries ]; do
        if curl -s -f "${KEYCLOAK_URL}/realms/master" > /dev/null 2>&1; then
            success "Keycloak запущен и готов"
            return 0
        fi

        retry_count=$((retry_count + 1))
        warn "Попытка $retry_count/$max_retries..."
        sleep 5
    done

    error "Keycloak не запустился за отведенное время"
    return 1
}

# Получение токена администратора
get_admin_token() {
    info "Получение токена администратора..."

    local token_response

    token_response=$(curl -s -X POST \
        "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "username=${ADMIN_USERNAME}" \
        -d "password=${ADMIN_PASSWORD}" \
        -d "grant_type=password" \
        -d "client_id=admin-cli" \
        --fail-with-body 2>/dev/null)

    if [ $? -ne 0 ]; then
        error "Не удалось получить токен. Проверьте логин/пароль и доступность Keycloak"
        echo "Response: $token_response"
        return 1
    fi

    ADMIN_TOKEN=$(echo "$token_response" | jq -r '.access_token')

    if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = "null" ]; then
        error "Не удалось извлечь токен из ответа"
        echo "Response: $token_response"
        return 1
    fi

    success "Токен администратора получен"
    return 0
}

# Функция для выполнения запросов к Keycloak API
kc_api() {
    local method=$1
    local endpoint=$2
    local data_file=$3
    local expected_status=$4

    local curl_cmd="curl -s -X ${method} \
        '${KEYCLOAK_URL}${endpoint}' \
        -H 'Authorization: Bearer ${ADMIN_TOKEN}' \
        -H 'Content-Type: application/json'"

    if [ -n "$data_file" ]; then
        curl_cmd="${curl_cmd} --data-binary @${data_file}"
    fi

    # Добавляем флаг для вывода HTTP статуса
    curl_cmd="${curl_cmd} -w ' HTTP_STATUS:%{http_code}'"

    # Выполняем команду
    local response=$(eval "$curl_cmd")
    local http_status=$(echo "$response" | grep -o 'HTTP_STATUS:[0-9]*' | cut -d':' -f2)

    # Убираем статус из ответа
    response=$(echo "$response" | sed 's/ HTTP_STATUS:[0-9]*$//')

    if [ -n "$expected_status" ]; then
        if [ "$http_status" = "$expected_status" ]; then
            echo "$response"
            return 0
        else
            error "HTTP статус $http_status, ожидался $expected_status"
            echo "Response: $response"
            return 1
        fi
    else
        echo "$response"
        return 0
    fi
}

# Создание временного файла с JSON
create_temp_json() {
    local json_content=$1
    local temp_file=$(mktemp)
    echo "$json_content" > "$temp_file"
    echo "$temp_file"
}

# Создание Realm
create_realm() {
    info "Создание Realm '${REALM_NAME}'..."

    local realm_config=$(cat <<EOF
{
    "realm": "${REALM_NAME}",
    "enabled": true,
    "registrationAllowed": false,
    "registrationEmailAsUsername": true,
    "rememberMe": true,
    "verifyEmail": false,
    "loginWithEmailAllowed": true,
    "duplicateEmailsAllowed": false,
    "resetPasswordAllowed": true,
    "editUsernameAllowed": false,
    "bruteForceProtected": true,
    "sslRequired": "external",
    "accessTokenLifespan": 900,
    "refreshTokenLifespan": 604800,
    "ssoSessionIdleTimeout": 1800,
    "ssoSessionMaxLifespan": 36000,
    "attributes": {
        "frontendUrl": "${FRONTEND_URL}"
    }
}
EOF
)

    local temp_file=$(create_temp_json "$realm_config")

    # Проверяем, существует ли уже realm
    if kc_api "GET" "/admin/realms/${REALM_NAME}" "" "200" >/dev/null 2>&1; then
        warn "Realm '${REALM_NAME}' уже существует. Удаляем..."
        kc_api "DELETE" "/admin/realms/${REALM_NAME}" "" "204" >/dev/null 2>&1
        sleep 2
    fi

    # Создаем новый realm
    if kc_api "POST" "/admin/realms" "$temp_file" "201" >/dev/null 2>&1; then
        success "Realm создан"
    else
        error "Ошибка при создании Realm"
        rm "$temp_file"
        return 1
    fi

    rm "$temp_file"
    return 0
}

# Создание ролей Realm
create_realm_roles() {
    info "Создание ролей Realm..."

    local roles=("USER" "CONTRACTOR" "SPECIALIST" "ADMIN")

    for role in "${roles[@]}"; do
        local role_config=$(cat <<EOF
{
    "name": "${role}",
    "description": "Роль ${role}"
}
EOF
)

        local temp_file=$(create_temp_json "$role_config")

        if kc_api "POST" "/admin/realms/${REALM_NAME}/roles" "$temp_file" "201" >/dev/null 2>&1; then
            success "Роль ${role} создана"
        elif kc_api "POST" "/admin/realms/${REALM_NAME}/roles" "$temp_file" "409" >/dev/null 2>&1; then
            warn "Роль ${role} уже существует"
        else
            error "Ошибка при создании роли ${role}"
            rm "$temp_file"
            continue
        fi

        rm "$temp_file"
    done

    return 0
}

# Создание клиента
create_client() {
    info "Создание клиента '${CLIENT_ID}'..."

    local client_config=$(cat <<EOF
{
    "clientId": "${CLIENT_ID}",
    "enabled": true,
    "clientAuthenticatorType": "client-secret",
    "secret": "${CLIENT_SECRET}",
    "redirectUris": [
        "${USER_SERVICE_URL}/*",
        "${FRONTEND_URL}/*",
        "http://localhost:8080/*"
    ],
    "webOrigins": ["*"],
    "publicClient": false,
    "bearerOnly": false,
    "standardFlowEnabled": true,
    "implicitFlowEnabled": false,
    "directAccessGrantsEnabled": true,
    "serviceAccountsEnabled": true,
    "authorizationServicesEnabled": true,
    "protocol": "openid-connect",
    "attributes": {
        "access.token.lifespan": "900",
        "tls.client.certificate.bound.access.tokens": "false",
        "use.refresh.tokens": "true"
    },
    "defaultRoles": ["USER"]
}
EOF
)

    local temp_file=$(create_temp_json "$client_config")

    # Проверяем, существует ли уже клиент
    local clients_response=$(kc_api "GET" "/admin/realms/${REALM_NAME}/clients" "" "200")
    local existing_client=$(echo "$clients_response" | jq -r ".[] | select(.clientId == \"${CLIENT_ID}\") | .id")

    if [ -n "$existing_client" ]; then
        warn "Клиент '${CLIENT_ID}' уже существует. Удаляем..."
        kc_api "DELETE" "/admin/realms/${REALM_NAME}/clients/${existing_client}" "" "204" >/dev/null 2>&1
        sleep 1
    fi

    # Создаем нового клиента
    if kc_api "POST" "/admin/realms/${REALM_NAME}/clients" "$temp_file" "201" >/dev/null 2>&1; then
        success "Клиент создан"
    else
        error "Ошибка при создании клиента"
        rm "$temp_file"
        return 1
    fi

    rm "$temp_file"
    return 0
}

# Получение ID клиента
get_client_id() {
    info "Получение ID клиента..."

    local clients_response=$(kc_api "GET" "/admin/realms/${REALM_NAME}/clients" "" "200")
    local client_id=$(echo "$clients_response" | jq -r ".[] | select(.clientId == \"${CLIENT_ID}\") | .id")

    if [ -z "$client_id" ]; then
        error "Не удалось найти ID клиента"
        return 1
    fi

    echo "$client_id"
    return 0
}

# Создание ролей клиента
create_client_roles() {
    info "Создание ролей клиента..."

    local client_id=$1
    local roles=("USER" "CONTRACTOR" "SPECIALIST" "ADMIN")

    for role in "${roles[@]}"; do
        local role_config=$(cat <<EOF
{
    "name": "${role}",
    "description": "Клиентская роль ${role}"
}
EOF
)

        local temp_file=$(create_temp_json "$role_config")

        if kc_api "POST" "/admin/realms/${REALM_NAME}/clients/${client_id}/roles" "$temp_file" "201" >/dev/null 2>&1; then
            success "Клиентская роль ${role} создана"
        elif kc_api "POST" "/admin/realms/${REALM_NAME}/clients/${client_id}/roles" "$temp_file" "409" >/dev/null 2>&1; then
            warn "Клиентская роль ${role} уже существует"
        else
            error "Ошибка при создании клиентской роли ${role}"
            rm "$temp_file"
            continue
        fi

        rm "$temp_file"
    done

    return 0
}

# Создание администратора
create_admin_user() {
    info "Создание администратора..."

    local admin_user_config=$(cat <<EOF
{
    "username": "admin@charging-stations.com",
    "email": "admin@charging-stations.com",
    "firstName": "System",
    "lastName": "Administrator",
    "enabled": true,
    "emailVerified": true,
    "credentials": [
        {
            "type": "password",
            "value": "Admin123!",
            "temporary": false
        }
    ],
    "attributes": {
        "phone": "+77771234567"
    }
}
EOF
)

    local temp_file=$(create_temp_json "$admin_user_config")

    # Создаем пользователя
    local user_response=$(kc_api "POST" "/admin/realms/${REALM_NAME}/users" "$temp_file" "201")
    local user_location=$(echo "$user_response" | grep -i "location:" | cut -d' ' -f2 | tr -d '\r')

    if [ -z "$user_location" ]; then
        # Возможно, пользователь уже существует
        warn "Пользователь уже существует или ошибка при создании. Получаем существующего..."

        # Ищем пользователя по email
        local users_response=$(kc_api "GET" "/admin/realms/${REALM_NAME}/users?email=admin@charging-stations.com" "" "200")
        local user_id=$(echo "$users_response" | jq -r '.[0].id')

        if [ -z "$user_id" ] || [ "$user_id" = "null" ]; then
            error "Не удалось найти или создать пользователя"
            rm "$temp_file"
            return 1
        fi

        user_location="/admin/realms/${REALM_NAME}/users/${user_id}"
    fi

    # Извлекаем ID пользователя из URL
    local user_id=$(echo "$user_location" | rev | cut -d'/' -f1 | rev)

    success "Администратор создан, ID: $user_id"

    # Получаем ID роли ADMIN
    local role_response=$(kc_api "GET" "/admin/realms/${REALM_NAME}/roles/ADMIN" "" "200")
    local role_id=$(echo "$role_response" | jq -r '.id')

    if [ -n "$role_id" ] && [ "$role_id" != "null" ]; then
        # Назначаем роль ADMIN
        local role_assignment=$(cat <<EOF
[
    {
        "id": "${role_id}",
        "name": "ADMIN"
    }
]
EOF
)

        local role_temp_file=$(create_temp_json "$role_assignment")

        if kc_api "POST" "/admin/realms/${REALM_NAME}/users/${user_id}/role-mappings/realm" "$role_temp_file" "204" >/dev/null 2>&1; then
            success "Роль ADMIN назначена администратору"
        else
            warn "Не удалось назначить роль ADMIN"
        fi

        rm "$role_temp_file"
    fi

    rm "$temp_file"
    return 0
}

# Настройка Email (опционально)
setup_email() {
    info "Настройка Email..."

    local email_config=$(cat <<EOF
{
    "host": "smtp.yandex.ru",
    "port": "587",
    "from": "no-reply@charging-stations.com",
    "fromDisplayName": "Charging Stations",
    "starttls": true,
    "auth": true,
    "user": "your-email@yandex.ru",
    "password": "your-password"
}
EOF
)

    local temp_file=$(create_temp_json "$email_config")

    if kc_api "PUT" "/admin/realms/${REALM_NAME}/smtp-server" "$temp_file" "204" >/dev/null 2>&1; then
        success "Настройки Email обновлены"
    else
        warn "Не удалось настроить Email (возможно, неверные учетные данные)"
    fi

    rm "$temp_file"
}

# Настройка локализации
setup_localization() {
    info "Настройка локализации..."

    local localization_config=$(cat <<EOF
{
    "internationalizationEnabled": true,
    "supportedLocales": ["en", "ru", "kk"],
    "defaultLocale": "ru"
}
EOF
)

    local temp_file=$(create_temp_json "$localization_config")

    if kc_api "PUT" "/admin/realms/${REALM_NAME}/localization" "$temp_file" "204" >/dev/null 2>&1; then
        success "Локализация настроена"
    else
        warn "Не удалось настроить локализацию"
    fi

    rm "$temp_file"
}

# Настройка политики паролей
setup_password_policy() {
    info "Настройка политики паролей..."

    local password_policy=$(cat <<EOF
{
    "passwordPolicy": "length(8) and digits(1) and lowerCase(1) and upperCase(1) and specialChars(1)"
}
EOF
)

    local temp_file=$(create_temp_json "$password_policy")

    if kc_api "PUT" "/admin/realms/${REALM_NAME}" "$temp_file" "204" >/dev/null 2>&1; then
        success "Политика паролей настроена"
    else
        warn "Не удалось настроить политику паролей"
    fi

    rm "$temp_file"
}

# Основная функция
main() {
    echo "========================================="
    echo "   Настройка Keycloak для User Service   "
    echo "========================================="
    echo ""

    # Проверка зависимостей
    check_dependencies

    # Ожидание Keycloak
    wait_for_keycloak

    # Получение токена
    if ! get_admin_token; then
        exit 1
    fi

    # Создание Realm
    if ! create_realm; then
        exit 1
    fi

    # Создание ролей Realm
    create_realm_roles

    # Создание клиента
    if ! create_client; then
        exit 1
    fi

    # Получение ID клиента
    CLIENT_UUID=$(get_client_id)
    if [ -z "$CLIENT_UUID" ]; then
        exit 1
    fi

    # Создание ролей клиента
    create_client_roles "$CLIENT_UUID"

    # Создание администратора
    create_admin_user

    # Дополнительные настройки (опционально)
    setup_email
    setup_localization
    setup_password_policy

    echo ""
    echo "========================================="
    echo "        НАСТРОЙКА ЗАВЕРШЕНА!            "
    echo "========================================="
    echo ""
    echo "ИНФОРМАЦИЯ ДЛЯ НАСТРОЙКИ:"
    echo "-----------------------------------------"
    echo "Realm:                 ${REALM_NAME}"
    echo "Client ID:             ${CLIENT_ID}"
    echo "Client Secret:         ${CLIENT_SECRET}"
    echo "Admin Console:         ${KEYCLOAK_URL}"
    echo "Admin Username:        admin"
    echo "Admin Password:        changeme"
    echo ""
    echo "Тестовый пользователь:"
    echo "  Email:    admin@charging-stations.com"
    echo "  Password: Admin123!"
    echo "  Role:     ADMIN"
    echo ""
    echo "Для user-service в application.yml:"
    echo "  keycloak:"
    echo "    auth-server-url: ${KEYCLOAK_URL}"
    echo "    realm: ${REALM_NAME}"
    echo "    resource: ${CLIENT_ID}"
    echo "    credentials:"
    echo "      secret: ${CLIENT_SECRET}"
    echo "========================================="
}

# Запуск основной функции
main