# Shirokai Raids

Mod **somente servidor** para Minecraft 1.21.1, Fabric e Cobblemon 1.8.3.

**0.1.3-dev: integração inicial do combate tradicional. Não é uma Release estável.**

O fluxo implementado é `/raid` → enviar seu Pokémon → apontar para o boss e pressionar **R** (ou a tecla configurada no Cobblemon) → escolher os movimentos na interface normal. Cada jogador tem uma batalha real independente, com sua equipe, movimentos e PP; o dano aplicado reduz o HP global do evento. O jogo aguarda a escolha do jogador. Não há `/raid atacar` nem loop de ataques automáticos.

Não exige Shirokai Raids no cliente nem o JAR do Raid Dens no servidor. Não registra cristais, itens, blocos, receitas ou dimensões do Raid Dens. O [escopo vigente](docs/ESCOPO_WORLD_BOSS.md) continua válido; as demais etapas de World Boss/recompensas ainda não estão concluídas.

## Combate e limites

A implementação intercepta o pacote padrão de desafio do Cobblemon somente quando o alvo é o boss ativo. Usa `PlayerBattleActor`, Showdown/Graal e pacotes normais da interface. O Pokémon de cada boss de sessão é uma cópia própria; a entidade pública não recebe um `battleId` de uma das sessões. A vida compartilhada, o crédito efetivo e o encerramento pertencem ao coordenador central.

`maxConcurrentPlayers` tem padrão 100 e aceita de 1 até 100. Ao lotar, o servidor recusa novas batalhas até haver vaga. **A capacidade de 100 combatentes não está comprovada.** Os ensaios realizados até agora incluem três sessões controladas em servidor real; não incluem clientes Minecraft reais nem medição de capacidade para 100 pessoas.

O adaptador exige o serviço Graal padrão do Cobblemon 1.8.3. Cura do boss está desabilitada; Perish Song e sacrifícios do próprio boss têm restrições para impedir vitórias locais. Captura e entrega de Pokémon não fazem parte do evento. Animações/representação visual em clientes e compatibilidade com outros mods ainda precisam de validação. Veja [validação e limitações](docs/VALIDACAO_0.1.3-dev.md) e [auditoria](docs/COBBLEMON_MULTIPLAYER_AUDIT.md).

## Testar o fluxo no jogo

1. Instale Fabric Loader 0.17.2+, Fabric API 0.116.6+1.21.1, Fabric Language Kotlin 1.13.6+kotlin.2.2.20, Cobblemon 1.8.3 e o JAR Shirokai **no servidor de testes**.
2. Construa uma arena segura e use `/raidadmin setspawn` no ponto desejado. Não há geração de arena nem modificação automática de blocos.
3. Use `/raidadmin iniciar rayquaza`.
4. Entre com `/raid`, envie seu Pokémon normalmente e aponte diretamente para o Rayquaza, dentro do alcance normal de desafio.
5. Pressione R (ou a tecla configurada). Escolha um movimento na batalha normal. Trocas usam o menu normal da equipe.
6. Repita com três jogadores e confira BossBar, `/raid top` e `/raid dano`. Verifique PP, saída, entrada tardia e encerramento conjunto.

O HP padrão é 10.000.000. Para um teste curto, configure um valor menor em `config/shirokai-raids/bosses/rayquaza.json` e use `/raidadmin reload` fora de uma raid ativa. Arquivos personalizados não são sobrescritos. Não use a fixture de integração em mundo de produção.

## Comandos

| Comando | Função |
| --- | --- |
| `/raid` | Teleporte seguro se houver raid; senão, próxima ocorrência |
| `/raid entrar` | Orientações para participar pela batalha normal |
| `/raid tempo` | Estado/tempo, sem teleporte |
| `/raid dano`, `/raid top` | Dano efetivo e ranking |
| `/raid sair` | Encerrar a própria batalha e preservar contribuição |
| `/raid recompensas` | Resgatar um stack pendente do sistema existente |
| `/raidadmin iniciar <boss>`, `parar`, `setspawn`, `reload`, `status`, `listar` | Administração |

Participação é permitida por padrão; administração exige OP 2 ou `shirokai.raids.admin`. LuckPerms, quando instalado, respeita negações explícitas. `shirokai.raids.attack` e os campos de cooldown/dano do combate antigo não controlam mais golpes. O limite efetivo usa `maxConcurrentPlayers`.

O sistema de recompensas existente foi preservado nesta etapa. O novo sorteio por raridade, entrega automática e menu de pendências ainda estão pendentes. Não há Raid Pouch. Inventário cheio preserva pendências; reservas interrompidas exigem conferência administrativa conforme a arquitetura existente.

## Compilar e validar

Requer JDK 21 completo, Node e Python:

```sh
bash gradlew --no-daemon build
python3 scripts/run_showdown_audit.py
python3 scripts/run_traditional_probe.py
python3 scripts/verify_jar.py --version 0.1.3-dev
```

Windows: `gradlew.bat build`. O artefato é `build/libs/Shirokai-Raids-0.1.3-dev-fabric-1.21.1.jar`; o verificador gera `SHA256SUMS`. O workflow mantém builds e Artifacts de desenvolvimento em Linux e Windows.

No ambiente cloud, `scripts/setup-cloud.sh` prepara o JDK e a compilação. Com servidor preparado por `scripts/prepare-test-server.sh` e EULA aceita para os testes:

```sh
bash scripts/run-teleport-integration.sh
bash scripts/run-combat-integration.sh
bash scripts/run-challenge-integration.sh
```

Os scripts criam mundos descartáveis separados. As fixtures ficam em `build/integration/shirokai-raids-integration.jar`, fora do JAR distribuível; simulam participantes controlados, não clientes reais. Resultados e logs são preservados no diretório de cada execução.

Não criar uma Release v1.0.0 antes do aceite funcional com clientes reais e dos ensaios de capacidade. Consulte [processo de Releases](release/README.md), [histórico](CHANGELOG.md) e [teste de aceitação](docs/TESTE_DE_ACEITACAO.md).
