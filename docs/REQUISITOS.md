# Projeto: Shirokai Raids — Mod de World Boss para Cobblemon

Quero desenvolver um mod chamado **Shirokai Raids**, destinado ao servidor Minecraft Shirokai.

O objetivo é criar um sistema de raids cooperativas em tempo real, onde um Pokémon extremamente poderoso aparece no mundo a cada 2 horas e vários jogadores podem enfrentá-lo simultaneamente utilizando seus próprios Pokémon.

O boss deve possuir uma única vida compartilhada entre todos os participantes.

Ao derrotá-lo, os jogadores recebem recompensas aleatórias de acordo com sua contribuição de dano.

## 1. Ambiente e compatibilidade

Desenvolver para:

- Minecraft Java 1.21.1
- Fabric Loader
- Fabric API compatível
- Java 21
- Cobblemon 1.8.3
- Servidor dedicado multiplayer

Nome: Shirokai Raids

Mod ID: `shirokai_raids`

Pacote Java sugerido: `com.shirokai.raids`

Criar um projeto Gradle independente, com código-fonte organizado, documentação e workflow do GitHub Actions.

Não modificar diretamente o código-fonte do Cobblemon.

Não utilizar APIs de outras versões sem verificar compatibilidade.

## 2. AUDITORIA OBRIGATÓRIA DA API DO COBBLEMON 1.8.3

Antes de implementar o combate, examine o código-fonte ou o JAR de desenvolvimento correspondente exatamente ao Cobblemon 1.8.3.

Investigue:

1. `PokemonEntity` e seu ciclo de vida.
2. Criação e invocação de Pokémon no mundo.
3. Acesso aos dados de espécie, nível, IVs, atributos e movimentos.
4. Como a vida de `PokemonEntity` se relaciona com a vida do objeto `Pokemon`.
5. Como o Cobblemon processa dano recebido por entidades Pokémon.
6. Como detectar Pokémon pertencentes a jogadores.
7. Como impedir capturas de um Pokémon especial.
8. Como impedir batalhas tradicionais contra o boss.
9. Como impedir despawn e alterações indevidas do boss.
10. Como utilizar animações existentes dos Pokémon.
11. Eventos e callbacks do Fabric para dano, entidades e ticks.
12. Como manter o sistema compatível com as batalhas tradicionais do Cobblemon.

Investigue também os conflitos com os estados `isBattling`, `isBusy` e `battleId`.

Não utilize identificadores de batalha falsos nem manipule diretamente o registro de batalhas tradicionais para simular uma raid.

**Importante:** as batalhas tradicionais do Cobblemon utilizam Pokémon Showdown. Quero um sistema de combate em tempo real, independente desse mecanismo.

Se a API pública não oferecer uma função necessária, documente a limitação e avalie uma integração mínima e segura, evitando Mixins invasivos.

Não invente métodos ou classes. Verifique suas assinaturas reais e compile contra as dependências corretas.

Antes da implementação, gere um documento `COBBLEMON_API_AUDIT.md` com os métodos confirmados, os pontos de integração e as limitações encontradas.

## 3. Sistema de aparição de raids

Uma raid deve começar automaticamente a cada 2 horas.

Configuração padrão:

- Intervalo: 7200 segundos.
- Duração máxima: 20 minutos.
- Uma raid ativa por vez.
- Local de aparição configurado por administrador.
- Pokémon escolhido aleatoriamente entre bosses cadastrados.
- Avisos globais antes do início.

Avisos configuráveis:

- Faltam 15 minutos.
- Faltam 5 minutos.
- Falta 1 minuto.
- Raid iniciada.
- Boss derrotado.
- Raid encerrada por tempo.

Persistir o horário da próxima raid em disco para sobreviver a reinicializações.

Não executar verificações pesadas a cada tick.

Após reinicialização, não criar múltiplas raids acumuladas pelo tempo em que o servidor ficou desligado.

O comportamento após reinício deve ser configurável e previsível.

## 4. Sistema de World Boss

O boss deve aparecer fisicamente no mundo como um Pokémon do Cobblemon.

Características:

