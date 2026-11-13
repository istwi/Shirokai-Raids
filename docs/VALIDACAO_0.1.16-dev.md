# Validação — 0.1.16-dev

- Build Java 21 aprovado, 99 testes JUnit sem falhas, erros ou casos ignorados.
- Recompensas modulares: contribuição, ranking acumulado por UUID e desempate, TOP 3 exclusivo, nenhum especial garantido, quantidades, dificuldades/overrides, tetos, IDs únicos e snapshots persistidos.
- Políticas futuras de cosméticos testadas com provedores de teste: consulta de propriedade, desbloqueio por UUID, SKIP, COMPENSATE, REROLL limitado, restrição de raridade/escopo e fallback sem compensação. Não existe integração cosmética real nesta versão.
- Proteção da barreira de persistência, recuperação de recibos, pagamento incerto sem replay e conferência administrativa testados.
- Integrações reais: Impactor 5.3.5, LuckPerms 5.4.140 e Shirokai Crates 0.2.1 em dois JVMs. Primeiro processo: 9 verificações; segundo processo: 10 verificações. Dollars e Tokens creditados, moeda entregue offline, fragmento gerado e validado pela fábrica oficial, playerdata salvo, recuperação após reinício sem duplicação e fragmento pendente entregue no login.
- Regressão sem os mods opcionais: combate em três sessões controladas, golpes/PP/troca, contribuição, entrega parcial/offline/login; seis cenários de recuperação de recompensas antigas; 105 verificações de cliques de menu e 105 de operações pendentes.
- Captura: 51 verificações aprovadas, incluindo R manual, cópia privada ciano, BossBar, animação, mensagens, limite de bolas e prazo absoluto.
- Auditoria Showdown: 8 verificações; probe do motor e coordenador Java aprovados.

Resultados de integração preservados localmente:

- `/workspace/shared/raids-rewards-test.qVZtZXgk` (Impactor/Crates, dois processos)
- `/workspace/shared/raids-challenge-test.IERvNhpN` (regressão de combate/menu/operações)
- `/workspace/shared/raids-capture-test.JGPaL70v` (captura)

São servidores reais com FakePlayers, sem clientes Minecraft humanos. As interrupções de entrega foram preparadas no journal e seguidas de reinício real do JVM; não houve corte de energia nem crash aleatório. A aparência no cliente e a capacidade para 100 jogadores não foram medidas. A API do Impactor não fornece chave de idempotência para depósito; a janela incerta exige conferência, como descrito em RECOMPENSAS_MODULARES.md.
