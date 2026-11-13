# Validação local — 0.1.17-dev

- 111 testes JUnit aprovados, sem falhas/erros/casos ignorados. Seleção final de duas ou três etapas, exclusão de intermediários/fracos/pequenos/não implementados, classificação por força, 1.000 sorteios com 50 especiais exatamente nas posições múltiplas de 20, histórico limitado, shiny, grupos e migração de estado.
- Catálogo nativo Cobblemon 1.8.3: 212 candidatos elegíveis, sendo 193 comuns e 19 especiais. Dificuldades: 133 Normal, 47 Raro, 13 Épico, 13 Lendário e 6 Supremo.
- Integração dedicada: 338 verificações passaram. Vinte bosses reais gerados sem arquivos individuais; só a 20ª posição foi especial. Verificados golpes legais, nível, IVs/EVs, vida global calibrada, persistência, recarga, recusa sem arena e recusa de segunda raid simultânea sem consumir contador. Um boss sorteado executou ação em batalha nativa, consumindo PP; clone de combate manteve os IVs e golpes configurados.
- Segundo JVM no mesmo mundo: sete verificações passaram. O contador permaneceu em 20; a 21ª raid foi comum e avançou para 21. Evento manual iniciado por ID não consumiu posição.
- Regressão de combate coletivo: três sessões controladas, movimentos, PP, troca, contribuição, entregas parcial/offline/login; seis cenários de recuperação de recompensas, 105 verificações de cliques e 105 de operações pendentes.
- Regressão de captura: 51 verificações passaram, incluindo R manual, cópias privadas ciano, BossBar, animação, resultado no chat, bolas, prazo e retorno.

Resultados preservados:

- `/workspace/shared/raids-catalog-test.jrw6W5vv`
- `/workspace/shared/raids-challenge-test.b8kJW8Tb`
- `/workspace/shared/raids-capture-test.0OvnGyhQ`

Os testes usam servidor real com FakePlayers, sem clientes humanos. Não houve inspeção visual de todos os modelos, simulação de todas as equipes ou medição para 100 clientes. O balanceamento inicial estima dez participantes e é ajustável. O teste de persistência fez encerramento e reinício reais do JVM, sem corte de energia aleatório.

O CSV em examples/catalogo-validado-0.1.17.csv é relatório da seleção observada; não é configuração nem pré-requisito do sorteio.

Verificação adicional de Speed: IV fixo em 15 nos vinte bosses sorteados, no clone de combate nativo e em evento manual após reinício. Os outros IVs continuam por perfil.
