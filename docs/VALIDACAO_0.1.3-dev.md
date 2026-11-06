# Validação 0.1.3-dev — prioridade: R e movimentos normais

## Comportamento implementado

O jogador aponta para o boss e usa o desafio padrão da tecla R. O servidor intercepta `ChallengeHandler.handle(BattleChallengePacket, ...)` apenas para essa entidade, valida arena/permissões/equipe/linha de visão e cria uma batalha normal singles com a equipe real do jogador, sem cloná-la ou curá-la. Não existe comando para disparar dano, movimento automático por cooldown ou UI própria.

Cada sessão tem um Pokémon boss próprio. A entidade pública é usada como referência de posição pelo ator adversário, mas não como `BattlePokemon.effectedPokemon`. Os Mixins de InitializeInstruction e SwitchInstruction limitam-se ao ator customizado: inicializam o slot antes do pacote real da UI e impedem o envio de outro boss ao mundo. A referência física permite o envio normal do próximo Pokémon ao trocar a equipe. Não se limpa nem regrava `battleId` da entidade pública para abrir outra sessão.

No serviço Graal padrão, a função `damage` da instância boss é adaptada depois das verificações normais de precisão, tipo e habilidades. Ela reserva o dano efetivo no coordenador e retorna esse valor para o processamento nativo de drain/recoil. O motor mantém PP, escolhas, trocas, clima e status da sessão. O HP local é uma projeção do pool virtual, mantida acima de zero até a parada central; o dano bruto não recebe multiplicador arbitrário.

O fim da raid impede novas admissões e encerra suas sessões com forcetie, sem vitórias/capturas locais. Antes de parar, o adaptador lê os PP reais do motor e os aplica à equipe, evitando depender de uma instrução enfileirada depois da remoção da batalha. Não há escrita de estado da raid em disco por golpe.

## Evidências locais

- JDK 21, build e 25 testes JUnit passaram; zero falhas/erros/skips nos resultados atuais.
- Servidor Fabric 0.17.2 / Minecraft 1.21.1 / Cobblemon 1.8.3 iniciou após liberar `meta.fabricmc.net`.
- Integração de teleporte passou em `/workspace/shared/raids-teleport-test.0vVO2Yvc`.
- Três sessões Java/Graal com atores controlados passaram em `/workspace/shared/raids-combat-test.jodR6SIH`.
- O fluxo do handler padrão passou com FakePlayers em `/workspace/shared/raids-challenge-test.RwkOmELa`: batalha real, dados do boss para a UI, escolha manual, troca, PP do movimento escolhido, entrada tardia, três contribuições somando 2.000 HP e término conjunto. Nenhum cliente real participou.
- O teste anterior de PP foi corrigido: a fixture consultava o primeiro golpe da lista, enquanto selecionava Dragon Claw. A verificação agora consulta o movimento efetivamente usado, incluindo Psychic após a troca para Mewtwo.
- A troca detectou uma dependência real do send-out nativo: um ator adversário sem entidade não fornece a referência procurada por `Pokemon.sendOutWithAnimation`. A solução de ator-âncora e Mixins foi exercitada no servidor, sem introduzir clones físicos.
- Dez testes no simulador oficial passaram lendo o mesmo `shirokai/raid-battle.js` embarcado: ausência de dano sem escolha, PP, ausência de KO local, drain e recoil com overkill efetivo, imunidade, miss, multihit, bloqueio de faint direto e política de cura do boss. O callback Java é mockado nesses dez testes; o servidor Graal é validado separadamente.

O ensaio em `/workspace/shared/raids-challenge-test.lFPKHxNx` também passou: capturou o pacote de inicialização quando o Cobblemon o constrói, verificou a espera por escolha manual e abriu uma batalha selvagem comum depois da raid. A execução `/workspace/shared/raids-challenge-test.pi94xD51` também passou pelo callback real de desconexão: encerrou apenas a própria sessão, preservou contribuição e PP e deixou as demais concluírem a raid. A conexão TCP não foi derrubada; o método de saída foi invocado pela fixture.

## Reprodução

```sh
bash gradlew --no-daemon build
python3 scripts/run_showdown_audit.py
python3 scripts/run_traditional_probe.py
python3 scripts/verify_jar.py --version 0.1.3-dev
```

Com servidor descartável preparado e EULA autorizada:

```sh
bash scripts/run-teleport-integration.sh
bash scripts/run-combat-integration.sh
bash scripts/run-challenge-integration.sh
```

Os scripts não modificam mundos existentes. A fixture de desafio inclui três FakePlayers, equipe real no storage do Cobblemon, envio de Pokémon, chamadas do handler real e respostas normais de movimento/troca. Não executa um cliente Minecraft nem pressiona fisicamente uma tecla. O teste valida o lado servidor do fluxo R; a interface, as animações e os controles precisam de teste visual/ponta a ponta com clientes reais.

## Limitações e trabalho restante

- É uma build de desenvolvimento, não um produto concluído nem aceite de 100 jogadores. Não houve ensaio de 10, 25, 50 ou 100 batalhas no servidor, nem métricas comparativas de TPS/MSPT/CPU/memória/latência nessas escalas.
- Exige Graal padrão; serviço SocketShowdown/customizações de outros mods não foram validados.
- O boss não cura, não perde HP global por seu próprio recoil/confusão e não finaliza uma sessão por faint direto. Perish Song e sacrifícios do próprio boss são restringidos. Cura de jogadores, drain e recoil de golpes de jogadores seguem o motor normal. Outras interações de HP/status/habilidades ainda precisam de cobertura; não presumir paridade integral com todo movimento possível.
- A animação física de cada golpe do boss e a apresentação visual das várias sessões sobre uma entidade pública não foram verificadas em cliente. Não requer mod cliente adicional.
- Captura, remoções externas, reinício, persistência, LuckPerms e SkiesClear com mods reais ainda precisam de ensaios completos. A tag solicitada é aplicada, mas a blacklist do SkiesClear continua sendo configuração administrativa.
- Lobby configurável, tickets/política de recuperação, sorteio em duas etapas, entrega automática e menu ainda pertencem às etapas seguintes. O sistema antigo de recompensas foi preservado, sem Raid Pouch.
- `maxConcurrentPlayers` não altera o `max-players` nem a capacidade de hardware do servidor.
- API GitHub permaneceu bloqueada; nenhum run de Actions com resultado confirmado foi inventado. A branch é enviada pelo proxy Git autorizado; publicação de Release estável permanece bloqueada pelos critérios de aceite.

## Arquivos principais alterados

- `ShirokaiRaids.java`, `TraditionalRaidBattles.java`, `RaidConfig.java`, `RaidCombatCoordinator.java`.
- Mixins de desafio, inicialização e switch; metadados Fabric; `shirokai/raid-battle.js`.
- Fixtures Java/Graal, desafio padrão e captura de pacote; scripts de integração e auditoria do adaptador.
- Teste de configuração do limite, verificador de JAR, versão dev, README, auditoria e histórico.