- Espécie configurável.
- Forma configurável.
- Nível configurável.
- Shiny opcional.
- Tamanho visual aumentado, quando tecnicamente possível.
- Nome personalizado.
- Vida extremamente alta.
- Vida compartilhada.
- Proteção contra captura.
- Proteção contra despawn natural.
- Proteção contra batalhas tradicionais.
- Proteção contra remoção acidental por jogadores.
- Limite de distância da arena.
- Retorno ao centro caso saia da área permitida.

O boss não deve ser um Pokémon comum que possa ser capturado, recolhido ou adicionado à equipe de um jogador.

Criar um gerenciador que identifique o boss por UUID e ID da raid, sem depender exclusivamente do nome exibido.

Evitar que jogadores possam duplicar bosses ou invocar entidades adicionais explorando comandos.

## 5. Combate em tempo real

**Este é o sistema mais importante do mod.**

Não quero batalhas por turnos.

Quero que vários jogadores possam utilizar seus Pokémon simultaneamente contra o mesmo boss.

Implementar um mecanismo de combate cooperativo próprio.

Fluxo desejado:

1. O jogador entra na área da raid.
2. Envia um Pokémon da sua equipe para o mundo.
3. Seleciona o boss como alvo usando uma interação ou comando.
4. Seu Pokémon começa a atacar o boss automaticamente em tempo real.
5. Os ataques possuem intervalos e dano calculado no servidor.
6. Outros jogadores podem atacar o mesmo boss simultaneamente.
7. Todo dano válido reduz a mesma vida compartilhada.
8. O boss pode atacar os Pokémon participantes.
9. O combate continua até o boss morrer ou o tempo terminar.

A primeira versão deve priorizar estabilidade e funcionamento real no servidor.

Não é obrigatório reproduzir perfeitamente todas as mecânicas competitivas do Pokémon Showdown.

Entretanto, o sistema deve considerar, quando viável:

- Nível do Pokémon atacante.
- Attack e Special Attack.
- Defense e Special Defense do boss.
- Tipos dos Pokémon.
- Poder do movimento utilizado.
- STAB.
- Efetividade de tipos.
- Cooldown entre ataques.
- Distância máxima de ataque.

Criar um sistema próprio e documentado de cálculo de dano.

Movimentos que não podem ser implementados corretamente devem possuir tratamento explícito, sem fingir que todas as mecânicas do Cobblemon funcionam automaticamente.

Permitir inicialmente ataques automáticos baseados em movimentos ofensivos compatíveis.

Animações de ataque podem utilizar recursos existentes do Cobblemon, quando a API permitir.

Não permitir que jogadores usem ataques infinitos, sem cooldown ou com valores de dano enviados diretamente pelo cliente.

Toda validação e cálculo devem ocorrer no servidor.

## 6. Vida compartilhada e sincronização

Criar um `RaidHealthManager`.

A vida do boss deve ser controlada por um único estado autoritativo no servidor.

Configuração de exemplo:

- HP máximo: 10.000.000.
- HP atual: 10.000.000.
- Dano recebido de todos os jogadores reduz a mesma vida.

Não confiar exclusivamente nos corações padrão do Minecraft para representar milhões de HP.

Se necessário, implementar uma camada de HP virtual para o boss.

Garantir:

- Sem perda de dano entre ataques simultâneos.
- Sem HP negativo.
- Sem contagem duplicada.
- Sem múltiplas mortes do boss.
- Sem recompensas duplicadas.
- Sem inconsistência entre HP visual e HP real.

As alterações de estado devem acontecer de maneira segura na thread apropriada do servidor.

## 7. Habilidades do boss

Implementar uma estrutura de habilidades configuráveis.

Primeira versão:

- Ataque básico periódico.
- Ataque em área.
- Habilidade especial com cooldown.
- Mudança de comportamento conforme a porcentagem de HP.

Exemplo de fases:

- Fase 1: 100% a 70% de HP.
- Fase 2: abaixo de 70% até 30%.
- Fase 3: abaixo de 30%.

As fases podem alterar:

- Frequência dos ataques.
- Dano.
- Habilidades disponíveis.
- Efeitos visuais.
- Mensagens.

O boss deve priorizar Pokémon participantes e não causar destruição de blocos.

Não permitir explosões que destruam construções ou causem grief.

Os ataques devem ser previsíveis e possuir limites configuráveis.

## 8. Registro individual de dano

Criar um `RaidDamageTracker`.

