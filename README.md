# Shirokai Raids

Mod **somente servidor** para Minecraft 1.21.1, Fabric e Cobblemon 1.8.3.

**0.1.11-dev: menu de entrada e saída, retorno à origem e bloqueio de comandos durante a participação. Não é uma Release estável.**

O fluxo implementado é `/raid` → menu de informações → **Entrar na raid** → enviar seu Pokémon → apontar para o boss e pressionar **R** (ou a tecla configurada no Cobblemon) → escolher os movimentos na interface normal. Cada jogador tem uma batalha real independente, com sua equipe, movimentos e PP; o dano aplicado reduz o HP global do evento. O jogo aguarda a escolha do jogador. Não há `/raid atacar` nem loop de ataques automáticos.

O boss público aparece com contorno luminoso nativo (Glowing), sem mod adicional no cliente. O mod não altera equipes do scoreboard para definir cores. O estado foi conferido no servidor; a aparência no modelo Cobblemon precisa da observação com cliente real.

Não exige Shirokai Raids no cliente nem o JAR do Raid Dens no servidor. Não registra cristais, itens, blocos, receitas ou dimensões do Raid Dens. O [escopo vigente](docs/ESCOPO_WORLD_BOSS.md) continua válido; as demais etapas de World Boss/recompensas ainda não estão concluídas.

Para preparar a instalação e o teste final, siga [o guia do servidor](docs/PREPARAR_SERVIDOR.md). Ele reúne arena/lobby, comandos, campos ativos, LuckPerms, SkiesClear, backups e conferência de reservas antigas. O mod mantém a arena carregada apenas durante o evento e libera seu próprio ticket ao encerrar, sem apagar construções nem alterar `/forceload` externo.

## Combate e limites

A implementação intercepta o pacote padrão de desafio do Cobblemon somente quando o alvo é o boss ativo. Usa `PlayerBattleActor`, Showdown/Graal e pacotes normais da interface. O Pokémon de cada boss de sessão é uma cópia própria; a entidade pública não recebe um `battleId` de uma das sessões. A vida compartilhada, o crédito efetivo e o encerramento pertencem ao coordenador central.

`maxConcurrentPlayers` tem padrão 100 e aceita de 1 até 100. Ao lotar, o servidor recusa novas batalhas até haver vaga. **A capacidade de 100 combatentes não está comprovada.** Os ensaios realizados até agora incluem três sessões controladas em servidor real; não incluem clientes Minecraft reais nem medição de capacidade para 100 pessoas.

O adaptador exige o serviço Graal padrão do Cobblemon 1.8.3. Cura do boss está desabilitada; Perish Song e sacrifícios do próprio boss têm restrições para impedir vitórias locais. Captura e entrega de Pokémon não fazem parte do evento. Animações/representação visual em clientes e compatibilidade com outros mods ainda precisam de validação. Veja [validação das recompensas](docs/VALIDACAO_0.1.5-dev.md), [validação do combate](docs/VALIDACAO_0.1.4-dev.md) e [auditoria](docs/COBBLEMON_MULTIPLAYER_AUDIT.md).

## Testar o fluxo no jogo

1. Instale Fabric Loader 0.17.2+, Fabric API 0.116.6+1.21.1, Fabric Language Kotlin 1.13.6+kotlin.2.2.20, Cobblemon 1.8.3 e o JAR Shirokai **no servidor de testes**.
2. Construa uma arena segura e use `/raidadmin setspawn` no ponto desejado. Não há geração de arena nem modificação automática de blocos.
3. Use `/raidadmin iniciar rayquaza`.
4. Entre com `/raid`, envie seu Pokémon normalmente e aponte diretamente para o Rayquaza, dentro do alcance normal de desafio.
5. Pressione R (ou a tecla configurada). Escolha um movimento na batalha normal. Trocas usam o menu normal da equipe.
6. Repita com três jogadores e confira BossBar, `/raid top` e `/raid dano`. Verifique PP, saída, entrada tardia e encerramento conjunto.

O HP padrão é 10.000.000. Para um teste curto, configure um valor menor em `config/shirokai-raids/bosses/rayquaza.json` e use `/raidadmin reload` fora de uma raid ativa. Arquivos personalizados não são sobrescritos. Não use a fixture de integração em mundo de produção.

