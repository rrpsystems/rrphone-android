# G.729 no Android (`libmsbcg729.so`)

O `linphone-sdk-android` publicado no Maven vem **sem G.729**: o bcg729 não é
incluído, e no mediastreamer2 o filtro G.729 é compilado para dentro da própria
biblioteca, não como plugin. A operadora fala G.711/G.729, então sem ele toda
chamada externa obriga o Asterisk a transcodificar.

Em vez de recompilar o SDK inteiro (o próprio README da Belledonne desaconselha
fazer isso no Windows), esta pasta gera **uma biblioteca à parte** com:

- `msbcg729/g729.c`: o filtro do mediastreamer2 5.5.18, copiado sem alterações;
- `third_party/bcg729/`: o codec (bcg729 1.1.2);
- `msbcg729/plugin.c`: uma função JNI que registra os dois filtros na fábrica
  de mídia do Core já criado.

Ela é ligada às bibliotecas do SDK que já vão no APK (`libmediastreamer2.so`,
`libortp.so`, `libbctoolbox.so`, `liblinphone.so`). Os headers em
`third_party/linphone-sdk-5.5.18/` são dessa mesma versão.

Do lado do app, `core/sip/G729.kt` carrega e registra a biblioteca. Em
`LinphoneManager.start()`, o Core é criado com `[sound] dont_check_codecs=1`:
a lista de codecs é montada na criação, antes de o filtro existir, e sem isso o
G729 seria descartado. Se a biblioteca faltar ou o registro falhar, o app tira
o G729 da oferta sozinho.

## Recompilar

Necessário **sempre que a versão do `linphone-sdk-android` mudar**
(`gradle/libs.versions.toml`). Nesse caso, troque também os headers em
`third_party/linphone-sdk-<versão>/` pelos da nova versão (a partir de
`bctoolbox/include`, `ortp/include` e `mediastreamer2/include` do linphone-sdk
na tag correspondente).

Pré-requisitos: Android NDK r27c (`C:\dev\Android\Sdk\ndk\27.2.12479018`),
CMake e Ninja (os do Qt servem) e um build do app já feito, para o Gradle ter
baixado o AAR.

```powershell
.\native\build-g729.ps1
```

As saídas vão para `app/src/main/jniLibs/<abi>/libmsbcg729.so` (arm64-v8a,
armeabi-v7a, x86_64, x86) e são versionadas, para quem abre no Android Studio
não precisar do NDK.

Para conferir, abra o app e procure no logcat:
`G.729 registrado no mediastreamer2` e `codecs: PCMA/8000, PCMU/8000, G729/8000, opus/48000`.

## Licenças

- bcg729: GNU GPL v3 (`third_party/bcg729/LICENSE.txt`)
- mediastreamer2 (`g729.c` e headers): GNU AGPL v3
  (`third_party/linphone-sdk-5.5.18/LICENSE.txt`)

Distribuir o APK com esta biblioteca exige o mesmo que o desktop: o código-fonte
correspondente disponível para quem recebe o binário. Esta pasta é esse fonte.
