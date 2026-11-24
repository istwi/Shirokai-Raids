# Validação 0.1.33-autoevents.1 — 24/11/2026

SHA-256 do JAR: `38d5ddec703ba338213d735e8f5c27a227e71aad25e94807a7d5a67572668fb6`.

Build Gradle/JDK 21 concluído. **189 testes JUnit aprovados**, sem falhas, erros ou ignorados. **167 checks nativos aprovados** em quatro fixtures Fabric com o mesmo JAR final, `-Xverify:all`, Minecraft 1.21.1 e Cobblemon 1.8.3.

| Fixture | Checks | Evidência |
|---|---:|---|
| AutomaticEventsIntegration | 51 | Hook real de conclusão salva oportunidade e configuração antes do anúncio; chance 0 e 100 controladas; captura/retorno bloqueiam o processamento; apenas preset habilitado é reservado; horário normal preservado sem spawn extra; um aviso Arceus após salvar; 100 chamadas seguintes não repetem sorteio/aviso; programação manual tem prioridade; todos os tipos desativados não geram eventos; snapshot de 1% continua usando 1% mesmo após reload para 100%; tipo desativado/cancelamento pendente respeitados; votação automática com espécies reais e voto público; Desafio Duplo aleatório só abre oportunidade no encerramento final; tempo esgotado com participação é elegível, cancelamento administrativo não; configuração fora de 0–100 rejeitada. |
| EventsIntegration | 61 | Regressão dos nove eventos, comandos, raridades, shiny, IVs no boss e alvo privado de captura, snapshots, votação, escolha elemental, Desafio Duplo com dois sorteios e esperas de captura/retorno, preservação do resgate manual. |
| EventDisplayIntegration | 42 | Prefixo normal/customizado, sugestões no TAB, separação staff/jogador, respostas de configuração privadas, boss bar real alternando em um minuto, título original/progresso/espectadores preservados, held item com intervalo A/B/C em V2 e legado, consumíveis inalterados. |
| EventsRestartIntegration | 13 | Três JVMs e dois SIGKILL. Mantém recompensas/retornos offline, modificadores armados e histórico de held items; não retoma sequência interrompida nem recria boss/captura. Marcador de sorteio automático processado e resultado sem evento sobrevivem, sem nova oportunidade/escalonamento no reinício. |

Dez testes unitários novos cobrem a fronteira de 1%, dez mil falhas sem aumento de chance, 0/100%, somente tipos habilitados, lista vazia, pesos condicionais, limites/valores de configuração, parâmetros do evento, snapshot persistente e cancelamento sem alterar probabilidade.

## Limites e comparação

Jogadores são simulados; servidores/entidades/arquivos de estado são reais. Tempos de captura/retorno e do Desafio Duplo foram controlados nas fixtures para conferir as portas de segurança. Não houve cliente humano, medição de carga, teste do modpack completo com todas as integrações ou alteração em produção.

A comparação com 0.1.32 comprova que a classe principal completa, autenticação/Guard, retorno, combate, captura, journal, recibos e política de held items continuam byte a byte idênticos. Mudam o runtime/estado/configuração dos eventos e entram as classes da automação. Os 113 checks da versão anterior pertencem àquela entrega; não são somados como se fossem testes extras atuais.

O reinício verifica o marcador de resultado já processado; não simula todas as janelas possíveis de queda entre salvar e anunciar. O fluxo usa o ID da raid como semente estável e salva o resultado antes da mensagem, evitando novas chances em tentativas repetidas. Isso não significa ausência garantida de bugs em produção.

As fixtures de regressão desativam a automação aleatória; a fixture de automação a habilita explicitamente com percentuais controlados, incluindo snapshot de 1%, para resultados reproduzíveis. O padrão de produção é ligado com 1% e os nove tipos habilitados, editáveis no bloco `automatic` de `events.json`.

Relatórios JSON, XML JUnit e log do build estão em `testes/`; hashes e classes alteradas em `AUDITORIA.json` e `COMPARACAO-0.1.32.json`.
