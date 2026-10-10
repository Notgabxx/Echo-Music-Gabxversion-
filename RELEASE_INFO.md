# Versões do Flux

As builds do Flux são geradas automaticamente pelo GitHub Actions a cada push na branch `main` e publicadas como **pre-release** em [Releases](https://github.com/Notgabxx/Echo-Music-Gabxversion-/releases), com o nome `flux-build-N`.

## Como instalar uma build

1. Abra a release mais recente no celular.
2. Baixe o arquivo `Flux-buildN.apk`.
3. Abra o arquivo e permita a instalação quando o Android pedir.

Enquanto o projeto não usar uma chave de assinatura fixa, o Android pode recusar a instalação por cima da versão anterior. Nesse caso, desinstale o Flux e instale a nova build.

## Notas das versões

As mudanças de cada build estão descritas nos commits e na própria página da release. Para pull requests, use títulos no padrão [Conventional Commits](https://www.conventionalcommits.org/pt-br/), por exemplo:

```
feat(player): botão de play que reage à música
fix(home): ícones do banner sobrepostos
```
