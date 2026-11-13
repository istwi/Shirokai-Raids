# Validação local — 0.1.14-dev

Build aprovado com 81 testes JUnit, sem falhas. Minecraft 1.21.1 e Cobblemon 1.8.3.

- Captura dedicada: 50 verificações aprovadas, incluindo início manual por R, cópias privadas sobrepostas, brilho/nome, obstrução por parede, BossBar individual, movimento/PP nativos, animação de falha, mensagens de sucesso/bolas esgotadas/prazo, cancelamento durante animação e ausência de captura tardia.
- Teleporte: ponto de entrada separado do boss, persistência, coordenadas/direção exatas, entrada compartilhada por jogadores, recusa de local inseguro, retorno à origem e ausência de `/raid lobby`.
- Combate coletivo: três sessões controladas, golpes, PP, troca, contribuição, configuração por boss e rejeição de reload inválido. Recompensas: seis cenários de recuperação, entrega parcial/offline/login e prevenção de duplicação.
- Menu: 105 verificações de cliques; operações: outras 105 verificações de cliques pendentes, permissões e comandos.
- Auditorias do motor Showdown e coordenador Java aprovadas.

Resultados preservados nos diretórios locais:

- `/workspace/shared/raids-capture-test.BjJRRBgH`
- `/workspace/shared/raids-teleport-test.cf8MLwiV`
- `/workspace/shared/raids-challenge-test.4SCxCIhU`

Os testes de servidor usam FakePlayers, sem clientes Minecraft reais. A aparência da animação, brilho e BossBar ainda requer conferência visual no cliente. Não foi medida capacidade para 100 jogadores.
