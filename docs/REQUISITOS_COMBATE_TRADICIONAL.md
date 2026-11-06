# Shirokai Raids — Substituir combate automático por batalhas tradicionais multiplayer

Quero modificar o sistema de combate do Shirokai Raids, no repositório `istwi/Shirokai-Raids`.

A implementação atual da versão `0.1.0-dev` utiliza ataques automáticos em tempo real.

**Não quero mais esse sistema.**

Quero que o Shirokai Raids utilize as batalhas tradicionais do Cobblemon 1.8.3, com a interface normal de seleção de movimentos, mas permitindo que vários jogadores enfrentem o mesmo World Boss simultaneamente.

## 1. Comando /raid

Quando uma raid estiver ativa, executar:

`/raid`

Deve teleportar o jogador diretamente para a arena onde o boss apareceu.

Requisitos:

- Teleportar para um local seguro próximo ao boss.
- Não teleportar dentro de blocos ou do próprio boss.
- Manter o boss visível e acessível.
- Se não existir raid ativa, informar quanto tempo falta para a próxima.
- Não permitir teleporte para dimensões ou locais inválidos.
- Permitir que vários jogadores utilizem o comando simultaneamente.
- Manter `/raid tempo`, `/raid top`, `/raid dano` e `/raid recompensas`.

## 2. Iniciar batalha pressionando R

Após chegar à arena, o jogador deve conseguir iniciar uma batalha contra o boss utilizando a interação normal do Cobblemon.

Fluxo:

1. Jogador utiliza `/raid`.
2. É teleportado para a arena.
3. Encontra o Pokémon boss no mundo.
4. Envia seu Pokémon normalmente.
5. Pressiona R, ou utiliza a tecla de batalha configurada no cliente Cobblemon, para desafiar o boss.
6. A batalha tradicional começa.
7. A interface normal de movimentos aparece.

Não quero que o jogador precise utilizar `/raid atacar` para executar ataques automáticos.

Preserve o funcionamento padrão dos controles do Cobblemon sempre que possível.

## 3. Interface tradicional do Cobblemon

Quero que o jogador utilize os recursos normais da batalha:

- Quatro movimentos do Pokémon.
- Seleção manual de movimentos.
- PP.
- Precisão.
- Efetividade de tipos.
- STAB.
- Status e habilidades compatíveis.
- Troca de Pokémon.
- Uso da equipe.
- Animações de batalha existentes.

Utilizar o mecanismo de batalha do Cobblemon/Showdown em vez de criar uma fórmula simplificada de dano.

Não substituir os movimentos por ataques automáticos com cooldown.

## 4. Vários jogadores contra o mesmo boss

**REQUISITO PRINCIPAL**

Vários jogadores precisam conseguir enfrentar o mesmo World Boss ao mesmo tempo.

Exemplo:

- Jogador A entra com Garchomp.
- Jogador B entra com Mewtwo.
- Jogador C entra com Zacian.
- Todos escolhem seus movimentos normalmente.
- Todos causam dano ao mesmo Rayquaza.
- O Rayquaza possui uma única vida global compartilhada.

Se o boss tiver 10.000.000 de HP:

- Jogador A causa 100.000 de dano.
- Jogador B causa 150.000 de dano.
- Jogador C causa 200.000 de dano.

O HP global deve passar para 9.550.000.

Os jogadores devem conseguir entrar enquanto a raid estiver ativa, mesmo que outros já estejam lutando.

## 5. AUDITORIA OBRIGATÓRIA — Batalhas multiplayer do Cobblemon

Antes de modificar o código, investigue profundamente a API e o código-fonte oficial do Cobblemon 1.8.3.

Verifique:

1. Como o Cobblemon cria batalhas.
2. Como o Pokémon Showdown gerencia participantes.
3. Como funcionam batalhas multiplayer.
4. Quantos jogadores podem participar de uma batalha.
5. Se é possível adicionar participantes depois do início.
6. Se o mesmo Pokémon pode participar de múltiplas batalhas.
7. Como interceptar dano calculado pelo Showdown.
8. Como modificar ou sincronizar HP entre batalhas.
9. Como o Cobblemon detecta o fim de uma batalha.
10. Como impedir que a morte do boss encerre incorretamente outras batalhas.
11. Como preservar movimentos, status, habilidades e PP.
12. Como impedir duplicação de dano ou recompensas.

Investigue duas arquiteturas:

**Alternativa A — Batalha multiplayer compartilhada**

Uma única batalha envolvendo vários jogadores contra um boss.

Utilizar essa arquitetura se a API suportar os requisitos necessários, incluindo entrada de participantes durante a raid.

**Alternativa B — Batalhas tradicionais sincronizadas**

Cada jogador utiliza uma batalha tradicional do Cobblemon, mas todas são vinculadas ao mesmo World Boss.

Um gerenciador central controla:

- HP global.
- Dano individual.
- Participantes.
- Estado do boss.
- Encerramento.
- Recompensas.

