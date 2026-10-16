# Temas do Flux

Um tema do Flux é um arquivo `.json`. Para usar, abra **Configurações → Aparência → Temas**, toque em **Importar tema** e escolha o arquivo. Depois toque em **Aplicar**: o Flux recarrega a tela e o tema vale para o app inteiro. Não é preciso instalar outro APK.

## Exemplo mínimo

Só `format`, `id`, `name` e uma cor são obrigatórios. As cores que faltarem são geradas a partir da cor `seed`, com o Material You:

```json
{
  "format": "flux-theme",
  "id": "meu-tema",
  "name": "Meu tema",
  "mode": "dark",
  "colors": { "seed": "#E8457C" }
}
```

## Campos

| Campo | O que faz |
| :-- | :-- |
| `format` | Sempre `"flux-theme"`. |
| `version` | Versão do formato. Hoje, `1`. |
| `id` | Identificador único (letras minúsculas, números, `-`, `_` e `.`). Importar outro arquivo com o mesmo `id` substitui o anterior. |
| `name`, `author`, `description` | Textos mostrados na lista de temas. |
| `mode` | `"dark"` ou `"light"` forçam esse modo; `"system"` segue a opção de modo escuro do app. Com um tema ativo, o preto puro fica desligado. |
| `albumColors` | `true` faz o app continuar tingido com as cores do álbum tocando; `false` usa só as cores do tema. |
| `colors` | Cores no formato `#RRGGBB` ou `#AARRGGBB` (veja abaixo). |
| `shapes.style` | `"rounded"` (cantos arredondados) ou `"cut"` (cantos cortados na diagonal). |
| `shapes.scale` | Multiplica o tamanho dos cantos (`1` é o padrão do Material, `0` deixa reto). |
| `typography.font` | `"system"`, `"google_sans"`, `"sans_flex"`, `"outfit"`, `"plus_jakarta_sans"`, `"condensed"`, `"serif"`, `"monospace"` ou o nome de uma família de fontes instalada no aparelho. |
| `typography.titleWeight` | Peso dos títulos, de `100` a `900`. |
| `typography.italicTitles` | `true` deixa os títulos em itálico. |
| `player.artworkCorner` | Arredondamento da capa no player, em dp. |
| `player.background` | Duas ou três cores do degradê de fundo do player, de cima para baixo. |
| `player.playButton` | `"diamond"` deixa o botão de play em losango, com um bloco colorido atrás. |
| `player.artworkTilt` | Inclina a capa, em graus (por exemplo `-4`). |
| `player.artworkFrame` | Moldura em volta da capa: uma cor (`"#FFFFFF"`) ou um papel (`"white"`, `"primary"`…). |
| `player.artworkBackdrop` | Bloco sólido deslocado atrás da capa, como uma sombra impressa: cor ou papel. |
| `player.titleShadow` | Sombra dura atrás do nome da música: cor ou papel. |

### Cores

`seed` gera a paleta inteira. Qualquer uma destas chaves substitui a cor gerada (são os papéis de cor do Material 3):

`primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer`, `inversePrimary`, `secondary`, `onSecondary`, `secondaryContainer`, `onSecondaryContainer`, `tertiary`, `onTertiary`, `tertiaryContainer`, `onTertiaryContainer`, `background`, `onBackground`, `surface`, `onSurface`, `surfaceVariant`, `onSurfaceVariant`, `surfaceTint`, `inverseSurface`, `inverseOnSurface`, `surfaceBright`, `surfaceDim`, `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`, `outline`, `outlineVariant`, `error`, `onError`, `errorContainer`, `onErrorContainer`, `scrim`.

As cores `on…` são as do texto e dos ícones em cima da cor correspondente. Mantenha bom contraste entre elas.

## Exemplo completo

Os temas que já vêm no app ficam em [`app/src/main/assets/themes`](app/src/main/assets/themes) e servem de exemplo:

| Tema | Estilo |
| :-- | :-- |
| **Tube Clássico** | Claro, vermelho e azul de link, cantos quase retos e player escuro de vídeo antigo |
| **Fita K7** | Walkman dos anos 80: alumínio, azul metálico e laranja |
| **Synthwave '84** | Neon rosa e ciano sobre roxo, títulos em itálico |
| **Café Lo-fi** | Tons de café, sálvia e terracota, títulos com serifa, capa de polaroide |
| **Aurora** | Azul-petróleo com verde-menta e lilás |
| **Midnight Hour** | Azul da meia-noite e ciano, cantos cortados, play em losango |

## Boas práticas

- Use só cores, fontes e formas. Logos, personagens e artes de jogos, filmes ou marcas têm dono e não devem ser distribuídos em temas públicos.
- Teste o tema no modo escuro e no claro, se ele usar `"mode": "system"`.
