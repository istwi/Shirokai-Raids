# Arquitetura inicial

`ShirokaiRaids` integra callbacks Fabric e Cobblemon na thread do servidor. Somente o servidor seleciona os movimentos e calcula dano. Não há pacote de cliente que aceite dano informado externamente.

`RaidHealthManager` mantém HP virtual inteiro e limita o dano ao HP restante. A finalização remove a referência ativa e o boss. `RaidDamageTracker` mantém UUID, nome, dano aplicado, número de ataques, horário e Pokémon utilizados. A saída da arena não apaga contribuição. O cooldown fica associado ao UUID do Pokémon durante toda a raid, inclusive após sair e entrar novamente.

`CombatRules` implementa `floor((((2*nível/5+2)*poder*ataque/defesa)/50+2)*STAB*efetividade)`, limitado a 1.000.000 por golpe, respeitando imunidade. Attack/Defense ou Special Attack/Special Defense vêm do Pokémon real. `TypeChart` aplica a tabela básica de tipos da geração VI em diante. Apenas a lista configurada de movimentos é suportada; status, habilidades, clima, precisão, efeitos secundários, PP e outros efeitos Showdown não são simulados. Ataques são automáticos por cooldown.

O boss usa propriedade oficial uncatchable, UNBATTLEABLE, invulnerabilidade, persistência de entidade e UUID próprio. Eventos cancelam captura e batalhas tradicionais envolvendo a raid; nenhum battleId fictício é criado. O boss retorna ao centro ao sair do raio. Não são procuradas entidades de todos os mundos por tick nem criados tickets permanentes de chunks.

`RaidRewardManager` sorteia por peso. Faixas de 1%, 5%, 15% e 30% oferecem 1, 2, 3 e 4 sorteios, limitados pelo máximo configurado. As tabelas são específicas por boss. O estado `completed` e a inclusão dos itens na caixa postal são persistidos juntos antes de permitir resgates.

`JsonFiles` grava UTF-8 em arquivo temporário no mesmo diretório, força o conteúdo e substitui atomicamente. Um estado inválido desativa raids e é preservado. Não há reset silencioso. O estado fica no mundo em `data/shirokai-raids/state.json`, separado das configurações do mod.

A entrega ao inventário usa reserva persistida, inserção em slot vazio, salvamento dos jogadores e conclusão da reserva. Se ocorrer crash no intervalo entre os dois armazenamentos, `uncertain` exige conferência administrativa; nunca há replay automático. Isso evita duplicação automática, mas não equivale a uma transação distribuída entre o mundo e o arquivo de recompensas.

Em reinícios, a raid ativa é cancelada e bosses marcados são removidos ao carregar. Contribuições do último snapshot permanecem no arquivo para diagnóstico, sem recompensa de raid cancelada. SKIP_MISSED reagenda a próxima ocorrência a partir do reinício; START_ONCE permite uma única tentativa vencida, sem acumular ocorrências.

## Limitações que impedem v1.0.0

- Combate, captura, batalha tradicional e resgates ainda precisam do teste real com três jogadores.
- Contra-ataques iniciais atingem de um a três Pokémon conforme a fase. Habilidades avançadas de área, habilidade especial separada, avisos visuais, animações e fases totalmente configuráveis ainda não estão implementados.
- Escritas de estado e salvamento durante resgates são síncronos. Snapshots a cada dez segundos limitam frequência, mas é necessário avaliar latência sob carga e implementar fila durável antes de uso público intenso.
- Reservas ambíguas de resgate exigem reconciliação manual com backup e logs. Ainda não há comando administrativo de reconciliação.
- Configuração de HP/movimentos/recompensas tem limites básicos. Configuração avançada por habilidade e efeitos está pendente.
- LuckPerms usa API pública opcional, com fallback padrão para jogadores e nível OP 2 para administração; ainda não foi testado com LuckPerms instalado e contextos personalizados.
- O ambiente de desenvolvimento `gradlew runServer` apresenta falha de reflexão Kotlin nas classes remapeadas do Cobblemon. O build do JAR funciona; o servidor de produção com nomes intermediary foi iniciado e exercitado. Use o procedimento de servidor dedicado documentado.
