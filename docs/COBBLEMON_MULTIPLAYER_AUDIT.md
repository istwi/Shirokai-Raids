# Auditoria de batalhas tradicionais multiplayer — Cobblemon 1.8.3

## Resultado e decisão antes de alterar o combate

A alternativa A não atende ao requisito de três jogadores cooperando contra um boss com entrada contínua. A alternativa B é uma direção de pesquisa plausível, mas não está tecnicamente validada: a API pública identificada não fornece, em conjunto, redirecionamento do desafio padrão, representação de um boss único em várias sessões e alteração autoritativa de HP no simulador antes de faint/win.

Não há prova de impossibilidade geral de implementar B com extensões adicionais. Há impedimento concreto para implementá-la apenas trocando chamadas da API existente. Não substituir o combate legado por uma integração que apenas compila, lê HP por tick ou envia pacotes visuais. Nenhuma experiência multiplayer tradicional está aprovada nesta entrega. O teleporte pode ser implementado independentemente desse impedimento.

Esta conclusão antecede qualquer alteração no combate. O código de combate legado será preservado enquanto não existir um substituto validado; a documentação deve identificá-lo como legado, não como comportamento definitivo solicitado.

## Origem e reprodução

Fontes oficiais: https://gitlab.com/cable-mc/cobblemon, tag 1.8.3, commit `ffe005511dd0d3ba68377fa3f95906b3837d9a0e`. A auditoria anterior em `COBBLEMON_API_AUDIT.md` permanece válida como registro histórico.

Também foi executado o simulador real contido em `common/src/main/resources/data/cobblemon/showdown.zip` desse commit. SHA-256 do ZIP: `aad1393c313359bcb8ca9482baede9009c55256730ef1fd1905c147d8464385e`.

Reprodução:

```sh
python3 scripts/run_showdown_audit.py
```

O script lê o JAR oficial do cache Gradle ou baixa a coordenada exata do Maven oficial, verifica o SHA-256 do JAR e do ZIP embarcado, extrai em diretório temporário e executa oito verificações com Node. O ZIP é idêntico ao da tag auditada. Com uma cópia local, use `--archive /caminho/showdown.zip`. Não é necessário npm nem pacotes de terceiros adicionais.

Os testes exercitam o simulador embarcado, não os clientes Minecraft nem toda a integração Java/Graal. Não constituem validação multiplayer com três clientes.

## 1. Criação e controles padrão

Caminhos Kotlin relativos a `common/src/main/kotlin/com/cobblemon/mod/common/`.

- `net/serverhandling/ChallengeHandler.kt:37–55`: o pacote de desafio valida o alvo, a distância e o Pokémon selecionado da equipe; então chama `targetedEntity.canBattle(player)` e `BattleBuilder.pve(...)`.
- `client/keybind/keybinds/PartySendBinding.kt:131`: o cliente também consulta canBattle antes de enviar o desafio. Corrigir apenas o servidor não necessariamente libera a tecla R no cliente.
- `entity/pokemon/PokemonEntity.kt:1398–1417`: canBattle recusa UNBATTLEABLE, isBusy, battleId já atribuído, entidade com dono, morte e equipe ocupada.
- `battles/BattleBuilder.kt:243–322`: pve cria PlayerBattleActor e PokemonBattleActor. Recusa jogador já em batalha e alvo com battleId. cloneParties clona a equipe do jogador; o wildActor continua sendo construído com o Pokémon do alvo real. Portanto, cloneParties não cria automaticamente um boss separado para cada sessão.
- `battles/BattleRegistry.kt:145–202`: startShowdown atribui IDs p1/p3 para um lado, p2/p4 para o outro, cria slots ativos e escreve o battleId da sessão nas entidades envolvidas. O simulador recebe formato, equipes e participantes no início.

Consequência: retirar UNBATTLEABLE da raid atual só permitiria a primeira batalha tradicional. Não implementaria uma raid compartilhada. Limpar/regravar battleId para permitir outra sessão é incorreto e não será feito.

## 2. Alternativa A: uma batalha compartilhada

