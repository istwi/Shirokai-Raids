# Zorykai Raids 0.1.30-events.1

Fabric Minecraft 1.21.1, Java 21, Cobblemon 1.8.3. Parte da distribuição completa 0.1.29-receipts.1, preservando o nome Zorykai, retorno/autenticação, captura privada, batalha e mensagens de dano. Não instala nem altera o servidor de produção.

Branches conferidas em 24/11/2026: `main` (`ca6f4bc`, a mais recente), `fix/raid-return-spawn`, `fix/raid-bugs` e `zorykai`. A base é o JAR completo 0.1.29 da main, não as fontes antigas da raiz.

## Como cada evento funciona

Todos os comandos administrativos exigem `shirokai.raids.admin` (OP nível 2 sem LuckPerms). No console, omita a barra. Eventos são manuais e valem para a próxima raid; não há agenda recorrente. O catálogo automático deve estar habilitado.

| Evento | Exemplo | Comportamento e limites |
|---|---|---|
| Chuva de Shinies | `/raid evento shiny 10%` | Próximo boss tem 10% de chance de shiny; a captura privada usa o mesmo resultado. Não aumenta encontros selvagens nem garante captura. Aceita 0–100%. |
| Genética Perfeita | `/raid evento ivs 3` | Pelo menos 3 atributos com IV 31 no boss e em cada alvo privado de captura. Seleção fixa por evento, sem rerrolagem ao abrir a captura. Não reduz atributos que já eram 31. Aceita 1–6. |
| Caçada Recompensada | `/raid evento drop 2` | Multiplica por 2 a chance dos itens e fragmentos modulares: 15% vira 30%, com teto 100%. Não duplica a quantidade de fragmentos. No TOP 3, multiplica a probabilidade combinada dos dois testes, sem tornar cosméticos/moedas mais prováveis. |
| Fortuna de Tokens | `/raid evento token 2` | Multiplica por 2 a quantidade final de tokens Impactor, respeitando os limites de entrega. Não multiplica dollars. Inclui eventual compensação em tokens de cosmético duplicado, sem aplicar duas vezes. |
| Invasão Lendária | `/raid evento invasao` | Próxima raid sorteia categoria lendário/supremo, inicialmente 80/20; só categorias com espécie elegível participam. Mantém filtros do catálogo e não cai silenciosamente para normal. |
| Raid Elemental | `/raid evento elemental fogo` | Filtra o tipo primário ou secundário da espécie. Aceita os 18 tipos em inglês e aliases em português, como água, elétrico, fada e dragão. Se combinado com raridade impossível, recusa a alteração. |
| Votação da comunidade | `/raid evento votacao charizard gyarados tyranitar` | Duas ou três espécies elegíveis, voto pelo comando `/raid votar 1`, `2` ou `3`. Um voto por UUID; pode trocar a cada 2s. Repetir a mesma opção não grava de novo. Dura 60s por padrão; empate/zero votos usam desempate determinístico. Vencedor fica reservado para a próxima raid. Não inicia imediatamente. |
| Boss Enfurecido | `/raid evento enfurecido` | Dano causado pelo boss ×1,5 desde o início. Na vitória: quantidade dos itens modulares ×1,5 (arredondada para cima), tokens ×1,5 (parte inteira), fragmentos e dollars com quantidade original. Nenhum prêmio por atingir fases de HP; não permite farmar uma fase repetidamente. |
| Dupla Ameaça | `/raid evento dupla lendario supremo` | Duas raids completas em sequência. Sem argumentos, lendário → supremo. Uma arena/boss por vez; cada vitória tem seu próprio sorteio e resgate manual. Captura, animação e retornos da primeira precisam terminar antes da contagem de 30s para a segunda. |

**Tabelas antigas:** bônus de recompensa afetam as tabelas modulares. As tabelas `legacyItems` usam pesos e quantidade de sorteios, não porcentagem independente por item; permanecem com suas regras originais. Para ter uma única tabela de prêmios com bônus, migre a configuração para as recompensas modulares e use `legacyItems=false`; esta atualização não muda esse campo automaticamente nem apaga prêmios antigos.

