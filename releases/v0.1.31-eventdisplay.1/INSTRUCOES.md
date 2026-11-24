# Zorykai Raids 0.1.31-eventdisplay.1

Fabric Minecraft 1.21.1, Java 21, Cobblemon 1.8.3. Parte da distribuição completa 0.1.29-receipts.1, preservando o nome Zorykai, retorno/autenticação, captura privada, batalha e mensagens de dano. Não instala nem altera o servidor de produção.

Continua a implementação de eventos da 0.1.30 com o build sobre o binário completo fixado 0.1.29. As fontes históricas da raiz do GitHub não substituem esse binário recente.

## Como cada evento funciona

Todos os comandos administrativos exigem `shirokai.raids.admin` (OP nível 2 sem LuckPerms). No console, omita a barra. Eventos são manuais e valem para a próxima raid; não há agenda recorrente. O catálogo automático deve estar habilitado.

| Evento | Exemplo | Comportamento e limites |
|---|---|---|
| Chuva de Shinies | `/raidadmin evento shiny 10%` | Próximo boss tem 10% de chance de shiny; a captura privada usa o mesmo resultado. Não aumenta encontros selvagens nem garante captura. Aceita 0–100%. |
| Genética Perfeita | `/raidadmin evento ivs 3` | Pelo menos 3 atributos com IV 31 no boss e em cada alvo privado de captura. Seleção fixa por evento, sem rerrolagem ao abrir a captura. Não reduz atributos que já eram 31. Aceita 1–6. |
| Caçada Recompensada | `/raidadmin evento drop 2` | Multiplica por 2 a chance dos itens e fragmentos modulares: 15% vira 30%, com teto 100%. Não duplica a quantidade de fragmentos. No TOP 3, multiplica a probabilidade combinada dos dois testes, sem tornar cosméticos/moedas mais prováveis. |
| Fortuna de Tokens | `/raidadmin evento token 2` | Multiplica por 2 a quantidade final de tokens Impactor, respeitando os limites de entrega. Não multiplica dollars. Inclui eventual compensação em tokens de cosmético duplicado, sem aplicar duas vezes. |
| Invasão Lendária | `/raidadmin evento invasao` | Próxima raid sorteia categoria lendário/supremo, inicialmente 80/20; só categorias com espécie elegível participam. Mantém filtros do catálogo e não cai silenciosamente para normal. |
| Raid Elemental | `/raidadmin evento elemental fogo` | Filtra o tipo primário ou secundário da espécie. Aceita os 18 tipos em inglês e aliases em português, como água, elétrico, fada e dragão. Se combinado com raridade impossível, recusa a alteração. |
| Votação da comunidade | `/raidadmin evento votacao charizard gyarados tyranitar` | Duas ou três espécies elegíveis, voto pelo comando `/raid votar 1`, `2` ou `3`. Um voto por UUID; pode trocar a cada 2s. Repetir a mesma opção não grava de novo. Dura 60s por padrão; empate/zero votos usam desempate determinístico. Vencedor fica reservado para a próxima raid. Não inicia imediatamente. |
| Boss Enfurecido | `/raidadmin evento enfurecido` | Dano causado pelo boss ×1,5 desde o início. Na vitória: quantidade dos itens modulares ×1,5 (arredondada para cima), tokens ×1,5 (parte inteira), fragmentos e dollars com quantidade original. Nenhum prêmio por atingir fases de HP; não permite farmar uma fase repetidamente. |
| Dupla Ameaça | `/raidadmin evento dupla lendario supremo` | Duas raids completas em sequência. Sem argumentos, lendário → supremo. Uma arena/boss por vez; cada vitória tem seu próprio sorteio e resgate manual. Captura, animação e retornos da primeira precisam terminar antes da contagem de 30s para a segunda. |

