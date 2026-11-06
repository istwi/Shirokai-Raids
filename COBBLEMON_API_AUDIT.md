# Auditoria da API Cobblemon 1.8.3

## Origem e escopo

Inspeção estática das fontes oficiais em https://gitlab.com/cable-mc/cobblemon, tag `1.8.3`, commit `ffe005511dd0d3ba68377fa3f95906b3837d9a0e`. `gradle.properties` confirma mod_version=1.8.3 e mc_version=1.21.1. Cópia de referência fora do projeto: `/workspace/shared/cobblemon-1.8.3-audit`.

Esta etapa confirma declarações nas fontes. Compilação, resolução do artefato Maven, eventos Fabric e testes no servidor ainda estão pendentes por bloqueio de rede. Não constitui validação funcional.

## Métodos e estados confirmados

Caminhos abaixo relativos a `common/src/main/kotlin/com/cobblemon/mod/common/`.

| Área | Evidência nas fontes | Consequência para a integração |
| --- | --- | --- |
| Criação | `api/pokemon/PokemonProperties.kt:642`: `create(player: ServerPlayer? = null): Pokemon`; linha 675: `createEntity(world: Level, player: ServerPlayer? = null): PokemonEntity` | createEntity constrói a entidade e aplica propriedades; a inserção no mundo é etapa separada. |
| Propriedades | PokemonProperties possui espécie, forma, nível, shiny, nickname e scaleModifier; create aplica propriedades, inicializa e executa roll | Validar IDs e valores antes de criar. Fixar shiny explicitamente para não depender de sorteio. |
| Atributos | `pokemon/Pokemon.kt:590–601`: attack, defence, specialAttack, specialDefence e scaleModifier; linha 784: getStat(stat: Stat) | Usar atributos calculados, sem reimplementar IVs/EVs por suposição. |
| Movimentos | `api/moves/MoveTemplate.kt:44–50`: elementalType, damageCategory e power: Double | Poder estático não implementa efeitos Showdown. Selecionar lista explícita de movimentos ofensivos suportados. |
| Envio | `pokemon/Pokemon.kt:799`: sendOut(level: ServerLevel, position: Vec3, illusion: IllusionEffect?, mutation: (PokemonEntity) -> Unit = {}): PokemonEntity? | Respeitar possibilidade de falha e ciclo de vida do Pokémon. |
| Propriedade | `pokemon/Pokemon.kt:1562`: getOwnerUUID(): UUID?; linha 1577: isWild() verifica storeCoordinates | Atribuição deve conferir proprietário real e presença no mundo, não nome exibido. |
| Vida | `entity/pokemon/PokemonEntity.kt:1346`: hurt(source: DamageSource, amount: Float): Boolean | Após dano Minecraft aceito, morte zera currentHealth. Em Pokémon com dono, converte proporcionalmente HP da entidade para Pokemon. Boss selvagem não recebe essa conversão intermediária. |
| HP virtual | `pokemon/Pokemon.kt:336`: currentHealth; PokemonEntity.hurt descrito acima | Milhões de HP devem ficar no RaidHealthManager; não transformar HP Minecraft em fonte autoritativa da raid. |
| Captura | PokemonEntity.isUncatchable() delega a Pokemon.isUncatchable(); este usa UncatchableProperty.uncatchable().matches(this) | Aplicar propriedade oficial e proteção adicional no evento cancelável THROWN_POKEBALL_HIT; validar todos os caminhos de captura em servidor. |
| Batalhas | `PokemonEntity.kt:208`: UNBATTLEABLE; linha 1398: canBattle(player: Player): Boolean | canBattle rejeita UNBATTLEABLE, isBusy, battleId existente, dono, morte e equipe ocupada. Flag é salva/carregada em NBT, linhas 934 e 1033. |
| Eventos | `api/events/CobblemonEvents.kt`: THROWN_POKEBALL_HIT e BATTLE_STARTED_PRE são CancelableObservable | Defesa adicional contra batalhas: cancelar somente se envolver boss ou participantes ativos. BattleStartedEvent.Pre contém battle e reason. |
| Despawn | `PokemonEntity.kt:1369`: checkDespawn() consulta dono, isPersistenceRequired e despawner | Persistência da entidade é necessária. shouldBeSaved também depende da política Cobblemon e dispara POKEMON_ENTITY_SAVE_TO_WORLD. |
| Remoção | PokemonEntity.remove(reason: RemovalReason), linha 1792 | Monitorar desaparecimento por UUID e encerrar com segurança; persistência não impede toda remoção administrativa. |
| Animação | `entity/pokemon/ai/tasks/EatGrassTask.kt:63` usa entity.playAnimation("eat") | Há infraestrutura existente, mas nomes de animações de ataque dependem do recurso/espécie; não assumir animação universal. |

As fontes usam nomes oficiais Mojang (`Level`, `ServerLevel`, `hurt`). A integração deve escolher mappings compatíveis e compilar contra o artefato real antes de declarar assinaturas Java utilizáveis.

## Conflitos de estado

PokemonEntity.kt:277–289 define busyLocks, isBusy = busyLocks.isNotEmpty(), battleId sincronizado e isBattling = presença de BATTLE_ID. A propriedade battle resolve o ID pelo BattleRegistry. Logo, atribuir UUID fictício torna isBattling verdadeiro sem batalha válida. A raid não deve escrever battleId nem criar entradas fictícias no BattleRegistry.

Uma eventual trava própria em busyLocks precisa de identidade exclusiva e remoção garantida, sem limpar travas de outras funcionalidades. A compatibilidade com recall, interação, teleporte, desmaio e batalhas normais ainda exige teste dedicado.

