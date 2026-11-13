# Validação local — 0.1.18-dev

- Build Java 21 aprovado; 120 testes JUnit sem falhas, erros ou casos ignorados.
- Nove testes de probabilidades: sorteios gerais independentes, TOP 3 com posição/peso/chance, filtros de contribuição e rank, chance zero ocupando peso, agregação por tipo/raridade, categoria desativada, legado por sorteio, overrides, quantidades e prévia sem mutação.
- Menu administrativo nativo: 206 verificações aprovadas. Incluem 175 combinações de clique/tipo/botão em ícones, inventário e slot externo; navegação, aliases, boss por ID, categoria, rank, contribuição, paginação de 60 prêmios e atualização das configurações já carregadas.
- Estado persistente de recompensas foi comparado antes/depois da consulta; a prévia não alterou o arquivo. Itens/cursor mantiveram as quantidades originais, sem emissão de ícones.
- Raid ativa preservou suas probabilidades após rewardreload; o perfil para próximas raids refletiu o valor novo. Revogar OP invalidou/fechou o menu e jogador sem permissão não abriu a consulta.
- Regressão coletiva aprovada: três sessões, golpes, PP, troca, contribuição, entregas parcial/offline/login; seis cenários de recuperação de recompensas antigas, 105 verificações de menu e 105 de operações pendentes.
- JAR validado com `scripts/verify_jar.py --version 0.1.18-dev`.

Resultados preservados em `/workspace/shared/raids-admin-test.WIOziByE` (menu isolado final) e `/workspace/shared/raids-challenge-test.daXeDtP9` (combate/menus/operações).

Execução em servidores Minecraft reais com FakePlayers, sem clientes humanos. Os testes verificam conteúdo e comportamento dos menus no servidor, não a aparência em uma instalação de cliente. Não foi medida capacidade para 100 clientes.

Teste focado: `bash scripts/run-admin-menu-integration.sh`, em mundo descartável e com a EULA de testes já aceita nesta sessão.