**Tabelas antigas:** bônus de recompensa afetam as tabelas modulares. As tabelas `legacyItems` usam pesos e quantidade de sorteios, não porcentagem independente por item; permanecem com suas regras originais. Para ter uma única tabela de prêmios com bônus, migre a configuração para as recompensas modulares e use `legacyItems=false`; esta atualização não muda esse campo automaticamente nem apaga prêmios antigos.

Multiplicadores de drop/token aceitam valores de 1 a 10, inclusive decimais. Exemplo de combinação: token 2 + Enfurecido = 3× tokens, sempre limitado por `maximumAmount`. Quantidade de fragmentos nunca recebe bônus de quantidade. Held items da lista protegida têm no máximo uma unidade por raid, inclusive no Enfurecido. Se as integrações modulares estiverem desativadas, comandos de bônus de recompensa são recusados.

## Seleção e consulta

```
/raidadmin proxima epico
/raidadmin iniciar epico
/raidadmin iniciar
/raidadmin eventos
/raidadmin evento cancelar
/raidadmin evento reload
```

`proxima` reserva a raridade; não inicia imediatamente. `iniciar` sem argumento inicia o plano anunciado; com raridade, escolhe essa categoria sem perder os demais modificadores. Aceita normal, raro/rara, epico/épico, lendario/lendário e supremo/suprema. O comando antigo de iniciar um boss manual não ignora uma reserva de evento: use o novo comando.

Alterações afetam somente o próximo boss. Cada raid recebe uma cópia própria do plano e dos bônus. Configurar modificadores responde somente ao staff que executou o comando. Um aviso público curto informa o início de uma raid especial; os detalhes alternam na boss bar. `/raidadmin eventos` consulta sem alterar nada e também exige staff. Os comandos de evento não são adicionados à lista de comandos gerais liberados dentro da raid: somente a votação recebe essa exceção, preservando os filtros de autenticação existentes.

## Votação e dupla: barreiras de segurança

- Durante uma votação, próximos modificadores e início ficam bloqueados. Finalizar a votação fixa o vencedor; não muda a raid que já está acontecendo.
- Durante uma dupla, novas reservas ficam bloqueadas. `cancelar` remove somente programação futura; não encerra o boss atual nem remove prêmios/capturas.
- Na primeira vitória, não começa a segunda enquanto houver animação, sessão de captura, direito de captura ainda válido ou participante online com retorno/inventário pendente. O intervalo reinicia se a arena deixar de estar segura.
- Retornos de jogadores offline permanecem salvos para o fluxo normal de reconexão e não travam a fila para sempre.
- Primeira etapa cancelada ou expirada: segunda cancelada. Segunda perdida: prêmios da primeira continuam válidos; não existe prêmio adicional por completar a sequência.
- As duas etapas recebem os mesmos bônus anunciados, mas sorteios/recompensas têm IDs distintos. A segunda também passa pelas verificações normais de dimensão, arena, capacidade do journal e spawn.
- Se não houver condição segura em 900s após a primeira vitória, a sequência é interrompida com aviso. Não força teleporte nem corta captura. Se uma tentativa da segunda etapa falhar, interrompe a sequência sem tentativas por tick.
- Reinício/queda cancela votação aberta e sequência já iniciada. Mantém os eventos armados que ainda não começaram, retornos e recompensas já sorteadas. Não recria bosses nem oportunidades de captura encerradas.
- A reserva é consumida somente após inserir o boss, no mesmo salvamento atômico do estado da raid. Falha de persistência mantém a proteção original que suspende o mod; não prossegue entregando prêmios sem registro.
- Prêmios continuam no `/raid recompensas` para resgate manual. Sem entrega em massa no encerramento, sem débito/crédito de novo e sem reenvio de recibos já confirmados.

## Configuração

Gerado automaticamente: `config/shirokai-raids/events.json`.

```json
{
  "schema": 1,
  "voteSeconds": 60,
  "duoDelaySeconds": 30,
  "duoMaximumWaitSeconds": 900,
  "eventBarSeconds": 4,
  "normalBarSeconds": 8
}
```