Registrar por UUID do jogador:

- Nome exibido.
- UUID.
- Dano total causado.
- Quantidade de ataques válidos.
- Porcentagem de participação.
- Última participação.
- Pokémon utilizados.

O dano causado por um Pokémon deve ser atribuído ao seu proprietário.

Exemplo:

Boss com 1.000.000 de HP.

Jogador A: 450.000 de dano = 45%.

Jogador B: 300.000 de dano = 30%.

Jogador C: 200.000 de dano = 20%.

Jogador D: 50.000 de dano = 5%.

O sistema deve contabilizar somente o dano realmente aplicado ao boss.

Não contar dano excedente depois que a vida chegar a zero.

Não contar dano contra Pokémon comuns.

Não aceitar informações de dano enviadas pelo cliente como valores confiáveis.

Evitar que jogadores aumentem artificialmente sua participação por meio de cura, dano inválido ou ataques duplicados.

## 9. Sistema de recompensas

Criar um `RaidRewardManager`.

Quando o boss for derrotado, distribuir recompensas individuais de acordo com a contribuição.

As recompensas devem ser aleatórias, com chances configuráveis.

Sugestão de categorias:

- Comum.
- Incomum.
- Rara.
- Épica.
- Lendária.

Criar faixas de participação configuráveis:

- 1% a 4,99%: participação baixa.
- 5% a 14,99%: participação normal.
- 15% a 29,99%: participação alta.
- 30% ou mais: participação excepcional.

Essas faixas são valores iniciais de exemplo.

Jogadores com maior contribuição devem ter melhores chances ou maior quantidade de recompensas.

Entretanto, jogadores com contribuição menor, mas válida, também devem poder receber itens.

Permitir configurar:

- Itens por categoria.
- Quantidade mínima e máxima.
- Chance ou peso de cada item.
- Número de sorteios.
- Requisitos mínimos de participação.
- Multiplicadores por faixa de dano.
- Limite máximo de recompensas.
- Recompensas específicas por boss.

Suportar IDs de itens de Minecraft e Cobblemon.

Não utilizar recompensas monetizadas ou exigir pagamentos para participar.

Impedir que uma raid pague duas vezes ao mesmo jogador.

Caso o inventário esteja cheio, preservar as recompensas para resgate posterior.

Caso o jogador desconecte após participar, preservar sua contribuição e recompensas.

Criar persistência segura para recompensas pendentes.

## 10. Interface e informações

Criar BossBar mostrando:

- Nome do boss.
- HP atual.
- Porcentagem de vida.
- Tempo restante.

Criar mensagens e informações em português brasileiro.

Comandos:

`/raid` — Informações da raid.

`/raid entrar` — Participar ou ir para a raid, conforme configuração.

`/raid sair` — Encerrar participação.

`/raid atacar` — Selecionar o boss como alvo com o Pokémon enviado.

`/raid top` — Ranking de dano.

`/raid dano` — Consultar dano individual.

`/raid tempo` — Tempo até a próxima raid.

`/raid recompensas` — Consultar e resgatar recompensas pendentes.

Comandos administrativos:

`/raidadmin iniciar <boss>`

`/raidadmin parar`

`/raidadmin setspawn`

`/raidadmin reload`

`/raidadmin status`

`/raidadmin listar`

Implementar sugestões de comandos e mensagens de erro apropriadas.

Se alguma sintaxe precisar mudar por motivos técnicos, documentar.

## 11. Permissões

Compatibilidade com LuckPerms através do sistema de permissões disponível no Fabric.

Permissões sugeridas:

- `shirokai.raids.use`
- `shirokai.raids.join`
- `shirokai.raids.attack`
- `shirokai.raids.rewards`
- `shirokai.raids.admin`

Jogadores comuns devem conseguir participar por padrão.

Comandos administrativos devem ser restritos.

Não permitir que jogadores comuns iniciem ou encerrem raids arbitrariamente.

## 12. Configurações

Criar:

`config/shirokai-raids/config.json`

`config/shirokai-raids/bosses/`

`config/shirokai-raids/rewards/`

Cada boss deve ter seu próprio JSON.

Exemplo de informações configuráveis:

