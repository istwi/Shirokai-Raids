# Mensagens, bosses e agendamento

## Textos editáveis

O mod gera `config/shirokai-raids/messages.json` na inicialização. Edite os valores dos textos, mantendo as chaves. Exemplo de personalização parcial:

```json
{
  "comandos.bloqueados": "Você está na raid! Use /raid sair para voltar.",
  "raid.retorno": "Você voltou ao seu local anterior!",
  "raid.spawn": "O boss {boss} apareceu! Abra /raid para participar.",
  "bossbar.proxima": "&b&lPróxima raid &r&f| {tempo}",
  "bossbar.titulo": "{boss} | {vida}/{vida_maxima} HP | {tempo}"
}
```

O catálogo completo inclui chat, erros de participação, nomes e descrições dos menus, prefixo e BossBar. O prefixo dos anúncios usa `chat.prefixo` com `{texto}`. As variáveis das mensagens principais são `{boss}`, `{tempo}`, `{vida}`, `{vida_maxima}`, `{jogadores}`, `{limite}` e `{quantidade}`, conforme o texto original. Outras chaves em `textos.*` controlam rótulos e mensagens fixas. Logs técnicos e textos nativos do Minecraft/Cobblemon não são traduzidos pelo catálogo.

Use `/raidadmin reload` fora de uma raid ativa para carregar as alterações; reabra menus que já estavam abertos. Chaves omitidas usam o padrão incluído no mod. Seus arquivos existentes não são sobrescritos; novas chaves usam o padrão mesmo sem serem adicionadas ao arquivo. JSON inválido ou valores que não sejam texto são recusados, preservando a configuração carregada anteriormente. Os textos não executam comandos. No chat e nos títulos das BossBars, use `&0`–`&9` e `&a`–`&f` para cores, `&l` para negrito, `&o` para itálico, `&n` para sublinhado, `&m` para riscado, `&k` para ofuscado e `&r` para reset. Também são aceitos `§` e hexadecimal como `&#FFAA00`. Uma nova cor limpa os estilos anteriores; por exemplo, `&6&lShirokai-RAID&r &f{texto}` em `chat.prefixo`. Os placeholders continuam com chaves e os códigos desconhecidos permanecem literais.

## Adicionar bosses à lista

Cada arquivo `config/shirokai-raids/bosses/*.json` registra um boss. Copie o exemplo `docs/examples/bosses/mewtwo.json` para `config/shirokai-raids/bosses/mewtwo.json`. Ajuste:

- `id`: identificador único, como `mewtwo`.
- `species`: espécie existente no Cobblemon instalado.
- `name`: nome exibido no jogo.
- `hp`, `level`, `scale`, `damageMultiplier`: atributos do boss.
- `rewards`: nome de uma tabela existente em `rewards/<nome>.json`.

Depois use `/raidadmin reload` e `/raidadmin listar`. Arquivos com IDs duplicados, valores inválidos ou tabela de drops ausente impedem o reload; a lista anterior é preservada. Espécies devem existir na instalação do Cobblemon. Adicionar JSON não instala espécies novas no jogo.

## Contagem regressiva no topo

Com arena configurada e raids automáticas habilitadas, todos os jogadores online veem uma BossBar azul com `{tempo}` até a próxima raid. A barra esvazia proporcionalmente ao intervalo e atualiza a cada segundo, inclusive para quem acabou de entrar. Personalize `bossbar.proxima` no JSON. Os antigos avisos periódicos no chat (`raid.aviso`, `raid.aviso_singular`, `warningsSeconds`) não são mais emitidos. O anúncio de nascimento permanece.

Ao nascer o boss, a contagem sai e a barra de HP existente aparece para os participantes dentro da arena. Ela acompanha o dano global e mantém o alerta vermelho abaixo de 20%. Desativar o automático, perder a persistência ou encerrar o servidor remove a contagem. O boss é sorteado ao nascer, por isso o título de espera não anuncia uma espécie antecipadamente.

## Boss aleatório a cada duas horas

No `config/shirokai-raids/config.json`, mantenha:

```json
{
  "intervalSeconds": 7200,
  "automaticRaids": true
}
```

Esses já são os valores padrão. O intervalo começa quando o boss é derrotado; o tempo de combate não consome a espera até a próxima raid. Se o evento expirar ou for cancelado, o intervalo completo começa nesse encerramento. Com raid ativa, outro boss não aparece. No momento do evento automático, o mod sorteia com chances iguais um dos bosses carregados da pasta `bosses`. Repetições entre eventos são possíveis. Não existe uma segunda lista para sincronizar.

Configure a arena com `/raidadmin setspawn`. Para ajustar um servidor já configurado, fora de evento ativo, use `/raidadmin intervalo 7200` e `/raidadmin automatico true`. Isso salva a configuração e recalcula o próximo evento. Editar o JSON e recarregar preserva a ocorrência já agendada; o novo intervalo vale para os agendamentos seguintes. Para recalcular imediatamente, use o comando de intervalo. Não altere `state.json` manualmente. `/raidadmin iniciar <id>` continua permitindo escolher um boss manualmente.
