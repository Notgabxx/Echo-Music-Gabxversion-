<div align="center">

# Flux

**Cliente de música para Android, sem anúncios, com letras sincronizadas, ouvir junto e uma interface fluida.**

[Baixar a versão mais recente](https://github.com/Notgabxx/Echo-Music-Gabxversion-/releases)

</div>

---

## O que tem no Flux

- **Ouvir junto:** crie uma sala e escute a mesma música em sincronia com amigos (banner direto na tela inicial).
- **Player fluido:** o mini player se transforma no player completo com capa compartilhada, física de mola e profundidade.
- **Aura reativa ao áudio:** luz atrás da capa que reage a graves, médios e agudos.
- **Cores da capa:** o tema e o fundo das telas acompanham a música tocando, com contraste automático para manter o texto legível.
- **Liquid glass:** barras e cartões com vidro fosco refinado.
- Letras sincronizadas, downloads offline, reconhecimento de músicas, playlists com IA, equalizador e muito mais.

Os efeitos podem ser ligados ou desligados em **Configurações → Aparência**.

## Compilação

Cada push nas branches `main` ou `premium-ui` dispara o workflow [`flux-apk.yml`](.github/workflows/flux-apk.yml), que gera um APK otimizado e o publica em **Releases**.

Localmente:

```bash
./gradlew :app:assembleUniversalGmsRelease
```

## Créditos

O Flux é um fork do **[Echo Music](https://github.com/EchoMusicApp/Echo-Music)** e é distribuído sob a mesma licença, a **GNU GPL v3.0** (veja [LICENSE](LICENSE)). O código-fonte completo está neste repositório.

Projetos open source que também fazem parte desta base:

| Projeto | Contribuição |
| :------ | :----------- |
| **[Echo Music](https://github.com/EchoMusicApp/Echo-Music)** | Base do aplicativo (fork) |
| **[Metrolist](https://github.com/MetrolistGroup/Metrolist)** & **[Vivi Music](https://github.com/vivizzz007/vivi-music)** | Inspiração e referência de arquitetura; servidor comunitário do Ouvir junto |
| **[ArchiveTune](https://github.com/koiverse/ArchiveTune)** | Inspiração de UI Material You |
| **[Better Lyrics](https://better-lyrics.boidu.dev/)** | Aprimoramento e sincronização de letras |
| **[SimpMusic](https://github.com/maxrave-dev/SimpMusic)** | Referência de implementação de letras |
| **[Music Recognizer](https://github.com/aleksey-saenko/MusicRecognizer)** | Reconhecimento de áudio |
| **[BravePipe](https://github.com/bravepipeproject/BravePipe)** | Decriptação e mecanismo de reprodução reserva |
| **[Kyant0/backdrop](https://github.com/Kyant0/backdrop)** | Efeito liquid glass (Apache 2.0) |

## Aviso legal

O Flux é um projeto open source, gratuito e sem fins comerciais. Ele não hospeda nem distribui conteúdo protegido: todo o áudio e vídeo vem dos servidores públicos do YouTube e do YouTube Music e pertence aos respectivos donos. Para apoiar os artistas, considere assinar o YouTube Premium.

O software é fornecido "como está", sem garantia de qualquer tipo. Cada usuário é responsável por usar o app de acordo com as leis locais e os termos de serviço das plataformas acessadas.

<div align="center">
  <p>Licenciado sob <a href="LICENSE">GPL-3.0</a></p>
</div>
