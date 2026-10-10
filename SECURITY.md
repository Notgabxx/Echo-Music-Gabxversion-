# Política de segurança

## Versões com suporte

Só a build mais recente publicada em [Releases](https://github.com/Notgabxx/Echo-Music-Gabxversion-/releases) recebe correções. Se você encontrar um problema, confirme primeiro que ele acontece nessa versão.

## Como relatar uma vulnerabilidade

1. **Não publique os detalhes da falha em uma issue pública.**
2. Se a aba [Security](https://github.com/Notgabxx/Echo-Music-Gabxversion-/security) mostrar o botão **Report a vulnerability**, use-o: o relato fica privado entre você e os mantenedores.
3. Se o botão não aparecer, abra uma [issue](https://github.com/Notgabxx/Echo-Music-Gabxversion-/issues) só pedindo um contato privado, sem descrever a falha.
4. No relato, conte o que acontece, como reproduzir, qual o impacto e, se tiver, uma sugestão de correção.

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
