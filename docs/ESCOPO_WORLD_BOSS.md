# Shirokai Raids — escopo vigente

Este documento registra o pedido atual e prevalece sobre os requisitos históricos de combate automático em `REQUISITOS.md`.

- Mod independente para Fabric, Minecraft 1.21.1, Java 21 e Cobblemon 1.8.3.
- Preservar a base e o histórico do Shirokai Raids; Raid Dens é referência técnica, não dependência de produção.
- Não registrar, incorporar ou distribuir itens, blocos, receitas ou geração de mundo do Raid Dens: cristais, chaves, shards, Raid Home, Raid Pouch e derivados ficam fora do mod, inclusive desativados ou escondidos no criativo.
- O fluxo é por comandos, agendamento, spawn físico do World Boss e arena pública configurada. Não exigir itens para iniciar ou participar.
- Não gerar cristais, dimensões de raid, portais, estruturas ou arenas temporárias. Não remover blocos ou cristais antigos de outros mods automaticamente.
- Batalhas reais do Cobblemon/Showdown, iniciadas pela interação normal, movimentos escolhidos manualmente, PP, trocas e equipes. Nenhum ataque automático como substituto.
- Instalação somente no servidor: funcionar com o cliente Cobblemon existente, sem exigir Shirokai Raids no cliente ou pacote de desafio próprio.
- Sessões independentes com objetos Pokémon próprios, um boss público visível e HP global autoritativo. Entrada tardia e saída isolada.
- `maxConcurrentPlayers` configurável entre 1 e 100, padrão desejado 100. O teto é de 100 jogadores efetivamente em batalhas simultâneas, não 100 inscritos com poucos combatentes. Recusar novas admissões quando lotado; liberar a vaga ao terminar a sessão, sair ou desconectar. Não encerrar as demais batalhas para abrir vaga. A capacidade operacional precisa ser comprovada nos ensaios progressivos; o valor de configuração não é evidência de desempenho.
- `/raid`, tempo, top, dano, recompensas e sair; comandos administrativos para spawn, lobby, informações, início, parada, configuração e status.
- Captura proibida. Recompensas sorteadas em duas etapas (raridade e item), com chances por contribuição; entrega direta de itens existentes do Minecraft, Cobblemon ou outros mods configurados. Nenhum item intermediário próprio.
- Proteger apenas bosses identificados por raid/UUID com a tag `shirokai_raid_boss`; documentar as exclusões correspondentes no SkiesClear.
- Persistência, recuperação, liberação de chunks e pendências de inventário, com limites de atomicidade documentados.
- Releases oficiais apenas após validação funcional e de capacidade. Compilação e testes de unidade não comprovam o aceite final.

O código legado ainda contém combate automático. Sua existência não significa cumprimento do novo fluxo. A substituição só deve ser habilitada quando houver um caminho de combate tradicional validado.
