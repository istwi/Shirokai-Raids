# Validação da build 0.1.1-dev

## Escopo e resultado

A migração para batalhas tradicionais multiplayer **não foi implementada nem validada**. A entrega contém auditoria de viabilidade, teleporte seguro para a arena, atualização dos critérios de Release e testes reproduzíveis. O combate automático anterior permanece somente como legado provisório, conforme a condição de não substituí-lo por uma alternativa não comprovada.

## Verificações executadas

- JDK 21, Minecraft 1.21.1, Fabric Loader 0.17.2, Fabric API 0.116.6+1.21.1 e Cobblemon 1.8.3.
- `gradlew build`: passou, incluindo compilação da fixture separada de integração.
- 17 testes JUnit executados, zero falhas/erros: os 13 anteriores e quatro da busca de destino seguro.
- Oito verificações no simulador oficial embarcado passaram: composição MULTI, rejeição de nova equipe em vaga ocupada, protocolo p1–p4, ausência de comando sethp tipado, dano/PP reais, isolamento de HP entre sessões, faint/win local e mensagens privada/pública de dano.
- `scripts/verify_jar.py --version 0.1.1-dev`: passou; metadados, versão, classes Java 21 e checksum conferidos.
- `scripts/setup-cloud.sh` e `scripts/prepare-test-server.sh`: passaram. As configurações e o estado persistente do servidor de teste anterior foram comparados por SHA-256 antes/depois e permaneceram idênticos byte a byte. O JAR antigo foi preservado em previous-mods com checksum antes da atualização.

## Integração real de teleporte

Uma fixture separada, fora do JAR distribuível, iniciou um servidor dedicado com Cobblemon real em mundo descartável. Ela executou os comandos reais com FakePlayer, criou um Rayquaza e verificou:

- `/raid` mudou a posição para dentro da arena, sobre piso sólido, sem colisão com blocos ou com o boss.
- `/raid tempo` informou estado sem mover o jogador.
- Teleporte repetido imediatamente foi recusado pelo cooldown.
- Após remover o piso da arena e aguardar o cooldown, `/raid` recusou o teleporte e preservou a posição original.
- Após encerrar a raid, `/raid` informou o agendamento sem teleportar.

O primeiro teste encontrou um erro de precisão: somar altura float à coordenada inteira arredondava a base da caixa de colisão para dentro do piso em Y=200. O cálculo foi corrigido para double e o teste completo passou sem relaxar as verificações.

O resultado é gravado em `teleport-integration-result.json` no mundo descartável, contendo `passed: true` e `realClients: 0`. FakePlayer é ferramenta de teste do Fabric, não um cliente humano e não prova batalha multiplayer.

Reprodução no ambiente cloud, após build e preparação do servidor de teste com EULA já aceita:

```sh
bash scripts/run-teleport-integration.sh
```

O script cria um diretório novo em /workspace/shared, preserva o mundo anterior, usa o JAR de produção remapeado e inspeciona um resultado novo. A fixture requer `-Dshirokai.integration=true`, encerra o servidor ao terminar e não deve ser instalada em produção.

## Não executado / não comprovado

- Três clientes reais, tecla R, interface de movimentos, entrada tardia e sessões de combate com HP global.
- Migração de PP/status/habilidades, troca/desmaio em raid tradicional e distribuição de recompensa desse novo modelo.
- Teleporte entre dimensões com cliente humano.
- Execução da fixture dedicada em Windows. O CI compila a fixture e roda JUnit/auditoria em Windows/Linux; seu resultado remoto deve ser consultado na execução correspondente, separadamente dos testes locais.

Não há aceite de Release estável. Os impedimentos estão detalhados em COBBLEMON_MULTIPLAYER_AUDIT.md e o roteiro de aceite atual em TESTE_DE_ACEITACAO.md.
