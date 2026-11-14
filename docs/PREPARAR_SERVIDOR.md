# Preparar o servidor para o teste final

Use o build de desenvolvimento `0.1.19-dev` num servidor de testes com backup. Ela reúne o combate tradicional, drops, histórico/rankings e os controles operacionais; não é uma versão estável nem comprovação de 100 jogadores.

## Instalação

Servidor: Java 21, Minecraft 1.21.1, Fabric Loader 0.17.2+, Fabric API 0.116.6+1.21.1, Fabric Language Kotlin 1.13.6+kotlin.2.2.20 e Cobblemon 1.8.3. Instale um único JAR Shirokai Raids em `mods/`. Não instale a fixture de integração nem o Raid Dens. Jogadores usam o cliente Cobblemon normal, sem Shirokai Raids no cliente.

Baixe o JAR e `SHA256SUMS` da pré-release em Releases. Pare o servidor e preserve, juntos, o mundo, `playerdata`, `data/shirokai-raids/state.json` e `config/shirokai-raids` antes de atualizar. Não substitua configurações existentes pelos exemplos: adapte os campos desejados. Arquivos padrão só são criados quando ausentes.

## Arena e entrada

Use uma dimensão que já exista no servidor. O mod não cria dimensões, arenas, estruturas, portais nem altera blocos. Construa um piso seguro, com espaço para o boss e para os jogadores; evite fluidos, fogo e bordas perigosas.

Com OP 2 ou permissão administrativa, sem uma raid ativa:

```text
/raidadmin automatico false
/raidadmin intervalo 7200
/raidadmin duracao 1200
/raidadmin limite 3
```

Fique no centro destinado ao boss, orientado como deseja, e execute `/raidadmin setspawn`. Vá ao ponto onde os jogadores devem chegar, sobre piso sólido e com espaço livre, ajuste sua direção e execute `/raidadmin setentrada`. As posições pertencem ao seu mundo; não é possível escolher coordenadas válidas para ele a partir do repositório.

`/raid` e `/raid entrar` levam ao ponto de entrada durante o evento. Sem configuração, é usada uma posição segura perto do boss. O jogador envia o Pokémon e usa R/tecla normal de desafio; escolhe os movimentos manualmente. Após a vitória, quem tiver direito verá uma cópia brilhante e uma BossBar individual; deve usar R na cópia para iniciar a captura, dentro dos dois minutos desde a vitória. `/raid sair` encerra a sessão e retorna à origem. Os comandos de lobby foram removidos.

A arena recebe um ticket temporário enquanto o evento existe. Encerrar/cancelar libera apenas esse ticket; não remove blocos, mundos ou tickets de outros mods. O raio do ticket varia de 2 a 9 chunks conforme o raio configurado da arena. Apenas o centro é carregado imediatamente; chunks vizinhos são processados pelo Minecraft. Uma busca segura pode recusar um destino ainda indisponível, sem mover o jogador. Não há carregamento ilimitado, mas o custo precisa ser medido no hardware real.

## Boss e drops

Adapte `config/shirokai-raids/bosses/rayquaza.json`: `hp`, `scale`, `damageMultiplier`, nome/espécie/nível e `rewards`. O padrão de HP é 10 milhões; para o primeiro teste use um valor menor explicitamente. Não altere esses arquivos durante um evento: encerre e faça `/raidadmin reload`.

Adapte a tabela referenciada em `rewards/`. Os exemplos em `docs/examples/` têm faixas de contribuição e pools ponderados. Só use itens de mods instalados. Um ID inexistente ou destino inválido rejeita o reload preservando a configuração anterior. Tabelas antigas mantêm suas regras; nenhuma migração silenciosa.

Após vitória, a entrega é automática, inclusive para quem saiu da batalha. Offline ou sem espaço mantém saldo. `/raid recompensas` ou `/raid pendentes` abre a lista; o botão tenta entregar até quatro transações e mantém cooldown. `/raid resgatar` usa o mesmo caminho por comando. Ícones são informativos e não podem ser retirados. Histórico e ranking estão em `/raid historico` e `/raid ranking`.

## Agendamento e informações

O intervalo começa quando o boss é derrotado, sem descontar o tempo de combate. Em caso de expiração ou cancelamento, o intervalo completo começa no encerramento. `SKIP_MISSED` pula uma ocorrência vencida ao reiniciar; `START_ONCE` admite uma única ocorrência vencida, sem acumular eventos. Os avisos configurados aparecem uma vez em cada limiar. Avisos maiores que o intervalo ficam inativos sem serem apagados; após uma pausa é emitido apenas o aviso mais próximo ainda pertinente.

Quando o cenário estiver preparado, `/raidadmin automatico true` habilita e reagenda a próxima ocorrência. Para testar imediatamente: `/raidadmin iniciar rayquaza`. Para cancelar: `/raidadmin parar`. `/raidadmin status` ou `info` mostra persistência, agendamento, configurações, sessões, ticket, pendências, MSPT médio e memória heap. O valor de limite configurado não demonstra capacidade operacional.