Use `/raidadmin evento reload` após editar esse arquivo. Voto: 15–600s; intervalo: 10–300s; espera: 300–7200s. Duração de uma votação já aberta fica fixada no seu encerramento salvo. Mudanças no intervalo/limite de espera valem para sequências ainda aguardando. Dados operacionais ficam no `state.json` existente; não os apague nem edite com o servidor ligado. Configuração inválida suspende eventos e novos inícios automáticos até correção e reload/reinício, sem alterar recompensas existentes.

## Instalação e rollback

1. Pare o servidor e faça backup de `config/shirokai-raids/` e `<mundo>/data/shirokai-raids/`.
2. Remova o JAR anterior de Raids e coloque somente `Zorykai-Raids-0.1.31-eventdisplay.1-fabric-1.21.1.jar`. Não deixe duas versões do mod.
3. Mantenha arquivos de configuração/dados existentes. Guard, autenticação e outros mods não precisam ser substituídos por esta entrega.
4. Inicie primeiro em uma cópia de teste. Confira `/raidadmin eventos`, comandos com conta sem OP e um ciclo de vitória/captura/resgate com seu modpack completo antes de levar à produção.
5. Para rollback de código, pare o servidor e restaure o JAR anterior. Não restaure um backup antigo de recompensas por cima de prêmios já resgatados, pois isso pode permitir repetição.

## Código e construção

O repositório possui fontes antigas e versões completas recentes nos JARs. O build usa o JAR completo 0.1.29 fixado em `releases/zorykai/v0.1.29-receipts.1/` e aplica adições auditadas, em vez de substituir a classe principal por fontes antigas. `tools/PatchEventHooks.java` valida todos os pontos de integração; `tools/package_events.py` registra hashes e classes preservadas no arquivo `.audit.json`.

Novos arquivos: `RaidEvents`, `RaidEventPlan`, `RaidEventState`, `RaidEventRewards`. Alterações no journal limitadas a sorteio/quantidade e campos de snapshot; lógica de recibos/resgate preservada. Pontos adicionados no lifecycle original: seleção automática, decoração do boss, consumo atômico do plano, conclusão da primeira etapa e IVs na criação do alvo de captura. Fonte e testes acompanham a entrega.

Construir com JDK 21: `./gradlew build`. A distribuição final é o JAR **Zorykai** em `build/libs/`, não o intermediário Shirokai. Fixtures de integração são separadas e não devem ser instaladas em produção.

Consulte `TESTES.md` da entrega para resultados executados e limitações. Nenhum teste automatizado garante ausência total de bugs, especialmente com todas as integrações do servidor de produção.


## Comandos visíveis e sugestões

A configuração está em `/raidadmin evento`, a escolha em `/raidadmin proxima` e o início em `/raidadmin iniciar`. Consultar detalhes usa `/raidadmin eventos`. Todos exigem `shirokai.raids.admin` e ficam fora da árvore enviada a jogadores comuns. O comando público de eventos é apenas `/raid votar [número]`; os demais comandos públicos de raid, entrada, saída e recompensas continuam existentes.

Use TAB depois de `/raidadmin evento ` para listar eventos. Cada subcomando sugere opções reais: shiny mostra porcentagens, IVs lista 1–6, drop/token mostram multiplicadores, elemental mostra tipos e aliases em português, dupla sugere as duas raridades, votação sugere espécies elegíveis. `/raidadmin iniciar` sugere raridades e IDs dos bosses manuais já cadastrados. A sintaxe antiga `/raidadmin iniciar rayquaza` continua funcionando. Não é necessário colocar aspas em `10%` nem em `cobblemon:charizard`.

## Chat e alternância da boss bar

