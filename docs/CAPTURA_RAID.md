# Entrada, captura privada e retorno automático

## Entrada com inventário vazio

Antes de usar `/raid entrar` ou o botão de entrada, guarde os itens dos 36 slots principais, hotbar, mão secundária e cursor. Se houver algum item, a entrada é recusada sem mover ou apagar os itens. Armadura e acessórios já equipados são permitidos. O código não toca no armazenamento de acessórios de outros mods. Efeitos e consumo de itens da equipe Pokémon continuam conforme Cobblemon e os mods instalados.

Nenhuma nova entrada cria backup de inventário. Armadura permanece equipada, com suas regras normais de durabilidade e morte. Não há promessa de proteção de equipamento contra a morte vanilla. Coleta, crafting, manipulação do inventário e troca manual de itens dos Pokémon ficam bloqueados durante a participação. Recompensas comuns permanecem pendentes até a saída/retorno para não ocupar o inventário usado na captura.

Essa regra controla a entrada como participante pelo comando/menu: caminhar até a arena não inscreve o jogador e não dá direito a enfrentar o boss ou capturar uma cópia. Slots de acessórios continuam sob controle de seus próprios mods; compatibilidade com menus e efeitos desses mods requer teste no servidor instalado.

Se foi usado o build local anterior com inventário guardado, existe somente uma rotina de recuperação de dados legados, na entrada/respawn ou ao sair. Ela preserva o backup se houver conflito com itens atuais ou erro de leitura. O novo fluxo nunca cria outro backup. Recupere os itens de builds antigos antes de remover o mod.

## Quem ganha a captura

A lista é fechada no instante da vitória: precisa estar online, vivo, dentro da arena, ter entrado na raid e ter causado dano efetivo positivo. Quem estava offline, fora da área ou já havia saído não recebe oportunidade futura. O ranking global determina as bolas, mesmo que alguma posição pertença a um jogador ausente; as posições não são redistribuídas.

| Posição por dano | Bolas |
| --- | --- |
| 1º | 5 |
| 2º e 3º | 3 |
| Demais elegíveis | 1 |

Empates usam UUID como critério determinístico. Cada jogador recebe uma cópia privada: espécie, forma, nível e shiny do boss, com os demais atributos gerados normalmente, sem o HP global nem o multiplicador de dano da raid. O boss de combate continua impossível de capturar.

## Batalha nativa: dois minutos

Ao terminar o combate coletivo, o servidor mostra a cópia exclusiva na arena, com contorno brilhante e nome de captura. Envie seu Pokémon, mire na cópia e pressione R para iniciar a batalha Cobblemon normal. Não há início automático. Uma BossBar exclusiva mostra o tempo e as bolas restantes antes e durante a batalha. É preciso ter pelo menos um Pokémon em condições de lutar. Movimentos, trocas, PP, dano e status seguem as regras normais. Não há cura automática da equipe. Preparar a nova batalha não reinicia o relógio.

O prazo é fixo: **120 segundos desde a derrota do boss**. Ao terminar, a batalha é encerrada e o alvo e as bolas temporárias são removidos. Capturar, fugir, perder a batalha, sair da área, morrer, desconectar ou usar `/raid sair` também encerra a oportunidade. Não existe `/raid capturar`, retomada após reconexão ou captura pendente. Reiniciar o servidor encerra as oportunidades restantes.

As bolas são vinculadas à sua sessão e ao seu alvo. Há um projétil por vez e os lançamentos respeitam o turno de captura nativo. Cada lançamento efetivo gasta uma tentativa, inclusive se errar o alvo. Não há reembolso de bolas erradas. A animação e a inserção do Pokémon na equipe/PC usam o Cobblemon. Falhas também fazem os três shakes; o resultado é visualizado antes da limpeza normal, com pequena espera final, sempre respeitando o limite de dois minutos. O chat distingue captura bem-sucedida, bolas esgotadas, tempo esgotado, saída e fim da batalha sem captura.

A chance padrão continua sendo **2% por lançamento**, substituindo o resultado nativo nesta fase: HP/status/captura crítica não aumentam essa chance fixa. Cinco tentativas representam aproximadamente 9,6% de chance de ao menos um sucesso. A batalha permite enfraquecer e aplicar status normalmente, mas eles não alteram a probabilidade configurada nesta implementação.

Um sucesso é reservado antes da animação terminar, para impedir repetição. Uma desconexão, saída, término do prazo ou interrupção nessa janela pode encerrar a oportunidade sem entregar o Pokémon. Não há transação atômica entre os arquivos da raid e os do Cobblemon.

## Retorno após dez minutos

O servidor registra um prazo fixo de **600 segundos após a vitória** para os participantes daquele evento. Quem ainda estiver dentro da arena ao vencer o prazo volta à posição, dimensão e orientação salvas na entrada. A regra limpa participantes remanescentes, inclusive AFK; movimentar-se dentro da arena não renova o prazo.

O prazo não começa ao terminar a captura e não depende do horário de reconexão. Ficar offline não o reinicia: ao voltar dentro da arena após o limite, o jogador é retornado. Quem já está fora da área não é teleportado à força. Mortos aguardam respawn; dimensão de origem indisponível impede o retorno até ser restaurada. `/raid sair` permite retorno antes e encerra o registro desse evento.

## Configuração e teste

Em `config/shirokai-raids/config.json`, `capture.enabled`, `firstPlaceBalls`, `podiumBalls`, `participantBalls` e `chancePerThrow` controlam habilitação, quantidades (1–64) e chance (maior que zero e até 1). `durationSeconds` deve permanecer em `120`; o retorno é fixo em dez minutos. Alterações valem para novas vitórias. Use `/raidadmin reload` fora de uma raid ativa. Mensagens estão em `messages.json`, nas chaves `captura.*` e `raid.inventario_vazio`/`raid.retorno_automatico`.

O teste `bash scripts/run-capture-integration.sh` usa servidor dedicado descartável e requer a EULA aceita explicitamente. Exercita batalhas privadas, cálculo/armazenamento nativo, elegibilidade, limpeza seletiva e os limites de tempo com avanço controlado do relógio da verificação. Não representa clientes humanos nem ensaio de capacidade para 100 jogadores.

## Local de entrada

Use `/raidadmin setspawn` no local do boss e `/raidadmin setentrada` no destino desejado para os jogadores. O ponto de entrada conserva coordenadas exatas, dimensão, yaw e pitch. Defina-o sobre piso sólido e com espaço livre, sem uma raid ativa. Se ficar bloqueado ou fora da borda, o teleporte é recusado sem mover o jogador nem registrar uma nova entrada. Não há fallback silencioso para perto do boss quando um ponto personalizado está configurado.

O ponto pode estar fora da arena ou em outra dimensão existente, mas só é possível desafiar o boss e receber captura estando dentro da arena na vitória. Posicioná-lo longe não aumenta `arenaRadius`. Sem ponto configurado, a busca segura perto do boss permanece como compatibilidade. `/raid lobby` e `/raidadmin setlobby` foram removidos; a configuração antiga de lobby não é usada como entrada automaticamente.

As mensagens novas são `captura.nome`, `captura.bossbar`, `captura.sucesso`, `captura.esgotadas`, `captura.tempo_esgotado`, `captura.saiu`, `captura.derrota`, `captura.cancelada` e `entrada.*`. Chaves omitidas no JSON personalizado usam os padrões do mod.