O menu `/raid` mostra boss, HP, tempo restante e combatentes. Use **Entrar na raid** ou `/raid entrar`; o destino fica na arena definida por `/raidadmin setspawn`, dentro de `arenaRadius`. Só quem entrou e está dentro da área pode desafiar o boss. Sair da área encerra a batalha, mas a posição de retorno permanece salva até usar `/raid sair` ou **Sair da raid** no menu. Enquanto estiver na raid, todos os outros comandos ficam bloqueados; somente `/raid`, `/raid menu` e `/raid sair` são permitidos. Fechar o menu não sai da raid. A posição anterior, dimensão e orientação são persistidas, inclusive após desconexão ou reinício. Ao terminar o evento, o retorno continua disponível pelo comando ou menu.

Os contadores de evento, BossBar, avisos e histórico aparecem em horas, minutos e segundos (por exemplo, `1h 05min 30s`). Os campos JSON e argumentos administrativos de intervalo/duração continuam em segundos para preservar compatibilidade.

Veja [mensagens editáveis, novos bosses e sorteio a cada duas horas](docs/PERSONALIZACAO.md).

## Configurar o boss e os drops

Em `config/shirokai-raids/bosses/<id>.json`, `hp` define a vida compartilhada (1 a 1 trilhão), `scale` o tamanho (0,5 a 5), `damageMultiplier` o multiplicador dos golpes do boss (0 a 100; padrão 1) e `rewards` o nome da tabela de drops. O multiplicador atua no dano direto de movimentos, inclusive dano fixo, antes de proteções como Focus Sash; status e recoil mantêm as regras nativas. Recarregue fora de uma raid ativa com `/raidadmin reload`.

Veja os exemplos de [boss](docs/examples/bosses/rayquaza.json) e [drops](docs/examples/rewards/default.json). Na tabela nova, `rollsPerPlayer` vale para todas as faixas, `minimumContribution` usa frações do HP total e as chances de raridade de cada faixa devem somar 100. A maior faixa atingida define as chances; o peso do item define o segundo sorteio. `maximumItems` limita a quantidade total por jogador. Para migrar uma tabela antiga, adapte explicitamente o exemplo; mantenha uma cópia da configuração anterior.

Sair encerra somente sua sessão e libera a vaga. Seu dano permanece no ranking e conta para recompensas; voltar ao mesmo evento soma à contribuição anterior. `/raid sair` também salva esse acumulado em disco. A queda do servidor continua sujeita ao intervalo de persistência para os demais golpes.

## Comandos

| Comando | Função |
| --- | --- |
| `/raid`, `/raid menu` | Menu de informações, entrada e saída da raid |
| `/raid entrar` | Salva o local anterior e teleporta com segurança para a arena |
| `/raid lobby` | Teleporte seguro ao lobby configurado, fora de batalhas e com cooldown |
| `/raid tempo` | Estado/tempo, sem teleporte |
| `/raid dano`, `/raid top` | Dano efetivo e ranking |
| `/raid historico` | Menu com suas 20 últimas raids, resultado, contribuição, posição e drops |
| `/raid ranking` | Ranking global público: participações, dano acumulado e vitórias |
| `/raid sair` | Encerra a batalha e retorna ao local anterior à entrada, preservando contribuição |
| `/raid recompensas`, `/raid pendentes` | Menu de pendências com botão de entrega, atualização e paginação |
| `/raid resgatar` | Tentar entrega pelo comando, com o mesmo journal e cooldown do botão |
| `/raidadmin iniciar <boss>`, `parar`, `setspawn`, `setlobby`, `reload`, `status`, `info`, `listar` | Administração |
| `/raidadmin intervalo <segundos>`, `duracao <segundos>`, `limite <1–100>`, `automatico <true/false>` | Configuração persistente fora de raids ativas |
| `/raidadmin reservas [confirmar/devolver "<id>" <motivo>]` | Conferência auditada de reservas antigas; exige permissão específica |

Participação é permitida por padrão; administração exige OP 2 ou `shirokai.raids.admin`. LuckPerms, quando instalado, respeita negações explícitas. `shirokai.raids.attack` e os campos de cooldown/dano do combate antigo não controlam mais golpes. O limite efetivo usa `maxConcurrentPlayers`.

