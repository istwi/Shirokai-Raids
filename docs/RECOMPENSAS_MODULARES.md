# Recompensas gerais e especiais — 0.1.16-dev

Minecraft 1.21.1, Fabric, Java 21 e Cobblemon 1.8.3. Impactor e Shirokai Crates são opcionais: o JAR da raid não os inclui nem obriga sua instalação. Ausência de integração ou de moeda/crate cadastrada preserva a entrega pendente.

## Regras

O dano efetivamente aplicado é acumulado por UUID. Sair, voltar e desconectar não apaga a contribuição. O ranking usa dano decrescente e UUID crescente no desempate. Estar ausente na vitória não remove o direito às recompensas gerais/especiais; as regras de captura continuam exigindo presença.

Todos os jogadores com dano positivo e contribuição mínima recebem os sorteios gerais. A contribuição é dano/HP máximo global. Recompensas com `chance: 1` são fixas; as demais fazem um sorteio independente. Dollars e Tokens gerais usam uma quantidade determinística por dano, sem sorteio de valor. As demais recompensas sorteiam valores entre `minimum` e `maximum`, inclusive.

Apenas as posições originais 1, 2 e 3 podem concorrer aos especiais. Jogador inelegível não promove o quarto colocado. Cada posição tem uma chance de realizar um sorteio; depois, um prêmio é escolhido por `weight` e passa por sua própria `chance`. Pode não haver nenhum especial. No máximo um especial inicial por jogador e raid. Os padrões dão chances maiores ao TOP 1 que ao TOP 2, e ao TOP 2 que ao TOP 3.

## Quantidade das moedas gerais

Para `type: "currency"` em `general.json` (Dollars e Tokens), a quantidade é:

```
floor(minimum + (maximum - minimum) * contribuição)
```

A contribuição é o dano efetivo do jogador dividido pelo HP máximo global do boss, limitada entre 0 e 1. Não é normalizada pelo primeiro colocado. O valor é limitado por `maximumAmount`, com mínimo de 1. `baseMultiplier`, `damageMultiplier` e os multiplicadores de dificuldade/boss não se aplicam às moedas gerais; use `minimum`/`maximum` por pool para definir suas faixas. Não há um novo campo obrigatório de configuração.

Exemplo: com Dollars entre 5.000 e 15.000 e Tokens entre 5 e 25, contribuições de 1%, 10%, 25%, 50% e 100% rendem, respectivamente, 5.100/5, 6.000/7, 7.500/10, 10.000/15 e 15.000/25 (Dollars/Tokens). Na mesma raid e tabela, causar menos dano nunca aumenta a quantidade da mesma moeda; arredondamento e teto podem empatar valores. As chances configuradas de cada prêmio continuam valendo; com `chance: 1`, as moedas gerais são garantidas aos elegíveis.

Essa regra vale para todos os participantes elegíveis, inclusive a parte geral do TOP 3. O special continua com seleção e quantidade aleatórias, podendo mudar o total final recebido. Fragmentos, compensações e drops antigos mantêm seus cálculos. Pendências já sorteadas mantêm a quantidade salva e não são recalculadas. A prévia administrativa apresenta o valor determinístico para a contribuição simulada.

## Quantidade das demais recompensas

Para fragmentos e prêmios especiais, a quantidade final, inteira, é:

```
floor(base_sorteada * (baseMultiplier + contribuição * damageMultiplier)
      * multiplicador_da_dificuldade * multiplicador_do_boss)
```

O resultado é limitado entre 1 e `maximumAmount`; fragmentos têm limite adicional de 64 por entrega. Um multiplicador maior aumenta a recompensa esperada; sorteios individuais podem variar. `minimumContribution` e `maximumRank` de cada prêmio acrescentam condições à contribuição mínima global. `maximumRank: 0` aceita qualquer posição.

## Arquivos

Os sete arquivos ficam em `config/shirokai-raids/rewards-v2/`. São criados na primeira inicialização sem substituir arquivos existentes. Exemplos completos estão em `docs/examples/rewards-v2/`.

| Arquivo | Conteúdo |
| --- | --- |
| `general.json` | Pools de recompensas gerais |
| `special.json` | Pools de prêmios especiais |
| `difficulties.json` | Pools e multiplicador para normal, raro, epico, lendario e supremo |
| `top3.json` | Chances por dificuldade e posição, de 0 a 1 |
| `bosses.json` | Overrides por ID de boss |
| `duplicates.json` | Políticas separadas para títulos e partículas; compensação e limite de tentativas |
| `integrations.json` | Ativação, integração, contribuição mínima e limites de distribuição |

No JSON do boss, `difficulty` aceita `normal`, `raro`, `epico`, `lendario` ou `supremo`. Arquivos antigos assumem `normal`. Exemplo de `bosses.json`:

```json
{
  "values": {
    "rayquaza": {
      "difficulty": "lendario",
      "general": "lendario",
      "special": "lendario",
      "quantityMultiplier": 1.25
    }
  }
}
```

IDs de recompensa precisam ser únicos entre todos os pools gerais e especiais. As cinco dificuldades precisam existir. TOP 1 > TOP 2 > TOP 3 para chances positivas; zeros podem desativar as posições. Pools vazios desativam aquela categoria.

`integrations.enabled` controla o sistema novo. `legacyItems: true` mantém, adicionalmente, os drops de itens antigos de `rewards/*.json`; use `false` para distribuir somente os prêmios modulares. As tabelas antigas continuam disponíveis e suas pendências não são apagadas.