Cada batalha deve manter sua própria sessão válida, sem reutilizar indevidamente um mesmo Pokémon ou estado do Showdown.

Investigue como representar o boss em cada sessão sem criar inconsistências.

O dano calculado pelo Showdown deve ser convertido de maneira consistente para o HP global.

Não utilizar HP local das batalhas como fonte definitiva da vida global.

**Não presuma que qualquer uma dessas alternativas funciona.**

Implemente apenas uma solução tecnicamente validada contra a API real do Cobblemon 1.8.3.

Se nenhuma alternativa permitir preservar corretamente as batalhas tradicionais, documente o impedimento antes de substituir o sistema existente.

Não simule uma batalha tradicional usando uma interface falsa.

## 6. Vida compartilhada

Manter o sistema de HP virtual existente.

Exemplo:

Rayquaza com 10.000.000 HP.

Todos os jogadores atacam esse mesmo HP global.

O HP precisa:

- Ser atualizado corretamente.
- Nunca ficar negativo.
- Não sofrer dano duplicado.
- Permanecer sincronizado.
- Ser mostrado na BossBar.
- Continuar correto se um jogador sair.
- Encerrar todas as batalhas quando chegar a zero.

A BossBar deve continuar mostrando a vida global.

## 7. Contabilização de dano

Manter o sistema atual de contribuição individual.

Registrar o dano efetivamente aplicado ao boss pelo Pokémon de cada jogador.

O dano deve ser contabilizado mesmo quando vários jogadores estiverem em batalhas simultâneas.

Não contabilizar:

- Dano excedente ao HP restante.
- Dano duplicado.
- Ataques cancelados.
- Ataques que erraram.
- Dano que não foi efetivamente aplicado.

O ranking deve continuar funcionando com `/raid top`.

## 8. Comportamento do boss

O Rayquaza deve continuar sendo um único World Boss no mundo.

O boss pode enfrentar diferentes jogadores durante a raid.

Investigue e implemente uma política consistente para seus ataques, movimentos e condições de status.

Evite que efeitos aplicados por uma batalha provoquem inconsistências em outras.

Se determinadas mecânicas tradicionais não puderem ser compartilhadas corretamente, documente as limitações.

Quando o HP global chegar a zero:

1. Impedir novos ataques.
2. Encerrar as batalhas vinculadas à raid.
3. Registrar a derrota do boss.
4. Calcular a contribuição individual.
5. Distribuir as recompensas.
6. Remover o boss.
7. Encerrar a raid.

## 9. Recompensas

Preservar o sistema de recompensas baseado em dano.

Quanto maior a contribuição do jogador, melhores devem ser suas chances de receber recompensas raras.

Manter:

- Drops aleatórios.
- Faixas de contribuição.
- Recompensas individuais.
- Recompensas pendentes.
- Proteção contra duplicação.
- Persistência após desconexão.
- Recuperação após reinicialização.

## 10. Testes obrigatórios

Criar testes para:

- `/raid` teleportando até a arena.
- Início de batalha pela interação padrão do Cobblemon.
- Interface de movimentos tradicional.
- Três jogadores participando simultaneamente.
- Dano compartilhado entre participantes.
- HP global sincronizado.
- Jogador entrando durante uma raid em andamento.
- Jogador saindo da batalha.
- Pokémon derrotado durante a raid.
- Troca de Pokémon.
- Fim da raid por tempo.
- Derrota do boss.
- Encerramento correto das batalhas.
- Ranking de dano.
- Distribuição de recompensas.
- Prevenção de duplicação.

Realizar testes de integração com Cobblemon 1.8.3.

Se não for possível automatizar um teste real com três clientes, documentar o procedimento manual e não afirmar que a funcionalidade multiplayer foi validada.

## 11. Preservação do projeto

- Manter Minecraft 1.21.1.
- Manter Fabric.
- Manter Java 21.
- Manter Cobblemon 1.8.3.
- Preservar o mod ID `shirokai_raids`.
- Preservar configurações e dados existentes.
- Não modificar o GTS-Shirokai.
- Não publicar uma Release estável enquanto o combate multiplayer não estiver validado.

Atualizar a documentação para remover a descrição de ataques automáticos como comportamento definitivo.

Criar uma nova build de desenvolvimento no GitHub Actions.

Somente depois da implementação e validação, preparar uma nova versão oficial na aba Releases.

## 12. Critério de aceitação

Quero que três jogadores consigam:

1. Executar `/raid` e teleportar até o Rayquaza.
2. Enviar seus Pokémon.
3. Pressionar R para iniciar batalhas tradicionais do Cobblemon.
4. Escolher manualmente seus movimentos.
5. Atacar o mesmo boss simultaneamente.
6. Reduzir uma única barra de HP compartilhada.
7. Receber recompensas individuais baseadas no dano causado.

**O objetivo é ter a experiência de uma batalha normal de Pokémon, mas transformada em uma raid cooperativa multiplayer.**

Não considerar a funcionalidade concluída apenas porque o JAR compilou. Confirmar a viabilidade técnica e o funcionamento real.