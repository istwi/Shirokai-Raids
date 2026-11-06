# Auditoria de batalhas tradicionais multiplayer — Cobblemon 1.8.3

## Revisão: Raid Dens 0.13.4 e decisão de arquitetura

O escopo vigente está em [ESCOPO_WORLD_BOSS.md](ESCOPO_WORLD_BOSS.md). O mod final não deve registrar itens ou blocos do Raid Dens, nem depender do seu JAR. As conclusões históricas abaixo dizem respeito à API pública; o Raid Dens demonstra uma extensão do simulador e Mixins, não uma simples chamada adicional do builder.

### Proveniência e compatibilidade

- Shirokai Raids preservado a partir de `c5251dce31abe8e13f9c3b1318fbcde7bd953cf6`, branch de trabalho `feature/world-boss-traditional-battles`.
- Referência de fonte: [Raid Dens, commit 59eddf929451cd86256ad0a3a880f07f10fb1661](https://github.com/necro50n3/cobblemon-raiddens/tree/59eddf929451cd86256ad0a3a880f07f10fb1661), que declara versão `0.13.4+1.21.1` e compila contra Cobblemon `1.8.2+1.21.1`.
- JAR fornecido: `cobblemonraiddens-fabric-0.13.4+1.21.1.jar`, SHA256 `8caa1912a0d51dff2555855e054bec6606a3b148232643c0ba6c1c6ae5a35dff`. Declara Cobblemon `>=1.8.0`; esse metadado não comprova compatibilidade binária com 1.8.3.
- O artefato oficial Modrinth, versão `1Qqxt55J`, tem SHA256 `69345ba1d0b934bc181c01393280f8eadce437e4cb9de29b75f90a498393a070` (SHA512 conferido com a API oficial). Comparação ZIP mostrou exatamente sete tabelas `loot_table/raid/tier/tier_*.json` diferentes no anexo; nenhuma classe ou outro recurso diverge. O anexo não é byte a byte o release oficial; as tabelas customizadas não serão usadas como padrão.
- O recurso `raidboss.js` tem SHA256 `db3c39c8e754d2e4ca10a56d27033a9b04a708663b8dffc6060b761b1bad7e94`. Ele, `showdown/index.js` e `showdown/conditions.js` correspondem à fonte nesse commit depois de normalizar finais de linha. Os bytes diferem; não foi estabelecida correspondência de todas as classes compiladas nem um commit único de build do JAR.
- O HEAD disponível `958360b25f10620e4705ecba7fb6ed83d582f6d3` já está em 0.13.5/Cobblemon 1.8.4 e registra remoção temporária de sincronização/scripts. Não serve como prova da implementação 0.13.4.

### Licenças e reutilização

A raiz do commit 0.13.4 tem MIT, copyright `(c) 2026 necro50n3`. A licença permite incorporação de partes, preservando o aviso e o texto integral. O `fabric.mod.json` do JAR declara MIT, mas o arquivo de licença não foi encontrado no ZIP. Isso não transforma arquivos de terceiros em MIT.

`assets/cobblemonraiddens/showdown/index.js` contém copyright de Cobblemon Contributors e aviso MPL 2.0. Substituir arquivos inteiros do simulador implica também preservar suas licenças e disponibilizar as modificações desses arquivos conforme aplicável. O loader se descreve como implementação do loader do Mega Showdown; sua origem precisaria ser rastreada antes de incorporar esse arquivo. A decisão é implementar o adaptador mínimo no Shirokai, sem copiar o loader, a árvore do Showdown, assets, itens ou código de origem não comprovada. Os scripts de auditoria leem o JAR externo por caminho; não o embutem no mod.

Não há necessidade técnica de portar todo o Raid Dens para aproveitar a ideia do hook pré-KO. Qualquer cópia futura deverá incluir os avisos por arquivo e a licença correspondente no artefato; a licença principal do projeto não deve apagar esses direitos.

### Evidências por requisito

Caminhos abaixo relativos a `common/src/main/java/com/necro/raid/dens/common/`, salvo indicação. Consultar o commit fixado acima, não o HEAD.

| Área | Evidência | Consequência para Shirokai |
|---|---|---|
| Desafio normal | `mixins/client/PartySendBindingMixin.java` intercepta `processEntityTarget`, enviando `RaidChallengePacket`; `mixins/raid/PokemonEntityMixin.java` altera `canBattle` e `isBattling` | O original inclui extensão cliente. Não comprova funcionamento com cliente Cobblemon sem Shirokai instalado |
| Criação de batalha | `raids/RaidBuilder.java` usa `PlayerBattleActor`, `PokemonBattleActor`, singles e `BattleRegistry.startBattle`; equipe é obtida por `toBattleTeam(false, false, leadingPokemon)` | UI e simulador reais são reutilizados; não são ataques por cooldown |
| Sessões simultâneas | `RaidBuilder` cria novo ator, mas constrói `BattlePokemon` com o MESMO `pokemonEntity.getPokemon()` como original e efetivo | Não copiar esse compartilhamento; o usuário exige objetos independentes por sessão e uma entidade pública única |
| Admissão/limite | `network/packets/RaidChallengePacket.java` compara `raid.getBattles().size()` com `boss.getMaxPlayers()`, verifica equipe e distância | É limite de batalhas ativas, não prova de capacidade para 100. Revalidar admissão atomicamente no gerenciador central |
| Estado comum | `raids/RaidInstance.java` mantém `currentHealth`, `maxHealth`, `damageTracker`, lista de batalhas e estado | Centralizar HP/contribuição e manter estado local de cada sessão |
| Dano e KO | Recurso `data/cobblemonraiddens/cobblemonraiddens/showdown/conditions/raidboss.js`: `onDamage` emite split e `-raiddamage`, retorna zero | Intercepta antes da redução normal de HP, evitando KO por esse caminho. Não equivale a sincronizar o HP interno de todos os motores |
| Inicialização do hook | `showdown/events/StartRaidShowdownEvent.java` gera `>eval ...addVolatile('raidboss')` | A extensão depende de registro/ativação no motor. Não inserir JavaScript derivado de texto de jogadores |
| Interpretação | `showdown/RaidDensShowdownRegistry.java` registra parser split; `RaidDamageInstruction` lê a mensagem privada e chama `syncHealth` | Consumir um evento autoritativo por golpe; mensagem pública não é outro dano |
| Crédito efetivo | `RaidInstance.syncHealth` acumula dano solicitado/maxHealth ANTES de limitar o HP | Pode creditar overkill. Corrigir no gerenciador novo: crédito = min(dano, HP restante), nunca copiar literalmente |
| Sincronização | `sendHealthPacket` envia `BattleHealthChangePacket`; `RaidDamageInstruction` atualiza `effectedPokemon.currentHealth` | Pacote visual/objeto Java não prova atualização do HP interno das outras sessões Showdown |
| Cura | `RaidHealInstruction` passa delta negativo a `syncHealth`; hook `onTryHeal` retorna false e não emite evento se HP local cheio | Cura exige contrato explícito. O ramo de efeito Move pode emitir só uma linha depois de split; não presumir pareamento válido |
| Recoil/drain | Hook calcula efeitos usando o dano solicitado; também modifica movimentos como Perish Song, Explosion, Transform e Curse fantasma | Não assumir regras tradicionais completas. Overkill exige basear efeitos no dano efetivo e testar multihit/residual/cura |
| Saída/desconexão | Fabric `events/ModEvents.onPlayerDisconnect` chama `RaidJoinHelper`, que chama `RaidUtils.leaveRaid`; eventos BATTLE_FLED/BATTLE_ENDED removem participantes | O caminho original envolve regiões/teleporte e vidas. Substituir por encerramento isolado da sessão sem cancelar a raid de outros |
| Derrota | HP zero agenda `stopRaid` com 60 ticks; `handleSuccess` muda estado e emite recompensas; `closeRaid` descarta entidade/libera região | Fechar admissão imediatamente e fazer transição terminal única, antes de callbacks e pagamentos; não copiar janela de três segundos |
| Captura | `data/raid/RaidBoss.applyAspects` aplica `uncatchable`, mas sucesso gera Pokémon de recompensa | Proibir tanto captura física quanto qualquer entrega de Pokémon; não portar fluxo de captura pós-vitória |
| Recompensas | `handleSuccess`, `RewardDistributionContext`, `RaidPouchItem` e fila do helper | Implementar tabelas próprias e entrega direta; nada de pouch ou sorteio de direito de captura |
| Cristais/arenas | `worldgen`, `dimensions`, blocos, itens e registry são subsistemas separados | Não portar seus registros. Desativar por configuração é insuficiente para o requisito de ausência de itens |

### Comparação das alternativas

| Alternativa | Benefício | Custo/limitação | Decisão |
|---|---|---|---|
| Fork completo do Raid Dens | Fluxo de combate existente e integração cliente | Arrasta conteúdo, dimensões e dependências; usa compartilhamento de Pokémon e semântica de cura/dano diferente | Descartada como base principal |
| Incorporar componentes mínimos no Shirokai | Preserva histórico/base, independência e ausência de conteúdo original; licença MIT permite partes próprias do autor | Exige adaptar hook, atores/representação e contratos de encerramento; licenças por arquivo | Escolhida, com implementação própria do coordenador e adaptador mínimo a validar |
| Addon do Raid Dens | Menor implementação inicial | Exige o JAR e seus registros, acoplamento à versão e extensões cliente | Não atende ao objetivo de independência/ausência de itens |

### Restrição adicional: somente servidor e teto de 100

O usuário confirmou instalação somente no servidor e teto configurável de 100 combatentes reais, recusando a 101ª sessão até abrir vaga. O coordenador valida capacidade entre 1 e 100; o teste de admissão não comprova desempenho.

As fontes comuns oficiais `com.cobblemon:mod:1.8.3+1.21.1:sources` foram verificadas pelo SHA256 publicado `7d4eada6693557b668ba5cda3347559ce7cddbc6d25c3a2e55cf509818c5d5a2`. `ChallengeHandler.handle` recebe o pacote normal `BattleChallengePacket`; é candidato a interceptação restrita ao UUID do boss. Manter a entidade pública sem `battleId` e disponível em `canBattle` é pré-requisito para o cliente original enviar esse pacote; um Mixin exclusivamente servidor não muda a decisão já feita no cliente.

`PokemonBattleActor` é extensível, mas `BattlePokemon.getEntity` é final. `SwitchInstruction` tenta invocar um Pokémon destacado quando há ator EntityBacked sem entidade Pokémon; isso criaria clones visíveis. Um ator próprio sem entidade e slots pré-inicializados pode evitar esse caminho, porém `ActiveBattlePokemon.getSendOutPosition`, InitializeInstruction, animações, fuga e pacotes precisam de validação conjunta. É hipótese de implementação, não prova de suporte. Não adicionar pacote cliente próprio nem reutilizar o objeto do boss para contornar esses pontos.

### Contrato de arquitetura escolhido

Um coordenador central deve possuir raid UUID, estado (ativa/derrotada/cancelada), HP inteiro, versão monotônica e contribuição efetiva por jogador. A admissão cria um token único por sessão e limita combatentes simultâneos. Cada sessão tem `Pokemon`/`BattlePokemon` próprios e sequência monotônica de eventos; replay, sessão encerrada e evento de outra raid não alteram HP. Saídas preservam contribuição. Nenhum ataque escreve em disco. O coordenador retorna efeitos para a thread do servidor aplicar: snapshots, encerramento das sessões e uma transição terminal única.

O adaptador Showdown deve interceptar antes do KO e publicar dano efetivo, manter semântica coerente de cura/recoil/drain e identificar efeitos residuais. A entidade pública não pode ter o `battleId` sobrescrito por sessões distintas. É necessário implementar e verificar um ator/ponte de representação próprio e o redirecionamento do desafio. Não basta remover `UNBATTLEABLE`, reescrever `battleId`, clonar só a equipe do jogador ou atualizar BossBar/pacotes.

Decisão conservadora para o protótipo: clima, boosts e status ficam locais à sessão; HP e contribuição são globais. Mecânicas dependentes de fração de HP, dano residual, OHKO, recoil/drain e cura precisam de política explícita e testes no motor. Não usar uma escala arbitrária para converter dano. Um protótipo isolado não deve habilitar um modo de produção incompleto, nem pagar recompensas a cada vitória local.

A base existente ainda tem combate automático, bloqueia o boss com `UNBATTLEABLE` e cancela batalhas em BATTLE_STARTED_PRE. Ela NÃO atende ao novo combate; esses caminhos precisam ser substituídos conjuntamente depois de validar o adaptador.

### SkiesClear

Referência: [SkiesClear 1.1.2, commit 19a95b4e5c063487884d3237d282d085f452e698](https://github.com/PokeSkies/SkiesClear/tree/19a95b4e5c063487884d3237d282d085f452e698). `EntityClearable` e `CobblemonClearable` extraem entradas `#tag=` e comparam com `entity.tags`; `Clearable.shouldClear` verifica blacklist antes da whitelist. As duas categorias devem incluir `#tag=shirokai_raid_boss`. `clear_persistent`/`clear_named` são filtros adicionais, não substitutos dessa exclusão.

A base usa a tag diferente `shirokai_raids_boss`. A migração deve tratar bosses antigos controlados pelo próprio mod com segurança e aplicar a tag solicitada a novos bosses, sem excluir ou proteger outros lendários. A versão realmente instalada no servidor do usuário não foi fornecida; não há validação de limpeza manual/automática em runtime. Não modificar o SkiesClear.

### Validação e limites desta revisão

- Oito verificações existentes passaram no simulador oficial 1.8.3, extraído do JAR e validado pelo SHA256 do ZIP. Confirmam as limitações de formatos, sessões e KO descritas na auditoria histórica.
- `python3 scripts/run_raiddens_audit.py /caminho/cobblemonraiddens-fabric-0.13.4+1.21.1.jar` valida SHA256/versão e executa oito testes dos callbacks reais do recurso extraído. Usa mocks de contexto, NÃO o motor ou clientes. Confirma delta privado/público, retorno zero, golpes iguais distintos, base de recoil/drain e limitações de cura/movimentos.
- Seis testes adicionais com o hook original no simulador oficial passaram, incluindo PP, troca e imunidade. O protótipo Java + três sessões Showdown passou: entrada tardia, saída, replay, crédito efetivo de 2.000/2.062 e encerramento único. Consulte `VALIDACAO_0.1.2-dev.md` para reprodução e limites.
- Não houve teste real com três clientes, tecla R, interface, troca/PP ponta a ponta, SkiesClear instalado ou desempenho/TPS de 100 sessões. Nenhuma capacidade simultânea em Minecraft está comprovada.
- O domínio Maven oficial foi liberado e o artefato 1.8.3 baixado. A primeira compilação posterior falhou por falta de `javac` (só havia JRE). JDK 21 completo instalado com checksum pelo script existente; recompilação passou, com 24 testes JUnit sem falhas/erros/skips. O coordenador é base do protótipo e ainda não substitui o combate legado em produção.
- Nenhuma Release oficial foi publicada; nenhum link de Actions com JAR novo existe antes de push e execução confirmados.

## Auditoria histórica — API pública e simulador

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
