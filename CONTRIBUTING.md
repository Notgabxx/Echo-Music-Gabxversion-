# Como contribuir com o Flux

Que bom que você quer ajudar! Este guia explica como preparar o ambiente, enviar mudanças e relatar problemas.

Ao participar, você concorda com o nosso [código de conduta](CODE_OF_CONDUCT.md).

## Antes de começar

- Procure nas [Issues](https://github.com/Notgabxx/Echo-Music-Gabxversion-/issues) se o assunto já foi discutido.
- Para mudanças grandes, abra uma issue primeiro e explique a ideia. Assim ninguém perde tempo com algo que não vai entrar.
- Falhas de segurança seguem outro caminho: veja o [SECURITY.md](SECURITY.md).

## Ambiente de desenvolvimento

O passo a passo completo está no [SETUP.md](SETUP.md). Em resumo, você vai precisar do JDK 21, do Android SDK 37, do NDK 28.2.13676358 e do CMake 3.22.1.

## Fluxo de trabalho

1. Faça um fork e crie uma branch a partir da `main`, com um nome curto e claro, como `fix/banner-icones` ou `feat/fila-compartilhada`.
2. Faça commits pequenos, no padrão [Conventional Commits](https://www.conventionalcommits.org/pt-br/): `feat`, `fix`, `perf`, `refactor`, `docs`, `ci`, `chore`.
3. Rode o build antes de abrir o pull request: `./gradlew :app:assembleUniversalGmsRelease`.
4. Abra o pull request contra a `main` explicando o que mudou e por quê.
5. **Mudanças visuais precisam de prints ou vídeo** mostrando o antes e o depois.

## Padrões de código

- Kotlin e Jetpack Compose, seguindo o `.editorconfig` do projeto.
- Animações que mudam a cada quadro devem ler o estado só nas fases de desenho ou de camada (`drawBehind`, `graphicsLayer`), sem recompor a tela inteira. Veja o [DESIGN.md](DESIGN.md).
- Textos visíveis vão em `strings.xml`, com tradução em português (`values-pt-rBR`) sempre que possível.
- Não adicione bibliotecas de rastreamento, anúncios ou telemetria.
- Nunca faça commit de chaves, tokens ou arquivos de assinatura.

## Relatando bugs

Uma boa issue de bug tem:

- O que aconteceu e o que você esperava que acontecesse.
- Passo a passo para reproduzir.
- Modelo do aparelho, versão do Android e número da build do Flux.
- Prints, vídeo ou log, se tiver.

## Licença

Ao contribuir, você concorda que o seu código seja distribuído sob a [GPL-3.0](LICENSE), a mesma licença do projeto.
