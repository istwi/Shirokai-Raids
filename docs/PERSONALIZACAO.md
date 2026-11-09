# Mensagens, bosses e agendamento

## Textos editáveis

O mod gera `config/shirokai-raids/messages.json` na inicialização. Edite os valores dos textos, mantendo as chaves. Exemplo de personalização parcial:

```json
{
  "comandos.bloqueados": "Você está na raid! Use /raid sair para voltar.",
  "raid.retorno": "Você voltou ao seu local anterior!",
  "raid.spawn": "O boss {boss} apareceu! Abra /raid para participar.",
  "raid.aviso": "Faltam {tempo} para a próxima raid.",
  "raid.aviso_singular": "Falta {tempo} para a próxima raid.",
  "bossbar.titulo": "{boss} | {vida}/{vida_maxima} HP | {tempo}"
}
```

O catálogo completo inclui chat, erros de participação, nomes e descrições dos menus, prefixo e BossBar. O prefixo dos anúncios usa `chat.prefixo` com `{texto}`. As variáveis das mensagens principais são `{boss}`, `{tempo}`, `{vida}`, `{vida_maxima}`, `{jogadores}`, `{limite}` e `{quantidade}`, conforme o texto original. Outras chaves em `textos.*` controlam rótulos e mensagens fixas. Logs técnicos e textos nativos do Minecraft/Cobblemon não são traduzidos pelo catálogo.

Use `/raidadmin reload` fora de uma raid ativa para carregar as alterações; reabra menus que já estavam abertos. Chaves omitidas usam o padrão incluído no mod. Seus arquivos existentes não são sobrescritos; novas chaves usam o padrão mesmo sem serem adicionadas ao arquivo. JSON inválido ou valores que não sejam texto são recusados, preservando a configuração carregada anteriormente. Os textos são literais; não executam comandos nem interpretações de código.

## Adicionar bosses à lista

Cada arquivo `config/shirokai-raids/bosses/*.json` registra um boss. Copie o exemplo `docs/examples/bosses/mewtwo.json` para `config/shirokai-raids/bosses/mewtwo.json`. Ajuste:

- `id`: identificador único, como `mewtwo`.
- `species`: espécie existente no Cobblemon instalado.
- `name`: nome exibido no jogo.
- `hp`, `level`, `scale`, `damageMultiplier`: atributos do boss.
- `rewards`: nome de uma tabela existente em `rewards/<nome>.json`.

Depois use `/raidadmin reload` e `/raidadmin listar`. Arquivos com IDs duplicados, valores inválidos ou tabela de drops ausente impedem o reload; a lista anterior é preservada. Espécies devem existir na instalação do Cobblemon. Adicionar JSON não instala espécies novas no jogo.

## Boss aleatório a cada duas horas

No `config/shirokai-raids/config.json`, mantenha:

```json
{
  "intervalSeconds": 7200,
  "automaticRaids": true
}
```

Esses já são os valores padrão. O intervalo é contado a partir do início do evento; com raid ativa, outro boss não aparece. No momento do evento automático, o mod sorteia com chances iguais um dos bosses carregados da pasta `bosses`. Repetições entre eventos são possíveis. Não existe uma segunda lista para sincronizar.

Configure a arena com `/raidadmin setspawn`. Para ajustar um servidor já configurado, fora de evento ativo, use `/raidadmin intervalo 7200` e `/raidadmin automatico true`. Isso salva a configuração e recalcula o próximo evento. Editar o JSON e recarregar preserva a ocorrência já agendada; o novo intervalo vale para os agendamentos seguintes. Para recalcular imediatamente, use o comando de intervalo. Não altere `state.json` manualmente. `/raidadmin iniciar <id>` continua permitindo escolher um boss manualmente.
