# Validação — 24/11/2026

Artefato final: Zorykai-Raids-0.1.30-events.1-fabric-1.21.1.jar.
SHA-256: `347d0e9c623d1cd631059467cc32c8360a302be5c3a5b460fcc8758c34886160`.

## Resultados executados

- Build Gradle/JDK 21: **BUILD SUCCESSFUL**.
- JUnit: **166 testes, 0 falhas, 0 erros, 0 ignorados**; inclui 15 testes novos de probabilidades, limites, quantidades, snapshots e compatibilidade do estado.
- Servidores Fabric isolados: **360 checks aprovados**, todos carregando exatamente o SHA-256 final acima, com `-Xverify:all`, Minecraft 1.21.1, Cobblemon 1.8.3, Fabric API 0.116.6 e Loader 0.17.2.

| Fixture | Checks | Verificação |
|---|---:|---|
| EventsIntegration | 57 | Comandos reais, autorização administrativa, nascimento de entidades Cobblemon, shiny 100%, seis IVs perfeitos no boss e no alvo privado de captura, snapshot durante edição de próximo evento, vitória sem entrega automática, invasão, tipo elemental, voto único/troca/cooldown, bloqueio durante votação, dupla aguardando captura e retorno, expiração da primeira etapa, segunda etapa e restauração de padrões. |
| ReceiptRecoveryIntegration | 18 | Recibo persistido e somente em memória, inventário salvo, ausência/corrupção de prova, incerteza de moeda preservada, resgate manual e atualização do menu sem reenviar item. |
| ItemRewardsIntegration | 252 | 34 tipos de itens nativos, quantidades, journal/recibos, reload, jogador offline e capacidade de inventário. Reservas incertas de fragmentos preservadas. |
| RewardRetryIntegration | 25 | Sem tentativas automáticas para offline/inventário cheio, menu real e resgate com proteção por recibo. |
| EventsRestartIntegration | 8 | Três JVMs e dois SIGKILL: sequência interrompida não retomada, nenhum boss/captura fantasma, retorno offline e prêmio já sorteado preservados; modificadores armados sobrevivem a outro reinício sem duplicação. |

Os testes nativos usam entidades/servidor reais e jogador simulado. Na fixture da dupla, a passagem do intervalo é controlada no relógio de teste; não é um ensaio de várias horas com clientes conectados. Na votação, o cooldown também é exercitado com tempo controlado para testar a troca posterior sem bloquear o servidor de testes.

## Limites da evidência

Não houve acesso nem alteração ao servidor de produção. Não houve validação visual em cliente humano, captura com animação observada por um jogador real, ensaio de carga com muitos jogadores ou teste do conjunto completo Guard/EasyAuth/Impactor/Crates da produção. O engine de batalha tradicional e as classes de recuperação/autenticação/recibos nativos foram preservados pelo empacotador; isso reduz a área alterada, mas não substitui um teste com o modpack completo.

Os bônus de chance/quantidade atuam nas recompensas modulares. Tabelas legadas por peso mantêm o comportamento original. Não existe garantia de ausência total de bugs.

## Arquivos para conferência

`testes/` contém JSON de cada fixture, resumo, XML JUnit e log do build. `AUDITORIA.json` registra hashes e classes preservadas. O ZIP do código inclui fontes, testes, ferramentas de integração e o JAR base completo fixado; o JAR de integração é apenas uma fixture e não faz parte da distribuição de produção.
