# Catálogo automático — 0.1.17-dev

Com `catalog.json.enabled: true` (padrão), o agendamento escolhe Pokémon do catálogo carregado pelo Cobblemon. Não é necessário cadastrar cada um em `bosses/`. Os arquivos existentes continuam servindo para eventos manuais e podem ser mantidos; o modo automático também funciona com a pasta `bosses/` vazia.

## Quem pode aparecer

- Espécie implementada pelo Cobblemon, somente na forma padrão.
- Forma final: sem evolução seguinte. Arcanine e Gyarados entram mesmo tendo uma única pré-evolução. Charmander e Charmeleon ficam fora. Pokémon sem linha evolutiva podem entrar se cumprirem os demais filtros.
- Pelo menos 480 de soma dos atributos base e 85 no maior atributo ofensivo.
- Altura natural entre 1,2 m e 10 m; escala do boss é ajustada para dar presença na arena.
- Pelo menos dois golpes ofensivos adequados. Seleção prioriza força, atributo físico/especial, STAB, precisão e cobertura de tipos, usando golpes por nível, evolução e TM do próprio Pokémon. Não escolhe golpes futuros de nível acima do perfil, golpes de evento/legacy, autodestruição, golpes de recarga e outros condicionais problemáticos.
- A lista `blocked` exclui espécies indesejadas. O padrão também evita alguns Pokémon de aparência muito amistosa ou baseados em gimmicks, como Clefable, Togekiss, Audino e Ditto.

Força e altura são critérios objetivos para uma seleção de aparência imponente; não constituem uma avaliação artística individual de todos os modelos. A lista de bloqueio pode ser ajustada ao gosto do servidor. Não há geração de espécies inexistentes nem seleção indiscriminada de formas Mega, Gigantamax ou temporárias.

Na validação com Cobblemon 1.8.3 foram encontrados **212 candidatos**: 193 comuns e 19 especiais, distribuídos em 133 Normal, 47 Raro, 13 Épico, 13 Lendário e 6 Supremo. A quantidade acompanha os dados efetivamente carregados e pode mudar com datapacks/atualizações. [CSV do catálogo validado](examples/catalogo-validado-0.1.17.csv) é um relatório, não uma lista que o mod precisa carregar ou que você precise manter.

## Ciclo de 20 raids

As posições 1–19 sorteiam somente espécies comuns. A 20ª sorteia somente especiais. Depois o ciclo se repete: 21–39 comuns, 40 especial, etc.

São considerados especiais os rótulos nativos `legendary`, `mythical`, `ultra_beast` e `paradox`. Assim, o espaço especial pode trazer um lendário, mítico, Ultra Beast ou paradoxo elegível. Nenhuma dessas categorias entra nos outros 19 espaços. Dragonite, Tyranitar e outros pseudo-lendários são espécies comuns fortes, classificadas pela força, e podem aparecer fora da 20ª posição.

A sequência conta **inícios bem-sucedidos**, não vitórias. Derrota por tempo, cancelamento após nascer e raid concluída não devolvem a posição. Tentativa recusada antes do início não avança. Intervalo, duração e local da arena continuam definidos pela configuração já existente.

O contador `automaticRaidStarts` e os históricos ficam no `state.json`, persistidos no mesmo salvamento que registra o boss ativo. Reiniciar, recarregar, desconectar ou cancelar não zera a sequência. Um servidor atualizado de uma versão anterior começa o novo contador em zero; não se tenta adivinhar quantas raids antigas ocorreram. Uma raid perdida pelo agendamento durante downtime não é contada como nascimento.

Se não houver candidato para a 20ª posição, o evento é adiado com aviso no log. Não se coloca um comum no lugar nem se avança o contador. Não é uma chance de 5% a cada evento: há uma posição especial fixa a cada 20 inícios.

## Dificuldade e combate

A classificação considera soma dos atributos, maior ataque, velocidade e resistência. Espécies comuns recebem Normal, Raro ou Épico; especiais recebem Lendário ou Supremo. O rótulo `restricted` ou soma base de pelo menos 660 coloca um especial em Supremo. Um Pokémon comum com atributos altos nunca vira lendário por causa disso.

