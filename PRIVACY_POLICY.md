# Política de privacidade do Flux

_Última atualização: outubro de 2026_

O Flux é um app open source, gratuito e sem fins comerciais. Esta política explica, de forma direta, quais dados o app usa e com quem ele conversa para funcionar.

## Em resumo

- O Flux **não tem contas próprias, anúncios, rastreamento nem telemetria**. Ele não envia estatísticas de uso nem relatórios de falhas para os mantenedores.
- Seu histórico, suas playlists, seus downloads e suas configurações ficam **no seu aparelho**.
- Para tocar músicas, buscar letras e usar alguns recursos, o app se conecta **diretamente** a serviços de terceiros. Cada serviço tem a própria política de privacidade.
- Nenhum dado é vendido ou compartilhado com anunciantes.

## Dados guardados no aparelho

- Histórico de reprodução, curtidas, playlists, fila e estatísticas de escuta (usadas, por exemplo, para montar os mixes inteligentes, que são calculados no próprio aparelho).
- Músicas baixadas e cache de áudio, capas e letras.
- Preferências do app e, se você fizer login, os dados de sessão dos serviços conectados.

Esses dados só saem do aparelho se você exportar um backup. Desinstalar o app ou limpar os dados dele apaga tudo.

## Serviços de terceiros usados pelo app

**Reprodução e catálogo.** As buscas, recomendações e o áudio vêm do YouTube e do YouTube Music. Em caso de falha, o app pode usar instâncias públicas alternativas (como o Piped). Recursos opcionais consultam o SponsorBlock (para pular trechos) e o Return YouTube Dislike.

**Letras.** As letras são buscadas em provedores públicos, como LRCLIB, KuGou, Better Lyrics/Unison, Paxsenix, SimpMusic e espelhos do LyricsPlus. O app envia só dados da música, como título, artista e duração.

**Capas e vídeos de fundo.** Capas em alta resolução e vídeos curtos de fundo podem ser buscados no Apple Music, no Tidal e em arquivos públicos de artes. Informações de artistas podem vir da Wikipédia.

**Descobrir música (reconhecimento).** O áudio é transformado no próprio aparelho em uma "impressão digital" e só essa impressão digital é enviada ao serviço de reconhecimento da Shazam. A gravação em si não é enviada nem guardada. No modo **Neste celular**, o Flux capta o som tocado por outros apps somente depois que você inicia o reconhecimento e aprova o aviso do Android, e para de captar assim que termina. O som do próprio Flux não é captado.

**Ouvir junto.** Quando você cria ou entra em uma sala, o código da sala, o nome que você escolher e o estado da reprodução (música, posição, play/pausa) passam por um servidor comunitário e ficam visíveis para quem está na sala.

**Clima.** Recursos que levam o clima em conta consultam a previsão no Open-Meteo usando uma localização aproximada, obtida pelo seu IP (via ipapi.co ou ipwho.is) ou, se você permitir, pela localização do aparelho.

**Atualizações.** O app consulta a API do GitHub para saber se existe uma versão nova.

## Integrações opcionais

Estas integrações só funcionam se você ativá-las ou fizer login:

- **YouTube Music**, para sincronizar sua biblioteca.
- **Spotify**, para importar playlists.
- **Last.fm** e **ListenBrainz**, para registrar o que você ouve.
- **Discord**, para mostrar a música atual no seu status.
- **Provedores de IA**, como OpenRouter, OpenAI, Anthropic, Google Gemini, Mistral, Groq, xAI, Perplexity e NVIDIA, além do **DeepL**, para tradução de letras e outros recursos de IA. Eles usam a chave de API que você mesmo cadastrar, e o texto enviado (como a letra a traduzir) é processado pelo provedor escolhido.

## Permissões do Android

- **Microfone:** usado só durante o reconhecimento de música. No modo Neste celular, o Android também pede autorização para captar o áudio do aparelho.
- **Notificações:** controles de reprodução, downloads e resultados do reconhecimento.
- **Localização (opcional):** recursos baseados no clima.
- **Armazenamento e mídia:** tocar músicas salvas no aparelho e exportar arquivos.
- **Bluetooth e dispositivos próximos:** saída de áudio e transmissão para outros aparelhos.

Você pode revogar qualquer permissão nas configurações do Android.

## Crianças

O Flux não coleta dados pessoais de ninguém, incluindo crianças. O uso dos serviços de terceiros segue as regras de idade de cada um deles.

## Mudanças nesta política

Quando a política mudar, a nova versão será publicada neste arquivo, com a data atualizada no topo.

## Contato

Dúvidas e pedidos podem ser enviados pelas [Issues do repositório](https://github.com/Notgabxx/Echo-Music-Gabxversion-/issues).
