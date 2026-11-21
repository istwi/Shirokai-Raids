# Captura e recompensas externas — 0.1.23-capturefix.1

## Base preservada

Todas as branches remotas foram conferidas. `zorykai` (`55e17ba329a58205d2013ae02dd61c2c3be2c8d8`) é a mais recente; `main` está em `0375de86abdf828b412367cccf5860993254d6af`.

Os fontes do repositório ainda representam 0.1.20, mas o JAR mais recente é 0.1.22-damagechat.1. O arquivo enviado pelo usuário e o publicado no repositório são idênticos (SHA-256 `7edaf39537c447819d77e815f395ac10b73884b0868e7a2fc785b07104ce6254`). Recompilar integralmente os fontes antigos removeria funcionalidades recentes.

Por isso, `build` compila as alterações originais de captura/inventário e as aplica sobre a distribuição 0.1.22 completa. `tools/package_latest_fix.py` verifica o hash da base, preserva recursos/classes restantes e gera auditoria. `PatchLatestRaid.java` altera exclusivamente a chamada de validação de inventário em `tryChallenge`; exige exatamente uma ocorrência conhecida. Recuperação de retorno, autenticação, integração Guard e avisos privados de dano da versão publicada permanecem preservados. O candidato compilado dos fontes antigos fica em `build/intermediates`; o JAR instalável está em `build/libs/Zorykai-Raids-0.1.23-capturefix.1-fabric-1.21.1.jar`.

## Alterações

- Inventário vazio continua obrigatório para entrar. Depois da admissão, recompensas recebidas não impedem desafiar ou iniciar a captura.
- Bolas de raid permanecem na hotbar; o servidor envia explicitamente o slot selecionado. Hotbar cheia com espaço no inventário desloca um item sem apagá-lo.
- Selecionar uma Poké Ball comum recebida do quiz durante uma captura usa a cota da raid, conserva a recompensa e mantém a chance da raid. Não permite usar Master Ball externa para aumentar a chance nem ganhar tentativas.
- PC/healer podem ser utilizados durante a fase de captura. Quando necessário, encerram somente a batalha privada do próprio jogador; a cópia, prazo e bolas restantes permanecem. Uma bola em animação não pode ser interrompida dessa forma.
- Encerrar/perder a batalha privada não elimina imediatamente o direito de captura. O jogador pode curar/trocar e desafiar novamente, até o prazo original. Não há cura automática.

## Resultados

- Build concluído; [129 testes JUnit](unit-results.json), zero falhas.
- [79 verificações dedicadas](integration-result.json), aprovadas, zero clientes gráficos reais.
- [Controle negativo com 0.1.22](control-result.json): o mesmo roteiro falha ao iniciar captura após a recompensa real do quiz, exigindo inventário vazio. Essa falha é esperada e não representa falha do JAR corrigido.
- [Trechos de execução](runtime-evidence.txt); auditoria de entradas e hash do produto acompanham o JAR em `releases/zorykai/v0.1.23-capturefix.1`.

## Ensaio dedicado

Minecraft 1.21.1, Java 21.0.9, Fabric Loader 0.17.2, Fabric API 0.116.6+1.21.1, Cobblemon 1.8.3 e o CRZquiz 1.0.0 fornecido, sem alteração do JAR do quiz.

`CaptureFixIntegration` cria jogadores de servidor e executa batalhas reais no motor Graal/Showdown. Injeta uma pergunta sintética no quiz e chama o processamento de resposta do próprio mod, que executa seus comandos reais `/give`. Não é um ensaio de chat assinado por cliente. Os PCs/healers são blocos reais acionados pelo caminho normal de interação do servidor; a máquina cura ao longo dos ticks. A fixture usa o protocolo nativo de desafio e confirmação de turno, com colisão de projétil acionada de forma controlada.

São verificados prêmios antes/depois do início da captura, seleção da bola comum, hotbar cheia, conservação dos itens, PC, healer, equipe com apenas um Pokémon desmaiado, retomada após encerramento controlado da batalha privada, isolamento entre jogadores, captura única, falha, esgotamento, prazo e recuperação de retorno da versão mais recente.

**Limites:** zero clientes gráficos reais. O desmaio é colocado de forma controlada na transição; o encerramento posterior da batalha é controlado pela fixture. A disputa exata entre último desmaio e golpe final no boss compartilhado, no servidor do usuário, ainda precisa de validação com jogadores. O ensaio não comprova comportamento de outros mods de arena/proteção; o Guard foi inspecionado, mas não carregado neste conjunto de testes. Inventário completamente cheio antes da entrega inicial das bolas continua impedindo iniciar a captura, sem apagar recompensas; mantenha espaço livre. Não houve mudança da chance de captura ou da quantidade de bolas.

O quiz possui licença All-Rights-Reserved e não é redistribuído nem incorporado. A fixture usa reflexão somente para integração de testes, sem dependência de quiz no produto.

## Reproduzir

Build normal: Java 21 e `./gradlew build` (Maven oficial acessível). Em ambiente offline com os JARs oficiais disponíveis:

```sh
./gradlew --offline -I tools/local-deps.init.gradle \
  -PcobblemonJar=/caminho/cobblemon.jar \
  -PimpactorJar=/caminho/impactor.jar \
  -PcobblemonLibraries=/caminho/bibliotecas-cobblemon \
  -PimpactorLibraries=/caminho/bibliotecas-impactor build
```

As bibliotecas Cobblemon são os JARs internos extraídos recursivamente de `META-INF/jars`; as de Impactor são `adventure-*.jar` e `examination-*.jar`. São dependências de compilação/teste e não são incorporadas ao produto.

Para integração, use um servidor dedicado descartável com as dependências acima e adicione `build/integration/shirokai-raids-integration.jar` além do produto. Inicie com `-Dshirokai.integration=true -Dshirokai.integration.mode=capturefix`. A fixture encerra o servidor e grava `capturefix-integration-result.json`. Não instale a fixture em produção.
