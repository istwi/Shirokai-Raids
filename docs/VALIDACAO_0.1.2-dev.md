# Validação 0.1.2-dev — auditoria e protótipo

## Etapa 1: auditoria de arquitetura

Concluída a revisão da implementação 0.13.4 do Raid Dens, do JAR fornecido, do simulador Cobblemon 1.8.3 e da integração por tags do SkiesClear 1.1.2. Fontes, commits, hashes, licenças, alternativas e limitações estão em `COBBLEMON_MULTIPLAYER_AUDIT.md`.

Escolha: manter Shirokai independente, com coordenador autoritativo e adaptador mínimo de combate. Não portar registros de conteúdo, loader completo ou compartilhar o mesmo objeto Pokémon entre sessões. Não incorporar nem depender do JAR Raid Dens em produção.

## Etapa 2: protótipo parcial

Implementado `RaidCombatCoordinator`: admissão por token de sessão, limite de sessões ativas, sequência de eventos, HP inteiro, crédito limitado ao HP restante, contribuição preservada após saída e transição terminal única. Não faz IO nem chama callbacks dentro do monitor. Snapshots são imutáveis. Esta classe ainda não está ligada ao fluxo legado do servidor.

O adaptador deve fornecer eventos privados em ordem; eventos fora de ordem não podem simplesmente ser entregues ao coordenador. O controle de sequência impede replay de eventos já vistos; não autentica tráfego de cliente. Tokens e sequências devem ser gerados exclusivamente no servidor. Cura não está incluída neste ledger de protótipo; integrar cura requer definir denominador de contribuição e efeitos relacionados antes de habilitá-la.

`TraditionalBattleProbe` é uma fixture CLI com três motores de batalha reais do Showdown oficial, hospedados no Node. Cada sessão tem Pokémon e UUID próprios. O hook do JAR externo impede o KO local e gera os eventos privados que a fixture encaminha ao coordenador Java. A terceira sessão entra após duas já terem causado dano; uma sai e é encerrada isoladamente. No fim, o coordenador fecha as duas restantes e rejeita novas entradas.

O ensaio observou 2.062 pontos de dano solicitados, dos quais 2.000 foram efetivamente aplicados e creditados. Eventos repetidos não alteraram o resultado. Todos os jogadores que atacaram conservaram contribuição, incluindo quem saiu. A derrota ocorreu uma vez e todos os motores foram encerrados sem vitória local/pagamento.

Isso não exercita Graal, registries Fabric, pacotes Cobblemon, entidade pública, tecla R, interface cliente nem persistência. O HP interno das três simulações não foi sincronizado para o HP virtual global. O fluxo real de produção continua legado e não atende ao novo pedido de combate tradicional. Não instalar esta build esperando o novo World Boss completo.

## Comandos reproduzidos

```sh
# JDK 21 completo, Node e Python disponíveis.
bash gradlew --no-daemon build
python3 scripts/run_showdown_audit.py
python3 scripts/run_traditional_probe.py
python3 scripts/verify_jar.py --version 0.1.2-dev
```

O último script valida Java 21, metadados, ausência de dependência/namespace/conteúdo Raid Dens e ausência de classes de fixture no artefato distribuível, e escreve `build/libs/SHA256SUMS`.

Para reproduzir com o JAR anexado em vez do oficial:

```sh
python3 scripts/run_raiddens_audit.py /caminho/raiddens.jar \
  --showdown /caminho/showdown-1.8.3.zip \
  --coordinator-classpath build/classes/java/main:build/classes/java/integration
```

No Windows, separar entradas de classpath com `;`. `run_traditional_probe.py` faz essa adaptação automaticamente, usa o Cobblemon resolvido pelo Gradle e baixa temporariamente o Raid Dens oficial com SHA256 fixado. Nenhum deles é instalado em `mods/` por esse comando.

## Resultados

- Build Linux com JDK Temurin 21.0.13: passou.
- 24 testes JUnit: passaram, zero falhas/erros/skips. Incluem sete testes novos do coordenador.
- Oito verificações históricas no simulador oficial: passaram.
- Oito verificações isoladas dos callbacks reais do JAR: passaram; contexto mockado.
- Seis verificações adicionais no motor oficial com o hook original: passaram (PP, troca, imunidade, isolamento, ausência de KO local e encerramento).
- Protótipo Java + três sessões Showdown: passou. Não são três jogadores Minecraft reais.
- JAR e checksum: validados localmente.
- CI Linux/Windows: workflow atualizado para executar também o protótipo. Execução remota e link só podem ser registrados após confirmação no GitHub.

O teste com 100 chamadas concorrentes ao ledger verifica apenas conservação do HP e transição única. Não mede sessões Showdown, TPS/MSPT, latência, capacidade do servidor ou 100 jogadores.

## Bloqueios externos observados

A preparação do servidor dedicado pelo script existente falhou: o proxy retornou HTTP 403 para `meta.fabricmc.net/v2/versions/loader`. O endpoint `api.github.com` também retornou Forbidden ao consultar o repositório, portanto não foi confirmado um run remoto dos Actions. Esses destinos precisam ser liberados nas configurações do ambiente; não foram alteradas nem publicadas configurações nesta etapa. O servidor descartável não tem arquivo EULA aceito; o início depende de autorização explícita para registrar esse aceite.

Esses bloqueios não invalidam os testes locais do simulador e do coordenador, mas impedem afirmar validação no runtime Minecraft nesta etapa.

## Pendências obrigatórias

1. Adaptador de produção Cobblemon/Graal, desafio normal e representação pública única, sem compartilhar Pokémon/entidade entre sessões comuns.
2. Semântica explícita e testes de HP interno, cura, recoil/drain, multihit, residual, efeitos de HP proporcional, KO e término sem XP/loot duplicado.
3. Teste ponta a ponta com três clientes (movimentos, PP, trocas, saída, entrada tardia e sincronização).
4. Etapas de arena pública/lobby configurável, tickets de chunk, persistência/recuperação, tag SkiesClear, captura e recompensas conforme o escopo vigente. O código existente é base a adaptar, não aceite dessas etapas.
5. Ensaios progressivos com 1, 3, 10, 25, 50 e 100 combatentes, com telemetria real.
6. Release estável somente após os critérios de aceite. Nenhuma v1.0.0 foi criada.

## Arquivos desta etapa

- `src/main/java/com/shirokai/raids/core/RaidCombatCoordinator.java` e seu teste JUnit.
- `src/integration/java/com/shirokai/raids/integration/TraditionalBattleProbe.java`.
- Scripts `audit_raiddens_effect.cjs`, `audit_raiddens_engine.cjs`, `traditional_battle_probe.cjs`, `run_raiddens_audit.py`, `run_traditional_probe.py` e validação de JAR.
- Workflow de desenvolvimento Linux/Windows, versão dev, README, escopo, auditoria e este relatório.
