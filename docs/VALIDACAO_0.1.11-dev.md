# Validação 0.1.11-dev

Pré-release de desenvolvimento; não é uma versão estável.

## Alterações

- Catálogo `messages.json` para chat, menus e BossBar, com placeholders, fallback para chaves omitidas e reload fora de evento ativo. Valores inválidos impedem a troca da configuração carregada.
- Menu com painéis cinza e três estrelas; avisos compactos e BossBar roxo/vermelho com HP menor ou igual a 20%.
- Carregamento de bosses por JSON e sorteio uniforme da pasta, com intervalo padrão de 7200 segundos. Consulte [personalização](PERSONALIZACAO.md).

## Evidência

- Build local e 73 testes JUnit sem falhas ou testes ignorados.
- Testes de mensagens: criação, preservação de personalização, fallback, substituições literais e repetidas, JSON inválido e valores de tipo incorreto.
- Testes de bosses: múltiplos arquivos, seleção apenas entre registrados, cobertura dos candidatos com semente fixa, IDs duplicados, lista vazia e valores inválidos.
- Verificação da estrutura, metadados, bytecode Java 21 e SHA-256 do JAR por `scripts/verify_jar.py`.
- O build do GitHub Actions associado à pré-release verifica Linux e Windows, auditoria Showdown e simulação de três sessões.

## Limites

Menu, mensagens personalizadas e animação da BossBar ainda precisam de conferência visual com cliente Minecraft real. Não há novo ensaio dedicado de integração nesta alteração nem aceite de capacidade para 100 pessoas. As limitações da [0.1.9-dev](VALIDACAO_0.1.9-dev.md) continuam aplicáveis.
