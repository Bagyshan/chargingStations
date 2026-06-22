#!/usr/bin/env python3
"""
Настройка Keycloak для User Service
Требуется: Python 3.7+, requests
Установка зависимостей: pip install requests
"""

import json
import time
import requests
import sys
from typing import Optional, Dict, Any

class KeycloakConfigurator:
    def __init__(self):
        self.base_url = "http://localhost:7000"
        self.realm_name = "charging-stations"
        self.client_id = "user-service"
        self.client_secret = "change-me"
        self.admin_username = "admin"
        self.admin_password = "changeme"
        self.frontend_url = "http://localhost:3000"
        self.user_service_url = "http://localhost:8005"

        self.admin_token = None
        self.session = requests.Session()
        self.session.headers.update({
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        })

    def print_info(self, message: str):
        print(f"\033[94m[INFO]\033[0m {message}")

    def print_success(self, message: str):
        print(f"\033[92m[SUCCESS]\033[0m {message}")

    def print_warning(self, message: str):
        print(f"\033[93m[WARNING]\033[0m {message}")

    def print_error(self, message: str):
        print(f"\033[91m[ERROR]\033[0m {message}")

    def wait_for_keycloak(self, max_retries: int = 30, delay: int = 5) -> bool:
        """Ожидание запуска Keycloak"""
        self.print_info("Ожидание запуска Keycloak...")

        for i in range(max_retries):
            try:
                response = self.session.get(f"{self.base_url}/realms/master", timeout=5)
                if response.status_code == 200:
                    self.print_success("Keycloak запущен и готов")
                    return True
            except requests.exceptions.RequestException:
                pass

            if i < max_retries - 1:
                self.print_warning(f"Попытка {i+1}/{max_retries}...")
                time.sleep(delay)

        self.print_error("Keycloak не запустился за отведенное время")
        return False

    def get_admin_token(self) -> bool:
        """Получение токена администратора"""
        self.print_info("Получение токена администратора...")

        token_url = f"{self.base_url}/realms/master/protocol/openid-connect/token"
        data = {
            'username': self.admin_username,
            'password': self.admin_password,
            'grant_type': 'password',
            'client_id': 'admin-cli'
        }

        try:
            response = self.session.post(token_url, data=data, headers={
                'Content-Type': 'application/x-www-form-urlencoded'
            })
            response.raise_for_status()

            token_data = response.json()
            self.admin_token = token_data.get('access_token')

            if not self.admin_token:
                self.print_error("Не удалось получить токен")
                return False

            self.session.headers.update({
                'Authorization': f'Bearer {self.admin_token}'
            })

            self.print_success("Токен администратора получен")
            return True

        except requests.exceptions.RequestException as e:
            self.print_error(f"Ошибка при получении токена: {e}")
            return False

    def kc_request(self, method: str, endpoint: str, data: Optional[Dict] = None,
                  expected_status: int = None) -> Optional[Dict]:
        """Выполнение запроса к Keycloak API"""
        url = f"{self.base_url}{endpoint}"

        try:
            if method.upper() == 'GET':
                response = self.session.get(url)
            elif method.upper() == 'POST':
                response = self.session.post(url, json=data)
            elif method.upper() == 'PUT':
                response = self.session.put(url, json=data)
            elif method.upper() == 'DELETE':
                response = self.session.delete(url)
            else:
                self.print_error(f"Неизвестный метод: {method}")
                return None

#             if expected_status and response.status_code != expected_status:
#                 self.print_warning(f"HTTP {response.status_code} (ожидался {expected_status})")
#                 if response.text:
#                     self.print_warning(f"Ответ: {response.text}")
#                 return None

            if response.status_code == 204:  # No Content
                return {}

            if response.text:
                return response.json()
            return {}

        except requests.exceptions.RequestException as e:
            self.print_error(f"Ошибка запроса: {e}")
            return None

    def create_realm(self) -> bool:
        """Создание Realm"""
        self.print_info(f"Создание Realm '{self.realm_name}'...")

        # Проверяем, существует ли уже realm
        check_response = self.kc_request('GET', f"/admin/realms/{self.realm_name}")