| Perfil | Nível | Outros IVs | EVs ataque/velocidade/HP | Turnos-alvo | Multiplicador de dano |
| --- | ---: | ---: | --- | ---: | ---: |
| Normal | 85 | 25 | 128/128/128 | 25 | 1,00 |
| Raro | 90 | 28 | 196/196/116 | 35 | 1,10 |
| Épico | 95 | 31 | 252/252/4 | 50 | 1,20 |
| Lendário | 100 | 31 | 252/252/4 | 65 | 1,35 |
| Supremo | 100 | 31 | 252/252/4 | 85 | 1,50 |

**O IV de Speed é fixo em 15 em todos os bosses de combate, automáticos e manuais.** Os valores de IV da tabela se aplicam aos outros cinco atributos. Isso não fixa o atributo final de Speed em 15: espécie, nível, EVs, natureza e efeitos de batalha também influenciam a velocidade.

O EV ofensivo e a natureza favorecem o maior ataque base: Adamant para físico e Modest para especial. O boss recebe até quatro ataques escolhidos do seu learnset, sem inventar golpes. Resistências, imunidades, habilidades, precisão e escolhas do jogador continuam funcionando no combate nativo.

Vida global inicial:

```
expectedPlayers × estimatedDamagePerTurn × targetTurns
× clamp(força_do_Pokémon / referência_do_perfil, 0.85, 1.15)
```

O padrão usa **10 participantes esperados**, dano médio estimado de 100 por turno e os turnos da tabela. Isso resulta aproximadamente em 21–29 mil HP nos Normais e 72–98 mil nos Supremos. `expectedPlayers` não limita entradas; é um parâmetro de calibração. Para uma comunidade que costuma levar 30 jogadores, mude-o para 30. Aumentar esse valor multiplica HP, sem mudar arbitrariamente a espécie ou o dano do boss.

É um balanceamento inicial baseado nos atributos e em uma estimativa de grupo, não uma simulação de todas as composições possíveis. Monitore duração, dano e derrotas no servidor e ajuste os perfis. Não foi medida capacidade para 100 clientes.

Os bônus de treino são do boss de combate. A fase de captura continua com a cópia privada nativa, dois minutos e as regras existentes; esses bônus não são automaticamente transferidos ao Pokémon capturado.

As recompensas modulares usam a dificuldade do boss gerado. O ID é `auto_<espécie>` (por exemplo `auto_arcanine`) e pode ser usado nos overrides de recompensas em `rewards-v2/bosses.json`. Os perfis também indicam a tabela de itens antiga em `rewards`, caso `legacyItems` continue ativado.

## Configuração e comandos

Arquivo: `config/shirokai-raids/catalog.json`. [Exemplo completo](examples/catalog.json).

- `enabled`: ativa sorteio do catálogo. Se desativado, o agendamento volta ao sorteio antigo dos bosses cadastrados; a regra do ciclo pertence ao modo de catálogo.
- `minimumBaseStats`, `minimumOffense`, limites de altura: filtros de força/porte.
- `namespaces`: por padrão apenas `cobblemon`; outras espécies precisam de dados e modelos compatíveis.
- `blocked`: IDs completos que não devem aparecer.
- `expectedPlayers`, `estimatedDamagePerTurn`: calibração de HP.
- `profiles`: os cinco conjuntos de nível, treino, turnos, dano, porte e tabela de itens.
- `recentNormal` e `recentSpecial`: históricos separados para reduzir repetições. Se o pool ficar pequeno demais, repete um candidato válido sem relaxar os filtros ou o ciclo.
- `shinyChance`: chance de shiny, de 0 a 1; padrão 0,01.

`/raidadmin reload` recarrega catálogo/configuração quando não há raid ativa e mantém o contador. Configuração inválida não substitui a anterior. O catálogo é consultado novamente a cada sorteio, acompanhando as espécies carregadas.

`/raidadmin catalogo` mostra quantos candidatos há em cada dificuldade e quantas raids faltam até o próximo especial. Consulta não consome posição.

`/raidadmin sortear` inicia o próximo evento do catálogo e **consome uma posição do ciclo**, como uma raid agendada. Serve para iniciar antes do horário e para testar.

`/raidadmin iniciar <id>` continua iniciando um boss cadastrado manualmente e não interfere na sequência automática. Todos os comandos administrativos mantêm a permissão `shirokai.raids.admin`.
