# Validação 0.1.34-autocontrol.1 — 24/11/2026

JAR SHA-256: `5d1902d509084293ad9d659802d66caabc4b60cebc8a695eec7c22cdfadeeb11`.

Build Gradle/JDK 21 concluído. **189 testes JUnit aprovados**, sem falhas, erros ou ignorados. **100 checks em três fixtures Fabric aprovados**, todos com o JAR final acima e `-Xverify:all`, Minecraft 1.21.1 e Cobblemon 1.8.3.

| Fixture | Checks | Evidência |
|---|---:|---|
| AutomaticControlIntegration | 31 | TAB com ligar/pausar/status, execução por console e negação de todas as operações sem permissão; status e alterações sem mensagens públicas; pausa salva no arquivo, campos customizados/presets/percentual preservados; evento reservado e votação mantidos; oportunidade ainda não sorteada descartada; reload mantém pausa; ligar salva e não cria sorteios retroativos; raid real ativa preservada; conclusão enquanto pausado não cria oportunidade; ligar repetidamente não cancela uma oportunidade legítima; erro real de escrita com diretório de configuração sem permissão não confirma pausa nem altera estado; permissões restauradas; comando repetido corrige flag em disco alterada sem reload; percentual continua 1%. |
| AutomaticEventsIntegration | 51 | Regressão da automação: chance fixa/snapshot, espera por captura e retorno, evento habilitado apenas, sem raid extra, anúncio único do Arceus, sem reprocessamento após 100 chamadas, prioridade manual, desativação de tipos e cancelamento respeitados, votação automática, Desafio Duplo e término natural versus cancelamento administrativo. |
| EventsRestartIntegration | 18 | Três JVMs e dois SIGKILL. Ativa e pausa antes da primeira queda; confirma pausa carregada e salva ativação antes da segunda; confirma ativado depois. Mantém recompensas, retorno offline, modificadores futuros, histórico de held items e marcador de sorteio; não repete boss/captura/resultado nem sequência interrompida. |

A comparação com 0.1.33 comprova que mudaram apenas `RaidEvents`, `RaidEventCommands` e a versão em `fabric.mod.json`. Todas as outras entradas são idênticas byte a byte: cálculo da chance fixa, sorteio, estado, combate, captura, autenticação/Guard, retorno, inventário, journal, recibos e cooldown de held items.

## Limites

Os servidores/entidades são reais e os jogadores simulados. Não houve cliente humano, modpack completo da produção, medição de carga ou alteração ao servidor de produção. A falha de escrita foi provocada somente no diretório do servidor isolado de teste, com restauração das permissões. Não foram simuladas todas as combinações de falha entre gravação de configuração e de estado. A ordem do controle é conservadora: pausar grava o desligamento antes de descartar a oportunidade; ligar elimina uma oportunidade antiga antes de gravar a ativação.

Os 167 checks da 0.1.33 pertencem à entrega anterior e não foram somados como execução extra desta versão. Não há garantia de ausência total de bugs. JSON de cada fixture, XML JUnit e log do build estão em `testes/`; a comparação está em `COMPARACAO-0.1.33.json`.
