# Validação — Zorykai Raids 0.1.31-eventdisplay.1

Executada em 24/11/2026. SHA-256 do JAR final: `e1c2d4e8bb3963112985203842c58f41d96efb42707f537623e262912f8b7f16`.

Build Gradle/JDK 21 concluído com sucesso. **179 testes JUnit aprovados**, sem falhas, erros ou ignorados. **402 checks em seis fixtures Fabric aprovados**, todos carregando exatamente o JAR final acima com verificação de bytecode `-Xverify:all`.

| Fixture | Checks | Evidência |
|---|---:|---|
| EventDisplayIntegration | 40 | Sugestões reais do dispatcher para eventos, tipos, IVs, porcentagens, multiplicadores, raridades, dois parâmetros de dupla e espécies de votação. Root de administração nega jogador sem OP; os nós administrativos não existem mais em `/raid`; voto público continua permitido. Prefixo nativo e prefixo customizado respeitados. Mudanças de configuração não geram evento de broadcast público. Hooks reais dos títulos da barra de contagem e da barra ativa, alternância com tempo controlado, mesma referência do título normal, progresso/espectadores preservados. Comando antigo de iniciar boss manual preservado. Cooldown A/B/C para V2 e para tabelas legadas, quantidades unitárias de held e consumíveis mantidos. |
| EventsIntegration | 57 | Regressão dos nove eventos usando os comandos movidos para `/raidadmin`: seleção de raridade, shiny, IVs reais no boss e alvo privado de captura, snapshots, voto e cooldown, elemental, invasão, dupla aguardando captura e retorno, segurança/expiração e resgate manual. |
| EventsRestartIntegration | 10 | Três processos Java e dois SIGKILL. Não retoma sequência interrompida, preserva prêmios e retorno offline, mantém modificadores armados e histórico/IDs do cooldown de held items após reinício. |
| ItemRewardsIntegration | 252 | 34 tipos de itens da configuração existente, entrega, persistência/recibo, reload, offline e inventário cheio. Reservas incertas antigas preservadas. |
| ReceiptRecoveryIntegration | 18 | Confirmação de recibos salvos/em memória sem reinserção de item; ausência/corrupção mantém incerteza; confirmação de moeda não inferida por tag; atualização do menu e resgate manual mantidos. |
| RewardRetryIntegration | 25 | Sem reenvio automático para offline/inventário cheio; menu e pedido explícito de resgate funcionam com proteção por recibo. |

Os 13 testes unitários novos cobrem a alternância/rotação dos avisos, percentual fracionário, separação entre seleção de raridade e evento especial, quantidade de held com Enfurecido, bloqueio da próxima participação, liberação posterior, histórico serializado, jogador independente, idempotência de award, geral/TOP 3, cooldown configurável e limites.

## Limites

Servidores e entidades Cobblemon são reais; os jogadores são simulados. A alternância de 4/8 segundos e os intervalos de teste usam relógio controlado; não representam observação de um cliente Minecraft humano. A visibilidade de staff foi verificada por requisitos da árvore de comandos e execução negada sem permissão, além das sugestões do dispatcher, sem captura visual de um cliente.

Não foi alterado o servidor de produção nem carregado seu modpack completo com LuckPerms, Guard, EasyAuth, Impactor e Crates reais. Não houve ensaio de carga para dezenas de jogadores. Classes dos protocolos de autenticação/retorno, captura/inventário e recibos de itens foram preservadas ou receberam apenas os pontos de integração auditados já descritos. Isso reduz a área alterada, sem garantir ausência total de bugs.

Prêmios antigos não são apagados nem recalculados pelo cooldown. A regra começa nos novos sorteios desta versão, mantendo quantidade/recibo das pendências existentes. Helds adicionais de addons precisam estar na lista configurada para receber o intervalo.

`testes/` contém resultados JSON, XML JUnit e log do build. `AUDITORIA.json` registra hashes e classes preservadas. Fixtures e seus JARs não devem ser instalados em produção.
