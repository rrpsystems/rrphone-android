# RRP Softphone — Android

Softphone SIP para Android da RRP Systems. É o mesmo produto do
[RRP Softphone para Windows](https://github.com/RRPSystems/rrphone-desktop):
mesmo motor (liblinphone), mesma paleta e as mesmas funções, adaptadas ao toque.

- Conta SIP única (UDP/TCP/TLS), registro com senha guardada no Android Keystore
- Chamada efetuada e recebida, integrada ao sistema (ConnectionService):
  tela de bloqueio, Bluetooth, áudio de ligação
- Mudo, espera, teclado DTMF, alto-falante / fone / Bluetooth
- Transferência num fluxo só: com consulta se o destino atende, cega se não
- Chamada em espera (duas chamadas, alternar, terceira recebe ocupado)
- Não perturbe e siga-me (em Ajustes, para não serem acionados sem querer)
- Histórico local, incluindo chamadas atendidas em outro aparelho do ramal
- Agenda remota em XML (`<contacts>/<contact>`, o mesmo formato do desktop) e
  contatos locais
- Codecs PCMA, PCMU, G.729 e OPUS, com ordem e habilitação; método de DTMF
- Importar/exportar `.rrpprofile`, compatível com o desktop (AES-256-GCM)

## Compilando

Android Studio (ou `gradlew assembleDebug`), JDK 17+, `minSdk` 28.

Dois arquivos ficam **fora do repositório** e precisam existir localmente:

| Arquivo | Para quê | Sem ele |
|---|---|---|
| `app/google-services.json` | Firebase (push) | o build falha; gere um no console do Firebase para o seu projeto |
| `profile_key.txt` (64 hex) | chave do `.rrpprofile` | usa a chave de desenvolvimento publicada; perfis não abrem entre builds com chaves diferentes |

O `profile_key.txt` também é procurado em `../desktop/profile_key.txt`, para os
dois apps usarem a mesma chave.

### G.729

O `linphone-sdk-android` do Maven não traz G.729. Ele vem de
`app/src/main/jniLibs/*/libmsbcg729.so`, compilada a partir de `native/`.
As `.so` estão versionadas; recompile só ao mudar a versão do SDK — ver
[`native/README.md`](native/README.md).

## Publicando no Play

O Play recebe um `.aab` assinado com a **chave de upload**. A chave final do
app fica com o Google (Play App Signing); se a de upload se perder, o Google
troca, mas dá trabalho, então guarde uma cópia do `.jks` e das senhas em
local seguro, fora deste repositório.

1. Criar a chave de upload (uma vez só), fora da pasta do projeto:

   ```
   keytool -genkeypair -v -keystore C:\dev\RRPhone\keys\rrphone-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Criar `keystore.properties` na raiz deste projeto (está no `.gitignore`):

   ```
   storeFile=../keys/rrphone-upload.jks
   storePassword=...
   keyAlias=upload
   keyPassword=...
   ```

3. Subir `versionCode` em `app/build.gradle.kts` (o Play recusa um número já
   enviado) e ajustar `versionName`.

4. `gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.

Política de privacidade: https://rrpsystems.github.io/rrphone-desktop/privacidade.html

## Licença

GNU GPL v3 (ver [LICENSE](LICENSE)). O app usa o liblinphone (GPL v3), o
bcg729 (GPL v3) e o mediastreamer2 (AGPL v3); quem distribuir o APK precisa
oferecer o código-fonte correspondente, que é este repositório.

Copyright (C) 2026 RRP Systems Ltda.
