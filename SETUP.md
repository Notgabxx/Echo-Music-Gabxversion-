# Configuração do ambiente

Este guia mostra como compilar o Flux no seu computador.

## Requisitos

- Android Studio atualizado (ou só a linha de comando)
- JDK 21
- Android SDK 37, NDK `28.2.13676358` e CMake `3.22.1` (instale pelo SDK Manager)
- Git

## 1. Clonar o repositório

```bash
git clone https://github.com/Notgabxx/Echo-Music-Gabxversion-.git Flux
cd Flux
```

## 2. Apontar o SDK

Crie o `local.properties` a partir do modelo e informe o caminho do Android SDK:

```bash
cp local.properties.template local.properties
```

```properties
sdk.dir=/caminho/para/o/Android/sdk
```

## 3. Compilar

O Flux tem duas variantes: **FOSS** (sem Google Play Services) e **GMS** (com transmissão para Chromecast).

```bash
# Debug
./gradlew assembleUniversalFossDebug
./gradlew assembleUniversalGmsDebug

# Release (precisa de assinatura, veja abaixo)
./gradlew assembleUniversalFossRelease
./gradlew assembleUniversalGmsRelease
```

No Windows, use `.\gradlew.bat` no lugar de `./gradlew`.

## 4. Assinatura das builds de release

A build de release procura a chave em `keystore.jks`, na raiz do projeto, e lê as senhas das variáveis de ambiente:

```bash
export STORE_PASSWORD=sua_senha_do_keystore
export KEY_ALIAS=seu_alias
export KEY_PASSWORD=sua_senha_da_chave
```

Para criar uma chave nova:

```bash
keytool -genkeypair -keystore keystore.jks -alias flux -keyalg RSA -keysize 2048 -validity 10000
```

Guarde essa chave em um lugar seguro e **nunca** faça commit dela. Builds assinadas com chaves diferentes não instalam por cima umas das outras.

### Assinatura fixa no GitHub Actions

O workflow [`flux-apk.yml`](.github/workflows/flux-apk.yml) usa uma chave fixa quando o repositório tem estes secrets (em **Settings → Secrets and variables → Actions**):

| Secret | Conteúdo |
| :-- | :-- |
| `FLUX_KEYSTORE_BASE64` | O arquivo `keystore.jks` em base64 (`base64 -w0 keystore.jks`) |
| `FLUX_KEYSTORE_PASSWORD` | A senha do keystore e da chave |
| `FLUX_KEY_ALIAS` | O alias da chave (opcional, padrão `flux`) |

Sem esses secrets, cada build é assinada com uma chave temporária.

## 5. Recursos opcionais

- **Tradução de letras e outros recursos de IA:** nas configurações de IA do app, escolha o provedor e informe a sua chave de API.
- **Firebase:** o Flux não usa Firebase. Se você colocar um `app/google-services.json` no projeto, os plugins do Firebase são aplicados automaticamente, mas isso não é necessário.

## Arquivos que nunca devem ir para o Git

- `local.properties`
- `gradle.properties` com dados pessoais
- `*.keystore` / `*.jks`
- `app/google-services.json`

## Problemas comuns

**"SDK location not found":** confira o `sdk.dir` no `local.properties`.

**Erros estranhos depois de atualizar o projeto:** limpe e compile de novo.

```bash
./gradlew clean
./gradlew assembleUniversalGmsDebug
```

Mais detalhes sobre como contribuir estão no [CONTRIBUTING.md](CONTRIBUTING.md).
