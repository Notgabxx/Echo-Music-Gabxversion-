# Política de segurança

## Versões com suporte

Só a build mais recente publicada em [Releases](https://github.com/Notgabxx/Echo-Music-Gabxversion-/releases) recebe correções. Se você encontrar um problema, confirme primeiro que ele acontece nessa versão.

## Como relatar uma vulnerabilidade

1. **Não abra uma issue pública** para falhas de segurança.
2. Use o botão **Report a vulnerability** na aba [Security](https://github.com/Notgabxx/Echo-Music-Gabxversion-/security) do repositório. O relato fica privado entre você e os mantenedores.
3. Conte o que acontece, como reproduzir, qual o impacto e, se tiver, uma sugestão de correção.

Você vai receber uma resposta assim que possível. Quando a correção for publicada, o crédito pelo relato pode ser dado a você, se quiser.

## Boas práticas para quem contribui

- Nunca faça commit de chaves, tokens, senhas ou arquivos de assinatura.
- Guarde configurações sensíveis em variáveis de ambiente ou em arquivos ignorados pelo Git.
- Mantenha as dependências atualizadas.
- Todo código passa por revisão antes de entrar na `main`.

Arquivos que nunca devem ir para o repositório:

- `local.properties` e `gradle.properties` com dados pessoais
- `*.keystore` / `*.jks` (chaves de assinatura)
- `secrets.properties` e qualquer arquivo com chaves de API
- `app/google-services.json`
- `**/assets/po_token.html`

## Boas práticas para quem usa

- Baixe o APK só pela página oficial de [Releases](https://github.com/Notgabxx/Echo-Music-Gabxversion-/releases).
- Instale as atualizações assim que saírem.
- Confira as permissões que o app pede e revogue o que não usar.