- `battles/BattleTypes.kt:21–27`: singles = um ator/um slot por lado; doubles = um ator/dois slots; triples = um ator/três slots. Três slots não são três jogadores.
- MULTI = dois atores por lado, um slot por ator. `BattleBuilder.pvp2v2` monta exatamente dois jogadores em cada lado; TeamManager limita a composição usada pelo builder.
- `BattleSide` recebe um array de atores no construtor; não há operação pública auditada de entrada tardia que reconfigure simulador, slots, equipes e clientes.
- No simulador embarcado, `sim/dex-formats.js:295` define quatro participantes para multi/freeforall e dois para os outros formatos. `sim/battle.js:109` cria esse número de vagas. MULTI liga p1 a p3 e p2 a p4.
- `sim/battle.js:2795–2827`: setPlayer inicia quando todas as vagas estão preenchidas. Uma vaga com equipe existente rejeita uma nova equipe; renomear um participante não é adicionar outro jogador.
- `sim/battle-stream.js:134–143`: ações reconhecidas são p1 a p4. Criar um BattleType Kotlin chamado multi com três atores por lado não cria suporte a p5/p6 no protocolo.
- Free-for-all não é cooperação três contra um: possui semântica de adversários individuais. Não é uma substituição aceitável sem adaptação e testes específicos.

**Verificado em execução:** MULTI com p1/p2/p3 ainda não iniciou; ao receber p4, iniciou com alianças p1+p3 e p2+p4. Uma equipe nova em p1 ocupado é recusada. Uma ação p5 é recusada pelo protocolo.

**Conclusão A:** os formatos prontos não atendem à composição três-contra-um com participantes entrando após o início. A capacidade de criar objetos BattleSide arbitrários não demonstra suporte a esse requisito.

## 3. Alternativa B: sessões tradicionais sincronizadas

### Identidade e representação

Cada sessão precisaria de BattlePokemon/Pokemon próprios, sem reutilizar o Pokémon original em várias batalhas.

`battles/pokemon/BattlePokemon.kt:51–63` fornece safeCopyOf, marca clone de batalha e uncatchable. Isso ajuda no isolamento, mas não resolve a representação visual de uma única entidade no mundo.

`BattlePokemon.entity` deriva de effectedPokemon.entity. PokemonBattleActor usa essa entidade para posição, mundo, fuga e limpeza. `SwitchInstruction` envia Pokémon não presentes ao mundo com o battleId da sessão. Compartilhar a entidade física entre esses estados causa competição pelo battleId e pela limpeza da entidade. Clones sem entidade não foram validados como substituto visual do boss único. Múltiplas entidades visíveis contradizem o requisito.

Para a tecla R, seria necessário um redirecionamento dedicado do caminho de desafio, mantendo o alvo global desocupado e criando uma sessão real. Não foi identificado evento público cancelável específico antes de ChallengeHandler criar o PvE. O evento BATTLE_STARTED_PRE ocorre depois que os atores já foram construídos; cancelar e reconstruir não resolve os filtros anteriores nem a representação visual.

### Dano autoritativo e mensagens duplicadas

`battles/ShowdownInterpreter.kt:33–41` mantém parsers privados. O parser split de -damage cria DamageInstruction; a mensagem privada fornece HP exato e a pública fornece a visão do oponente.

`battles/interpreter/instructions/DamageInstruction.kt:151–241` interpreta o HP resultante e atualiza o objeto Pokémon e os pacotes. O dano já foi resolvido no Showdown. Não foi encontrado evento público de dano Showdown em CobblemonEvents que ofereça interceptação autoritativa anterior ao cálculo/KO. ALLOW_DAMAGE do Fabric é dano de entidade Minecraft, não dano Showdown.

**Verificado em execução:** um ataque válido reduziu o HP no simulador e consumiu PP. O log contém mensagem privada e pública para o mesmo dano. Somar ambas duplicaria contribuição. Eventos idênticos em golpes diferentes também não podem ser deduplicados por hash do texto.

Um adaptador futuro deve consumir uma única instrução privada com identidade de sessão, turno, sequência e alvo; atribuir dano pelo contexto real do movimento/efeito e limitar o crédito ao HP global restante. Leitura de HP por tick perde multihits, cura intercalada, fontes de dano e ordem de ações. Miss, falha e ação cancelada não podem gerar crédito fictício.

### Sincronização real de HP e morte local

`runner/ShowdownService.kt` expõe startBattle, endBattle, send e APIs de registro. PokemonBattle.writeShowdownAction encaminha mensagens. Alterar Pokemon.currentHealth ou enviar BattleHealthChangePacket não altera por si o objeto Pokémon já vivo dentro do simulador.

**Verificado em execução:** duas sessões com o mesmo UUID de boss mantêm HP independentes. Dano em uma não muda a outra. O protocolo não reconhece um comando tipado sethp.