Novas tabelas padrão usam duas etapas: raridade conforme a faixa de contribuição, depois item ponderado dentro da raridade. Arquivos antigos continuam usando seus itens e regras anteriores; não são sobrescritos. Não há Raid Pouch. O menu de histórico exibe drops e saldo pendente por evento; o botão do menu `/raid recompensas` e o comando `/raid resgatar` usam a mesma entrega persistente.

Os menus funcionam com o cliente Cobblemon normal, usando uma janela de baú. Abra `/raid historico` para ver boss, data, resultado, duração, dano efetivo, contribuição sobre o HP inicial, posição no evento, quantidade de participantes e drops sorteados. As setas mudam a página; os botões inferiores levam ao ranking global por participações, dano ou vitórias e ao menu de pendências. Clique em **Seus totais** para atualizar a página. Ícones não podem ser retirados nem usados como itens; somente o botão de entrega do menu de pendências aciona resgate.

O histórico guarda os 20 últimos eventos encerrados de cada jogador. Os totais globais continuam acumulando além desse limite e incluem jogadores offline. Participar significa iniciar uma batalha aceita pelo servidor: sair e retornar conta uma vez, inclusive se não causou dano. Vitórias são eventos vencidos nos quais participou; o dano acumulado inclui eventos cancelados/expirados. O registro começou na 0.1.6-dev, sem inventar raids antigas. Em queda abrupta, o histórico usa o último snapshot e marca o evento como interrompido. Veja [detalhes e validação](docs/VALIDACAO_0.1.6-dev.md).

Todos podem consultar os rankings por padrão. LuckPerms pode restringir `shirokai.raids.history` e `shirokai.raids.ranking`, além de `shirokai.raids.use`. O fuso do menu é `menuTimeZone` em `config.json`, padrão `America/Sao_Paulo`; valores inválidos são rejeitados no reload. O histórico pessoal não permite consultar o de outro jogador.

Após a derrota global, os drops entram no registro persistente antes da entrega. Jogadores online recebem no inventário principal, completando stacks compatíveis e usando slots vazios. Quem saiu continua elegível; quem está offline recebe ao entrar. Falta de espaço preserva o saldo, com novas tentativas periódicas e por `/raid recompensas`. Itens não são jogados no chão. Cada passagem atende um jogador, com até quatro transações; filas grandes podem demorar.

Novas reservas usam recibos nas tags vanilla do jogador, salvos no mesmo arquivo que o inventário, para reconciliar reinícios sem repetir a inserção. Reservas antigas em `uncertain` continuam exigindo conferência administrativa. Falha de persistência suspende raids e entregas. Preserve **juntos** `state.json` e os arquivos `playerdata` ao restaurar backups; recuperação de apenas um deles, corrupção de arquivos ou mods que alterem recibos/armazenamento não têm garantia de entrega única. Veja [protocolo e limites](docs/ENTREGA_RECOMPENSAS.md).

## Compilar e validar

Requer JDK 21 completo, Node e Python:

```sh
bash gradlew --no-daemon build
python3 scripts/run_showdown_audit.py
python3 scripts/run_traditional_probe.py
python3 scripts/verify_jar.py --version 0.1.11-dev
```

Windows: `gradlew.bat build`. O artefato é `build/libs/Shirokai-Raids-0.1.11-dev-fabric-1.21.1.jar`; o verificador gera `SHA256SUMS`. O workflow mantém builds e Artifacts de desenvolvimento em Linux e Windows.

No ambiente cloud, `scripts/setup-cloud.sh` prepara o JDK e a compilação. Com servidor preparado por `scripts/prepare-test-server.sh` e EULA aceita para os testes:

```sh
bash scripts/run-teleport-integration.sh
bash scripts/run-combat-integration.sh
bash scripts/run-challenge-integration.sh
```

Os scripts criam mundos descartáveis separados. As fixtures ficam em `build/integration/shirokai-raids-integration.jar`, fora do JAR distribuível; simulam participantes controlados, não clientes reais. Resultados e logs são preservados no diretório de cada execução.

Não criar uma Release v1.0.0 antes do aceite funcional com clientes reais e dos ensaios de capacidade. Consulte [processo de Releases](release/README.md), [histórico](CHANGELOG.md) e [teste de aceitação](docs/TESTE_DE_ACEITACAO.md).