Todas as mensagens novas usam `chat.prefixo` do `messages.json` existente. Não usam mais o prefixo literal `[Zorykai Raid]`. Configurar, trocar e consultar eventos gera resposta privada ao staff, sem uma mensagem global para cada ajuste. O início de uma raid especial tem um único aviso extra curto; o anúncio normal do boss continua. Votação anuncia abertura, vencedor ou cancelamento, sem repetir por tick. O aviso da segunda etapa da dupla não se repete toda vez que o intervalo de segurança reinicia.

A barra existente alterna 4 segundos de aviso e 8 segundos do texto normal; com vários eventos, mostra um por vez a cada ciclo. Próxima raid: `✦ ZORYKAI RAID | Evento Chuva de Shinies (10%) em 1h 59min 59s`. Raid ativa: `✦ ZORYKAI RAID | Evento Chuva de Shinies (10%) | Termina em ...`. A votação mostra o seu próprio prazo; a segunda etapa usa o intervalo correto ou informa que aguarda captura/retorno.

Não cria outra barra nem altera cor, progresso ou espectadores da barra original. A barra de captura não é modificada. A barra global de próxima raid continua respeitando `automaticRaids`; a barra de boss ativo continua sendo mostrada aos participantes conforme as regras existentes. Nos intervalos normais, o componente/título original é devolvido sem alteração, inclusive suas cores e configuração.

Tempos configuráveis em `events.json`: `eventBarSeconds` (2–30s) e `normalBarSeconds` (2–60s). Os campos são opcionais nos arquivos existentes. Use `/raidadmin evento reload` após editar. Templates adicionais opcionais em `messages.json`: `bossbar.evento.proximo`, `bossbar.evento.ativo` e `bossbar.evento.aguardando`, com `{evento}` e `{tempo}`. Não é preciso apagar nem substituir o arquivo atual para obter os padrões novos.

## Intervalo dos held items

Por padrão, um held item competitivo atribuído ao jogador fica bloqueado na próxima vitória em que ele tiver contribuição elegível e pode voltar a ser sorteado na participação seguinte. Exemplo: raid A ganhou Choice Band; B não sorteia Choice Band para esse jogador; C pode sortear novamente. Outro jogador não consome esse intervalo; sair/reconectar ou reiniciar não apaga o histórico. Raids canceladas, perdidas ou sem contribuição elegível não contam como vitória de recompensa.

A regra é por jogador e ID de item. Pode ganhar outro held item enquanto Choice Band está bloqueado. Cada held protegido tem no máximo uma unidade por raid, sem duplicar entre recompensa geral e TOP 3 ou entre V2 e legado. Aplica-se também aos itens das tabelas legadas por peso: se o resultado for um held bloqueado, ele é descartado desse sorteio, sem criar uma pendência falsa nem refazer os demais prêmios. Não garante um substituto para um held bloqueado.

Gemas, berries, candies, fragmentos, dinheiro, tokens e demais recompensas não recebem esse intervalo. A lista padrão protege Choice Band/Specs/Scarf, Leftovers, Life Orb, Focus Sash, Rocky Helmet, Assault Vest, Eviolite, Weakness Policy e outros helds competitivos comuns. Addons ou itens adicionais podem ser incluídos por ID em `heldItems`.

Configuração em `rewards-v2/integrations.json`: `heldItemCooldownRaids` (padrão 1, aceita 0–10) e `heldItems` (lista de IDs). Adicione os campos ao arquivo existente, preservando `enabled`, `legacyItems`, limites e demais opções; não substitua o arquivo inteiro por um trecho parcial. Use `/raidadmin rewardreload` para próximas raids. Zero desativa a espera entre raids, mantendo o limite de uma unidade por held na mesma raid.

O histórico fica no journal do `state.json`, salvo junto com o sorteio, antes do resgate manual. A proteção se aplica aos sorteios novos desta versão. Não remove itens já recebidos nem recalcula recompensas antigas pendentes, que continuam com os mesmos valores e recibos. Os candidatos de rerrolagem de cosmético duplicado não incluem helds protegidos, evitando uma entrega posterior contornar o intervalo.