## Integração proposta e pendências

- Estado de raid, cooldown, dano e atribuição processados na thread do servidor, usando apenas valores calculados pelo servidor.
- HP virtual único, dano aplicado limitado ao restante e transição de derrota única.
- Boss identificado por UUID e ID da raid; proteção de dano externo, arena e término seguro.
- Eventos Fabric de ticks, carregamento/descarregamento e dano precisam ser confirmados na versão efetivamente resolvida da Fabric API.
- Persistência transacional das recompensas e entrega ao inventário exigem desenho de recuperação: gravar um marcador simples antes/depois da entrega não garante exatamente uma entrega em caso de crash.
- Auditoria de assinaturas não substitui teste de captura, entrada em batalha, despawn e combate com três jogadores.

## Maven oficial alternativo e bloqueios

`gradle/build-logic/src/main/kotlin/cobblemon.publish-conventions.gradle.kts` da tag auditada declara os destinos oficiais de publicação:

- https://artefacts.cobblemon.com/releases
- https://artefacts.cobblemon.com/snapshots
- https://maven.impactdev.net/repository/development/

A existência do artefato específico nesses destinos ainda não foi confirmada. Ambos os hosts retornaram HTTP 403 no proxy do ambiente. Não foram usados mirrors ou binários desconhecidos.

URLs testadas com falha HTTP 403:

- https://maven.fabricmc.net/
- https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml
- https://api.modrinth.com/v2/project/cobblemon/version
- https://artefacts.cobblemon.com/releases/com/cobblemon/fabric/maven-metadata.xml
- https://maven.impactdev.net/repository/development/

O rascunho contém os domínios Fabric/Modrinth, mas a política observada da máquina em execução ainda contém apenas o preset package_managers. Reaplicar a configuração à instância é necessário antes de repetir a resolução.

## Atualização após publicação da política de rede

Os bloqueios acima são registros históricos. Na nova máquina, a política está `enforced`, com os domínios solicitados presentes. Os quatro endpoints foram retestados e responderam HTTP 200, incluindo o XML Maven do Fabric e do Cobblemon e o catálogo Modrinth.

O POM oficial `https://artefacts.cobblemon.com/releases/com/cobblemon/fabric/1.8.3+1.21.1/fabric-1.8.3+1.21.1.pom` confirma o mesmo gitCommit da auditoria: `ffe005511dd0d3ba68377fa3f95906b3837d9a0e`. O JAR Maven resolvido tem SHA-256 `4bc3b09d25b93b74a5822dc35b06bd6f5fa8bae36ed43a8d2b3904156a61269c` e metadados Fabric `1.8.3+1.21.1`.

### Verificação binária e de compilação

- PokemonProperties.parse é método do Companion em Java: `PokemonProperties.Companion.parse(String)` (sobrecarga gerada). Não é um método estático da classe externa.
- UNBATTLEABLE tem campo de armazenamento privado; o getter Java confirmado por javap é `PokemonEntity.getUNBATTLEABLE()`.
- Pokémon enviados são consultados via `Cobblemon.INSTANCE.getStorage().getParty(ServerPlayer)`, `Pokemon.getEntity()`, `getUuid()` e `getOwnerUUID()`.
- `CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Consumer)` permite `getPokemon()` e `cancel()`.
- `BATTLE_STARTED_PRE` expõe `getBattle().getActors()`, `getPokemonList()` e `getEffectedPokemon()` para detectar os UUIDs protegidos.
- Os callbacks Fabric END_SERVER_TICK, SERVER_STARTED, SERVER_STOPPING, ENTITY_LOAD e ALLOW_DAMAGE foram compilados contra Fabric API 0.116.6+1.21.1.
- O metadado do Cobblemon exige Loader >=0.17.2. O primeiro teste com 0.16.14 falhou; a dependência e os metadados do Shirokai foram corrigidos.

### Validação em servidor dedicado

Servidor de produção com Minecraft 1.21.1, Loader 0.17.2, Fabric API 0.116.6+1.21.1, Fabric Language Kotlin 1.13.6+kotlin.2.2.20 e o JAR Shirokai 0.1.0-dev inicializou. Um Rayquaza foi criado com HP virtual 10.000.000 e UUID registrado. NBT Unbattleable retornou 1b. Dano Minecraft externo foi recusado. Uma segunda raid foi recusada. Encerramento administrativo e por timeout removeram o boss e mantiveram o agendamento.

Isso NÃO valida o combate com múltiplos clientes, captura real por Pokébola, compatibilidade com telas de batalha ou entrega real de recompensas. Esses testes permanecem obrigatórios. A ausência de clientes e os destinos de autenticação bloqueados impedem aceitar o critério principal agora.

O modo dev remapeado `gradlew runServer` ainda falha em reflexão Kotlin do Cobblemon procurando `net.minecraft.class_2960`. Habilitar o plugin Kotlin não resolveu e foi revertido. O JAR remapeado de distribuição funcionou no servidor de produção; use esse fluxo para integração enquanto a limitação do modo dev é investigada.

## Pedido posterior: batalhas tradicionais multiplayer

O requisito de combate em tempo real foi substituído pelo pedido de interface tradicional Cobblemon com entrada contínua e HP global. A auditoria específica está em [docs/COBBLEMON_MULTIPLAYER_AUDIT.md](docs/COBBLEMON_MULTIPLAYER_AUDIT.md), incluindo oito verificações executáveis no simulador oficial da tag exata. As restrições de UNBATTLEABLE e battleId descritas acima explicam o legado, não uma implementação pronta do novo modelo. Nenhuma batalha tradicional multiplayer foi validada.