Os padrões gerais usam Dollars, Tokens e sorteio de fragmentos. Os especiais usam quantidades maiores dessas categorias. Os IDs padrão de crates correspondem aos exemplos publicados pelo Shirokai Crates: `shiny1`, `mega`, `lendaria` e `shiny2`. Ajuste-os para os IDs realmente cadastrados no servidor. `reference` de fragmento é ID da crate, não ID de item Minecraft.

## Administração e interface

- `/raidadmin rewardreload`: recarrega e valida apenas os sete arquivos novos, inclusive durante uma raid. Falha conserva a configuração anterior. Cada raid já iniciada mantém uma cópia de suas tabelas e limites; prêmios pendentes também mantêm valores já sorteados.
- `/raidadmin rewardstatus`: mostra contagens e pendências por tipo, além de até 20 transações incertas.
- `/raidadmin rewardreview <transação> confirmar <motivo>`: confirma, após conferir o provedor, que a entrega já ocorreu.
- `/raidadmin rewardreview <transação> repetir <motivo>`: só após comprovar que não houve entrega; devolve a transação incerta à fila.

Todos esses comandos exigem `shirokai.raids.admin` (ou OP nível 2 quando não há provedor de permissão). Não existe comando público para gerar moedas/prêmios.

As entregas são automáticas. Moedas podem ser creditadas mesmo offline. Fragmentos aguardam login, espaço no inventário e encerramento da captura. `/raid recompensas` mantém os itens antigos e adiciona um ícone informativo das pendências gerais/TOP 3. Avisos de entrega usam o prefixo SHIROKAI e `recompensas.modular_entregue` em `messages.json`.

## Integrações auditadas

**Impactor 5.3.5:** API pública `EconomyService`, consulta da moeda registrada, conta por UUID, `depositAsync` e `save`. Não cria moeda, saldo paralelo nem executa comandos. `impactor:dollars` e `impactor:tokens` precisam existir no Impactor; não são itens de inventário.

**Shirokai Crates 0.2.1:** adaptador opcional usa as APIs públicas `ShirokaiCrates.service()`, `snapshot()` e a fábrica `Tokens.create`. A fábrica carrega o secret original já existente e valida o fragmento com `Tokens.matches`. A raid não lê/copia a chave, não cria um secret e não falsifica NBT, nome ou lore. A integração exige serviço saudável e crate cadastrada. Não modifica o outro repositório.

**Cosméticos:** partículas permanentes ficam para o futuro, conforme solicitado. O Shirokai Core possui serviço de títulos, mas sua instância não está exposta por API pública; nenhum acesso a campos privados, comando presumido ou item fictício foi adicionado. Não há handlers de títulos ou partículas no JAR atual. Entradas desses tipos permanecem indisponíveis, sem conversão automática para moedas.

A infraestrutura de registro e políticas foi preparada e testada com provedores de teste: `RaidRewardHandler.Provider` pode registrar handlers pelo entrypoint Fabric `shirokai_raids:rewards`. O provedor consulta propriedade por UUID e deve conceder desbloqueio permanente no sistema responsável. A raid registra os desbloqueios confirmados por UUID, mas não substitui o sistema cosmético.

`SKIP` ignora uma duplicata; `COMPENSATE` usa o prêmio configurado; `REROLL` procura outra recompensa elegível do mesmo pool, sem aumentar raridade ou mudar geral para especial. Tentativas são limitadas. Sem alternativa, aplica a compensação configurada ou encerra sem prêmio se ela for `null`. A resolução e a quantidade são persistidas antes da entrega. A compensação usa seu intervalo fixo, limitado pelo teto global, sem voltar a aplicar multiplicadores.

## Persistência e recuperação

A outbox modular fica em `state.json`, no mesmo salvamento que marca a raid como concluída. Cada sorteio tem ID derivado de raid, UUID, categoria e ID da recompensa. Valores não são sorteados novamente no login, reload ou reinício. Logs registram transação, raid, UUID, categoria, sorteio, resolução, entrega e conferência. A fila processa um número limitado de transações por tick de distribuição, alterna entre pendências para que jogadores offline não bloqueiem os demais e serializa as entregas do mesmo jogador.

Fragmentos usam recibo salvo junto com o inventário no playerdata, com leitura de confirmação. Um reinício pode retomar a transação sem inserir os mesmos itens novamente. A entrega é integral; inventário cheio mantém a pendência. Recibos encerrados são limpos depois de confirmados no journal.

A API pública do Impactor 5.3.5 não aceita uma chave de idempotência fornecida pela raid para depósitos. Portanto, uma queda entre depósito/salvamento do Impactor e confirmação no journal **não pode ser resolvida automaticamente com garantia de pagamento único**. Essa transação vira `UNCERTAIN` e não é repetida. Use `rewardstatus` e os comandos de conferência após consultar o provedor. Isso evita duplicação; não promete uma transação distribuída atômica que a API não oferece. Exceções externas também preservam a transação incerta.

O limite de pendências é conferido antes de iniciar uma raid, reservando margem para os participantes simultâneos e conferindo a margem novamente para cada novo UUID admitido, para não descobrir falta de capacidade após a vitória. Novas configurações inválidas não substituem as anteriores.

## Verificação

`bash gradlew --no-daemon build` executa os testes de ranking, sorteios, limites, políticas e persistência. `scripts/run-reward-integration.sh` usa dois JVMs descartáveis com Impactor, LuckPerms e Shirokai Crates reais, verificando moedas offline, fragmentos assinados e recuperação. Dependências de teste têm checksums fixados em `scripts/reward-test-mods.json`; não são incluídas no mod da raid. A EULA do servidor de testes já foi aceita nesta sessão.