- ID.
- Espécie.
- Forma.
- Nível.
- HP.
- Nome.
- Shiny.
- Escala visual.
- Duração.
- Habilidades.
- Fases.
- Recompensas.
- Localização.
- Raio da arena.
- Multiplicadores de dano.
- Limite de participantes.

Validar os arquivos e mostrar mensagens claras caso algum ID ou valor seja inválido.

Não sobrescrever configurações personalizadas durante atualizações.

## 13. Desempenho

O mod será utilizado em um servidor público.

Otimizar para múltiplos participantes.

Requisitos:

- Não procurar entidades em todos os mundos a cada tick.
- Manter referências e identificadores da raid ativa.
- Evitar cálculos repetidos desnecessários.
- Usar intervalos configuráveis para IA.
- Limitar partículas e efeitos.
- Limitar frequência de atualizações visuais.
- Não bloquear a thread principal com gravações demoradas.
- Evitar carregamento descontrolado de chunks.
- Encerrar corretamente tarefas quando a raid terminar.
- Limpar referências após a remoção do boss.

Adicionar limites de segurança para impedir que centenas de ataques sejam processados indevidamente no mesmo instante.

## 14. Persistência e recuperação

Persistir:

- Horário da próxima raid.
- Raid ativa, se aplicável.
- Identidade do boss.
- HP atual.
- Participantes.
- Dano causado.
- Estado das recompensas.
- Recompensas pendentes.

Utilizar gravação segura e recuperação após falhas.

Evitar o problema de armazenamento no Windows que ocorreu anteriormente no GTS-Shirokai.

Não tentar abrir diretórios como arquivos.

Garantir compatibilidade com Windows e Linux.

Se ocorrer crash durante a distribuição de recompensas, não permitir duplicação após reinicialização.

## 15. Testes

Criar testes automatizados para:

1. Agendamento a cada 2 horas.
2. Uma única raid ativa.
3. Invocação e identificação do boss.
4. HP compartilhado.
5. Dano de múltiplos jogadores.
6. Atribuição de dano ao proprietário do Pokémon.
7. Ataques simultâneos.
8. Cooldowns.
9. Limite de distância.
10. Proteção contra captura.
11. Proteção contra batalhas tradicionais.
12. Fases do boss.
13. Encerramento por tempo.
14. Derrota do boss.
15. Ranking de dano.
16. Porcentagem de contribuição.
17. Recompensas aleatórias.
18. Recompensas pendentes.
19. Desconexão de participantes.
20. Reinicialização do servidor.
21. Proteção contra recompensas duplicadas.
22. Funcionamento no Windows.
23. Compatibilidade com Java 21.

Testes unitários não substituem testes reais dentro do Minecraft.

Quando possível, criar testes de integração com servidor dedicado Fabric.

## 16. Entrega

Quero um mod independente, compilado em JAR.

Nome do arquivo:

`Shirokai-Raids-1.0.0-fabric-1.21.1.jar`

Criar um workflow do GitHub Actions que:

- Configure Java 21.
- Compile o projeto.
- Execute testes.
- Valide os metadados Fabric.
- Disponibilize o JAR como artefato.
- Execute verificações no Windows e Linux quando possível.

Entregar:

1. Código-fonte completo.
2. JAR compilado.
3. Arquivos de configuração de exemplo.
4. Documentação dos comandos.
5. Documentação das permissões.
6. Documentação da arquitetura.
7. Relatório da auditoria da API do Cobblemon.
8. Instruções de instalação.
9. Lista de limitações conhecidas.

**Prioridade de implementação:**

Primeiro, desenvolver uma versão mínima funcional que permita invocar um boss, atacá-lo com Pokémon de múltiplos jogadores, compartilhar HP, registrar dano e distribuir recompensas.

Depois, adicionar habilidades avançadas, fases, efeitos visuais e interfaces mais elaboradas.

Não declarar que o combate em tempo real está funcionando apenas porque o projeto compilou.

Validar efetivamente a integração com o Cobblemon 1.8.3 e relatar qualquer funcionalidade que ainda precise de testes em servidor dedicado.

**Critério principal de aceitação:** três jogadores devem conseguir enviar seus Pokémon, atacar simultaneamente o mesmo Rayquaza no mundo, reduzir uma única barra de HP compartilhada e receber recompensas individuais proporcionais ao dano registrado, sem iniciar batalhas tradicionais por turnos.