O comando >eval existe em battle-stream, portanto não seria correto afirmar que não há qualquer forma de alterar o engine. Ele permite executar JavaScript sobre estruturas internas; não é uma API de raid nem uma garantia de consistência com o estado Java. Injetar código para alterar hp, maxhp, fila de faint, ações e vitória exigiria implementar e validar um adaptador de engine. Isso não foi feito nem apresentado como solução pronta.

**Verificado em execução:** ao receber dano letal, a sessão gera faint/win e ended=true. Restaurar o HP Java depois disso não desfaz a vitória que o simulador já resolveu. Uma integração precisa resolver essa regra antes do KO local, ou estabelecer um protocolo explícito de rodadas que seja aprovado como comportamento diferente. Ganhar rodadas locais não pode pagar o prêmio global várias vezes.

Usar milhões de HP só no campo currentHealth de uma sessão normal também não é uma solução auditada: maxHealth, proporções, cura, recoil, drain, status e a interface precisam concordar entre Java e Showdown. Não basta escolher um multiplicador arbitrário para converter dano.

### PP, status, habilidades e trocas

O formato de equipe em BattleRegistry.packTeam inclui movimentos, PP atual/máximo, habilidade, status, atributos, nível e UUID. O simulador e as instruções request/switch/pp_update/status mantêm as regras tradicionais por sessão.

Política candidata para B: PP, clima, boosts, status e movimentos do boss seriam locais à sessão; somente HP global e contribuição seriam compartilhados. Essa política evita que uma sessão regrave estados intermediários das demais, mas ainda exige definir cura, dano residual, OHKO, recoil, drain, troca e morte local. Não se pode declarar esses casos compatíveis sem testes. As equipes dos jogadores não devem ser clonadas e curadas inadvertidamente, pois isso descartaria PP e consequências normais.

## 4. Fim de batalha, saída e recompensas

- `PokemonBattle.stop():526` encaminha >forcetie e chama end().
- end() processa experiência, EVs, operações pós-batalha, cura de atores selvagens, recall, BATTLE_ENDED, BattleEndPacket e closeBattle. Logo, encerramento não é apenas remover um ID de um mapa.
- `PokemonBattleActor.sendUpdate` limpa entity.battleId ao receber BattleEndPacket. Isso reforça por que uma entidade não pode ser usada em várias sessões comuns.
- `FaintInstruction` zera currentHealth, emite BattleFaintPacket/BATTLE_FAINTED e limpa o slot ativo.
- `BattleRegistry.onPlayerDisconnect` interrompe a batalha do jogador; em uma batalha única compartilhada, desconexão pode atingir toda a sessão. Em B, é necessário garantir isolamento.

Uma integração futura deve fechar admissão/ataques antes de qualquer pagamento global, marcar a transição única de derrota, encerrar apenas sessões vinculadas à raid, preservar a caixa postal e usar o registro completed. Fim de sessão local, fuga e timeout não devem virar vitórias globais. Os riscos de reserva de inventário ambígua descritos na arquitetura anterior continuam existentes e não devem ser escondidos por esta migração.

## 5. Resultado dos testes e próximos pré-requisitos

Oito verificações no simulador oficial passaram. O build Java e testes do projeto serão executados separadamente. Não houve teste com três clientes, da tecla R, de tela de movimentos, troca de Pokémon ou entrega de recompensas neste modelo.

Para avançar B com segurança, faltam um protótipo executável de adaptação autoritativa do simulador e uma representação de sessão que preserve um boss visível único sem compartilhar indevidamente entidade/Pokemon. Esse protótipo deverá provar cancelamento de KO local, atribuição de residual/multihits, sincronização Java/Showdown/clientes, isolamento de status, entrada tardia e encerramento sem XP/loot/pagamento duplicado. Somente depois disso o legado poderá ser substituído.

### Endpoint raw GitLab bloqueado pelo servidor remoto

URL: https://gitlab.com/cable-mc/cobblemon/-/raw/ffe005511dd0d3ba68377fa3f95906b3837d9a0e/common/src/main/resources/data/cobblemon/showdown.zip

O proxy aceitou CONNECT com HTTP 200; o destino respondeu HTTP/2 403 com `cf-mitigated: challenge`. É um desafio Cloudflare do servidor remoto, não falta de domínio na política do ambiente nem erro de coordenada Maven. Não foi contornado o desafio. O mesmo ZIP foi obtido do JAR publicado em https://artefacts.cobblemon.com/releases/com/cobblemon/fabric/1.8.3+1.21.1/fabric-1.8.3+1.21.1.jar, com JAR e ZIP verificados por SHA-256. Nenhum domínio extra é necessário para esse caminho.