#         if check_response is not None:
#             self.print_warning(f"Realm '{self.realm_name}' уже существует. Удаляем...")
#             self.kc_request('DELETE', f"/admin/realms/{self.realm_name}", expected_status=204)
#             time.sleep(2)

        # Создаем новый realm
        realm_config = {
            "realm": self.realm_name,
            "enabled": True,
            "registrationAllowed": False,
            "registrationEmailAsUsername": True,
            "rememberMe": True,
            "verifyEmail": False,
            "loginWithEmailAllowed": True,
            "duplicateEmailsAllowed": False,
            "resetPasswordAllowed": True,
            "editUsernameAllowed": False,
            "bruteForceProtected": True,
            "sslRequired": "external",
            "accessTokenLifespan": 900,
            "refreshTokenLifespan": 604800,
            "ssoSessionIdleTimeout": 1800,
            "ssoSessionMaxLifespan": 36000,
            "attributes": {
                "frontendUrl": self.frontend_url
            }
        }

        response = self.kc_request('POST', "/admin/realms", realm_config, expected_status=201)
        if response is not None:
            self.print_success("Realm создан")
            return True

        self.print_error("Ошибка при создании Realm")
        return False

    def create_realm_roles(self) -> bool:
        """Создание ролей Realm"""
        self.print_info("Создание ролей Realm...")

        roles = ["USER", "CONTRACTOR", "SPECIALIST", "ADMIN"]
        success = True

        for role in roles:
            role_config = {
                "name": role,
                "description": f"Роль {role}"
            }

            response = self.kc_request('POST', f"/admin/realms/{self.realm_name}/roles",
                                      role_config, expected_status=201)

            if response is not None:
                self.print_success(f"Роль {role} создана")
            else:
                # Проверяем, может роль уже существует
                check_response = self.kc_request('GET', f"/admin/realms/{self.realm_name}/roles/{role}")
                if check_response is not None:
                    self.print_warning(f"Роль {role} уже существует")
                else:
                    self.print_error(f"Ошибка при создании роли {role}")
                    success = False

        return success

    def create_client(self) -> Optional[str]:
        """Создание клиента"""
        self.print_info(f"Создание клиента '{self.client_id}'...")

        # Проверяем, существует ли уже клиент
        clients = self.kc_request('GET', f"/admin/realms/{self.realm_name}/clients")
        if clients:
            for client in clients:
                if client.get('clientId') == self.client_id:
                    self.print_warning(f"Клиент '{self.client_id}' уже существует. Удаляем...")
                    self.kc_request('DELETE', f"/admin/realms/{self.realm_name}/clients/{client['id']}",
                                   expected_status=204)
                    time.sleep(1)
                    break

        # Создаем нового клиента
        client_config = {
            "clientId": self.client_id,
            "enabled": True,
            "clientAuthenticatorType": "client-secret",
            "secret": self.client_secret,
            "redirectUris": [
                f"{self.user_service_url}/*",
                f"{self.frontend_url}/*",
                "http://localhost:8080/*"
            ],
            "webOrigins": ["*"],
            "publicClient": False,
            "bearerOnly": False,
            "standardFlowEnabled": True,
            "implicitFlowEnabled": False,
            "directAccessGrantsEnabled": True,
            "serviceAccountsEnabled": True,
            "authorizationServicesEnabled": True,
            "protocol": "openid-connect",
            "attributes": {
                "access.token.lifespan": "900",
                "tls.client.certificate.bound.access.tokens": "false",
                "use.refresh.tokens": "true"
            },
            "defaultRoles": ["USER"]
        }

        response = self.kc_request('POST', f"/admin/realms/{self.realm_name}/clients",
                                  client_config, expected_status=201)

        if response is not None:
            self.print_success("Клиент создан")

            # Получаем ID клиента
            clients = self.kc_request('GET', f"/admin/realms/{self.realm_name}/clients")
            if clients:
                for client in clients:
                    if client.get('clientId') == self.client_id:
                        return client['id']

        self.print_error("Ошибка при создании клиента")
        return None

    def create_client_roles(self, client_id: str) -> bool:
        """Создание ролей клиента"""
        self.print_info("Создание ролей клиента...")

        roles = ["USER", "CONTRACTOR", "SPECIALIST", "ADMIN"]
        success = True

        for role in roles:
            role_config = {
                "name": role,
                "description": f"Клиентская роль {role}"
            }

            response = self.kc_request('POST',
                                      f"/admin/realms/{self.realm_name}/clients/{client_id}/roles",
                                      role_config, expected_status=201)

            if response is not None:
                self.print_success(f"Клиентская роль {role} создана")
            else:
                self.print_warning(f"Клиентская роль {role} уже существует или ошибка")

        return success

    def create_admin_user(self) -> Optional[str]:
        """Создание администратора"""
        self.print_info("Создание администратора...")

        admin_config = {
            "username": "admin@charging-stations.com",
            "email": "admin@charging-stations.com",
            "firstName": "System",
            "lastName": "Administrator",
            "enabled": True,
            "emailVerified": True,
            "credentials": [
                {
                    "type": "password",
                    "value": "Admin123!",
                    "temporary": False
                }
            ],
            "attributes": {
                "phone": "+77771234567"
            }
        }

        response = self.kc_request('POST', f"/admin/realms/{self.realm_name}/users",
                                  admin_config, expected_status=201)

        if response is None:
            # Проверяем, может пользователь уже существует
            users = self.kc_request('GET',
                                   f"/admin/realms/{self.realm_name}/users?email=admin@charging-stations.com")
            if users and len(users) > 0:
                self.print_warning("Администратор уже существует")
                return users[0]['id']
            else:
                self.print_error("Ошибка при создании администратора")
                return None

        # Получаем ID созданного пользователя из заголовка Location
        user_id = None
        if hasattr(response, 'headers') and 'Location' in response.headers:
            location = response.headers['Location']
            user_id = location.split('/')[-1]

        if not user_id:
            self.print_error("Не удалось получить ID пользователя")
            return None

        self.print_success(f"Администратор создан, ID: {user_id}")

        # Назначаем роль ADMIN
        role = self.kc_request('GET', f"/admin/realms/{self.realm_name}/roles/ADMIN")
        if role:
            role_assignment = [{"id": role['id'], "name": "ADMIN"}]
            self.kc_request('POST',
                           f"/admin/realms/{self.realm_name}/users/{user_id}/role-mappings/realm",
                           role_assignment, expected_status=204)
            self.print_success("Роль ADMIN назначена администратору")

        return user_id

    def setup_email(self):
        """Настройка Email (опционально)"""
        self.print_info("Настройка Email...")

        email_config = {
            "host": "smtp.yandex.ru",
            "port": "587",
            "from": "no-reply@charging-stations.com",
            "fromDisplayName": "Charging Stations",
            "starttls": True,
            "auth": True,
            "user": "your-email@yandex.ru",
            "password": "your-password"
        }

        response = self.kc_request('PUT', f"/admin/realms/{self.realm_name}/smtp-server",
                                  email_config, expected_status=204)

        if response is not None:
            self.print_success("Настройки Email обновлены")
        else:
            self.print_warning("Не удалось настроить Email")

    def setup_localization(self):
        """Настройка локализации"""
        self.print_info("Настройка локализации...")

        localization_config = {
            "internationalizationEnabled": True,
            "supportedLocales": ["en", "ru", "kk"],
            "defaultLocale": "ru"
        }

        response = self.kc_request('PUT', f"/admin/realms/{self.realm_name}/localization",
                                  localization_config, expected_status=204)

        if response is not None:
            self.print_success("Локализация настроена")
        else:
            self.print_warning("Не удалось настроить локализацию")

    def setup_password_policy(self):
        """Настройка политики паролей"""
        self.print_info("Настройка политики паролей...")

        password_policy = {
            "passwordPolicy": "length(8) and digits(1) and lowerCase(1) and upperCase(1) and specialChars(1)"
        }

        response = self.kc_request('PUT', f"/admin/realms/{self.realm_name}",
                                  password_policy, expected_status=204)

        if response is not None:
            self.print_success("Политика паролей настроена")
        else:
            self.print_warning("Не удалось настроить политику паролей")

    def run(self):
        """Основной метод запуска настройки"""
        print("=" * 50)
        print("   Настройка Keycloak для User Service   ")
        print("=" * 50)
        print()

        # 1. Ожидание Keycloak
        if not self.wait_for_keycloak():
            sys.exit(1)

        # 2. Получение токена
        if not self.get_admin_token():
            sys.exit(1)

        # 3. Создание Realm
        if not self.create_realm():
            sys.exit(1)

        # 4. Создание ролей Realm
        self.create_realm_roles()

        # 5. Создание клиента
        client_id = self.create_client()
        if not client_id:
            sys.exit(1)

        # 6. Создание ролей клиента
        self.create_client_roles(client_id)

        # 7. Создание администратора
        self.create_admin_user()

        # 8. Дополнительные настройки
        self.setup_email()
        self.setup_localization()
        self.setup_password_policy()

        print()
        print("=" * 50)
        print("        НАСТРОЙКА ЗАВЕРШЕНА!            ")
        print("=" * 50)
        print()
        print("ИНФОРМАЦИЯ ДЛЯ НАСТРОЙКИ:")
        print("-" * 50)
        print(f"Realm:                 {self.realm_name}")
        print(f"Client ID:             {self.client_id}")
        print(f"Client Secret:         {self.client_secret}")
        print(f"Admin Console:         {self.base_url}")
        print(f"Admin Username:        {self.admin_username}")
        print(f"Admin Password:        {self.admin_password}")
        print()
        print("Тестовый пользователь:")
        print("  Email:    admin@charging-stations.com")
        print("  Password: Admin123!")
        print("  Role:     ADMIN")
        print()
        print("Для user-service в application.yml:")
        print(f"  keycloak:")
        print(f"    auth-server-url: {self.base_url}")
        print(f"    realm: {self.realm_name}")
        print(f"    resource: {self.client_id}")
        print(f"    credentials:")
        print(f"      secret: {self.client_secret}")
        print("=" * 50)

def main():
    """Точка входа"""
    configurator = KeycloakConfigurator()
    configurator.run()

if __name__ == "__main__":
    main()