Multiplicadores de drop/token aceitam valores de 1 a 10, inclusive decimais. Exemplo de combinação: token 2 + Enfurecido = 3× tokens, sempre limitado por `maximumAmount`. Quantidade de fragmentos nunca recebe bônus de quantidade. Se as integrações modulares estiverem desativadas, comandos de bônus de recompensa são recusados.

## Seleção e consulta

```
/raid proxima epico
/raid iniciar epico
/raid iniciar
/raid eventos
/raid evento cancelar
/raid evento reload
```

`proxima` reserva a raridade; não inicia imediatamente. `iniciar` sem argumento inicia o plano anunciado; com raridade, escolhe essa categoria sem perder os demais modificadores. Aceita normal, raro/rara, epico/épico, lendario/lendário e supremo/suprema. O comando antigo de iniciar um boss manual não ignora uma reserva de evento: use o novo comando.

Alterações afetam somente o próximo boss. Cada raid recebe uma cópia própria do plano e dos bônus. Os comandos anunciam o plano no chat; quem conectar depois também recebe a informação. `/raid eventos` consulta sem alterar nada. Os comandos de evento não são adicionados à lista de comandos gerais liberados dentro da raid: só a consulta e a votação recebem essa exceção, preservando os filtros de autenticação existentes.

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
  "duoMaximumWaitSeconds": 900
}
```

Use `/raid evento reload` após editar esse arquivo. Voto: 15–600s; intervalo: 10–300s; espera: 300–7200s. Duração de uma votação já aberta fica fixada no seu encerramento salvo. Mudanças no intervalo/limite de espera valem para sequências ainda aguardando. Dados operacionais ficam no `state.json` existente; não os apague nem edite com o servidor ligado. Configuração inválida suspende eventos e novos inícios automáticos até correção e reload/reinício, sem alterar recompensas existentes.

## Instalação e rollback

1. Pare o servidor e faça backup de `config/shirokai-raids/` e `<mundo>/data/shirokai-raids/`.
2. Remova o JAR anterior de Raids e coloque somente `Zorykai-Raids-0.1.30-events.1-fabric-1.21.1.jar`. Não deixe duas versões do mod.
3. Mantenha arquivos de configuração/dados existentes. Guard, autenticação e outros mods não precisam ser substituídos por esta entrega.
4. Inicie primeiro em uma cópia de teste. Confira `/raid eventos`, comandos com conta sem OP e um ciclo de vitória/captura/resgate com seu modpack completo antes de levar à produção.
5. Para rollback de código, pare o servidor e restaure o JAR anterior. Não restaure um backup antigo de recompensas por cima de prêmios já resgatados, pois isso pode permitir repetição.

## Código e construção

O repositório possui fontes antigas e versões completas recentes nos JARs. O build usa o JAR completo 0.1.29 fixado em `releases/zorykai/v0.1.29-receipts.1/` e aplica adições auditadas, em vez de substituir a classe principal por fontes antigas. `tools/PatchEventHooks.java` valida todos os pontos de integração; `tools/package_events.py` registra hashes e classes preservadas no arquivo `.audit.json`.

Novos arquivos: `RaidEvents`, `RaidEventPlan`, `RaidEventState`, `RaidEventRewards`. Alterações no journal limitadas a sorteio/quantidade e campos de snapshot; lógica de recibos/resgate preservada. Pontos adicionados no lifecycle original: seleção automática, decoração do boss, consumo atômico do plano, conclusão da primeira etapa e IVs na criação do alvo de captura. Fonte e testes acompanham a entrega.

Construir com JDK 21: `./gradlew build`. A distribuição final é o JAR **Zorykai** em `build/libs/`, não o intermediário Shirokai. Fixtures de integração são separadas e não devem ser instaladas em produção.

Consulte `TESTES.md` da entrega para resultados executados e limitações. Nenhum teste automatizado garante ausência total de bugs, especialmente com todas as integrações do servidor de produção.