O exemplo `docs/examples/config.json` contém os campos ativos. Campos legados como `attackCooldownTicks`, `supportedMoves`, `attackRange`, `maximumParticipants`, `bossAttackSeconds` e `bossDamage` continuam compatíveis com arquivos antigos, mas não controlam o combate tradicional. Use `maxConcurrentPlayers` e os parâmetros atuais por boss; não há ataques automáticos do sistema antigo.

## Permissões

Sem LuckPerms, jogadores têm uso normal e administração exige OP 2. Com LuckPerms, respeitam-se negações explícitas; cache de permissão indisponível não concede acesso. A integração usa API pública opcional, mas deve ser testada com a versão instalada no seu servidor.

| Permissão | Finalidade |
| --- | --- |
| `shirokai.raids.use` | Comando principal e acesso aos recursos |
| `shirokai.raids.join` | Arena e desafio do boss |
| `shirokai.raids.rewards` | Menu e tentativas manuais de entrega |
| `shirokai.raids.history` | Histórico pessoal |
| `shirokai.raids.ranking` | Ranking global público |
| `shirokai.raids.admin` | Administração |
| `shirokai.raids.reconcile` | Conferir reservas antigas, também exigindo acesso administrativo |

Por exemplo, conceda `shirokai.raids.admin` e `shirokai.raids.reconcile` ao grupo administrativo escolhido com `/lp group <grupo> permission set <permissão> true`. Não conceda reconciliação a jogadores comuns. `shirokai.raids.attack` é legado e não controla movimentos.

## SkiesClear e proteção do boss

A entidade pública recebe as tags `shirokai_raid_boss` e `shirokai_raids_boss` (legada), propriedade oficial `uncatchable`, persistência e proteção de dano externo. Entidades normais, mesmo da mesma espécie, não recebem essas proteções.

Na versão auditada SkiesClear 1.1.2, inclua **`#tag=shirokai_raid_boss` nas blacklists das duas categorias de limpeza, entidades e Cobblemon**. Mantenha as outras entradas existentes. `clear_persistent` e `clear_named` não substituem essa exclusão. Não bloqueie a espécie Rayquaza inteira nem todos os lendários. O nome exato dos campos depende da versão instalada: confira o arquivo dela, sem substituí-lo por um modelo inventado. O mod não reescreve configurações do SkiesClear.

O teste final deve incluir limpeza automática/manual com SkiesClear instalado e tentativa real de captura; apenas aplicar tags não comprova essa compatibilidade em runtime.

## Reservas antigas e recuperação

Novas entregas usam recibos salvos com o inventário; veja `ENTREGA_RECOMPENSAS.md`. Erro de persistência suspende raids/entregas; corrija o armazenamento antes de reiniciar. Não apague `state.json` para contornar o erro.

Os [testes de reinício real](VALIDACAO_REINICIO.md) cobriram parada normal, `SIGKILL`, preservação dos JSONs e recuperação de recibos em quatro processos. A raid ativa é cancelada/interrompida; o ponto salvo permanece para a próxima raid. O boss antigo não continua com o mesmo HP após reiniciar.

Reservas antigas em `uncertain` não têm prova automática de recebimento. Confira inventário, logs e backup antes de decidir:

```text
/raidadmin reservas
/raidadmin reservas confirmar "<id completo>" recebimento comprovado no backup e inventário
/raidadmin reservas devolver "<id completo>" comprovado que os itens não foram recebidos
```

`confirmar` registra que já foram recebidos e não cria itens. `devolver` devolve uma única vez às pendências do proprietário identificado no ID antigo. ID sem proprietário identificável não permite devolução automática nem atribuição a outro jogador. Operador, motivo, quantidade, item e horário ficam em `reviews` no estado persistente. A auditoria e a remoção da reserva são gravadas juntas; repetir o comando não repete itens. Uma decisão administrativa errada ainda pode causar perda ou duplicação: o comando não adivinha o resultado de uma transação antiga.

## Teste final com jogadores

Siga `TESTE_DE_ACEITACAO.md`, anotando versão, commit e configurações. Comece com um jogador e três; só depois avance para 10, 25, 50 e 100, ajustando o limite e o `max-players` vanilla conscientemente. Registre MSPT, sessões, heap, consumo de CPU, latência, erros e resultado por etapa. Os dados de status são um auxílio, não um benchmark completo.

Ainda devem ser observados em clientes reais: R, interface, movimentos, PP, troca, animações, fuga, desconexão/reconexão, teleporte entre dimensões, captura bloqueada, encerramento coletivo e menus. LuckPerms e SkiesClear precisam dos ensaios com as versões reais do servidor. As gravações críticas permanecem síncronas e limitadas por passagem; não há promessa de desempenho para 100 jogadores.
