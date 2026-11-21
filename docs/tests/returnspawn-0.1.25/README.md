# Retorno da raid com alternativa no spawn — 0.1.25-returnspawn.1

## Problema e comportamento

O JAR enviado pelo usuário é exatamente o 0.1.24-quizguard.1 publicado: SHA-256 `9709ece1b392b984d10dfaa3941d8c6a2aaff02ab459190b9cb1af9fcb81cb09`. A versão preservava o retorno pendente quando o destino antigo não tinha piso, estava obstruído, fora da borda, em dimensão indisponível ou tinha o teleporte recusado. Entretanto, não oferecia destino alternativo, e `/raid sair` mostrava apenas uma mensagem genérica. A imagem não permite determinar qual dessas condições ocorreu no servidor do usuário.

Agora o retorno normal continua sendo a primeira opção. Nas falhas de destino acima, o mod tenta um spawn alternativo pela mesma rotina de autenticação, validação, Guard, limpeza de captura, salvamento de playerdata e conclusão da participação. Somente depois de verificar o movimento e o salvamento são removidos o retorno pendente e o bloqueio de comandos.

A mensagem de `/raid sair` inclui o motivo efetivo da pendência. Autenticação incompleta, jogador morto/montado, outra batalha e persistência indisponível continuam exigindo que a condição seja resolvida; não são contornadas por um teleporte cego.

## Qual spawn é usado

1. Se existir `config/shirokai-raids/recovery-spawn.json`, usa esse destino explícito.
2. Caso contrário, com **Shirokai Essentials**, lê a mesma posição configurada para `/spawn` pelo `spawnKey` do jogador. Foi testado com Essentials **0.5.0-guard.1**, configurando `/setspawn` de verdade e mantendo o spawn vanilla em outro lugar para comprovar a prioridade.
3. Sem Essentials ou sem posição cadastrada nele, usa o spawn global do Overworld. Não usa a cama do jogador.

A posição exata é preferida. Se estiver fisicamente obstruída ou um pouco acima do piso, procura um ponto seguro em até quatro blocos na horizontal e três na vertical. Ao encontrar o primeiro ponto físico seguro, a rotina original ainda verifica o Guard. Uma recusa do Guard não faz o mod procurar ao redor para contornar a proteção.

O mod **não executa comandos arbitrários** nem aumenta permissões. Ler a posição configurada de `/spawn` não é executar o comando de Essentials com seu aquecimento/cooldown; trata-se de um retorno sistêmico de recuperação.

Se outro mod fornece seu `/spawn`, configure a posição explícita abaixo. Não foi inventada uma API genérica para todos os provedores de spawn.

```json
{
  "dimension": "minecraft:overworld",
  "x": 0.5,
  "y": 80.0,
  "z": 0.5,
  "yaw": 0.0,
  "pitch": 0.0
}
```

Use as coordenadas reais de um spawn seguro. Esse arquivo é opcional, não é criado sobre sua configuração automaticamente, e é lido quando necessário. O campo interno `raid` é preenchido pelo retorno atual, não precisa ser informado.

## Preservação da versão mais recente

Como os fontes de `ShirokaiRaids.java` do repositório continuam anteriores às versões binárias publicadas, o build preserva o JAR completo **0.1.24**. O empacotamento exige seu hash e aplica três pontos restritos em `ShirokaiRaids.class`: chamada ao adaptador de retorno nas falhas de destino, mensagem de pendência detalhada e substituição local do destino durante a tentativa alternativa. O mapa persistido de retornos não é trocado para o spawn antes da conclusão. Acrescenta `RaidReturnFallback.class` e atualiza a versão do manifesto.

Autenticação, persistência, Guard, API de recuperação, chat de dano, captura, inventário, bloqueio do quiz e recursos permanecem byte a byte da 0.1.24. A auditoria acompanha o produto. O adaptador usa reflexão somente para métodos/campos internos dessa distribuição fixada; versões futuras exigem nova validação. Não é uma recompilação integral dos fontes antigos.

A interface pública de EasyAuth `PlayerAuth.easyAuth$isAuthenticated()` foi conferida no [fonte oficial](https://github.com/NikitaCartes/EasyAuth/blob/stonecutter/src/main/java/xyz/nikitacartes/easyauth/interfaces/PlayerAuth.java); o código de autenticação do produto não foi alterado. Não houve login real em cliente com EasyAuth neste ensaio.

## Ensaios

Minecraft 1.21.1, Fabric Loader 0.17.2, Java 21.0.9, Cobblemon 1.8.3 e Impactor, em servidores descartáveis. Os jogadores são instâncias nativas simuladas de `ServerPlayer`, não clientes gráficos.

A fixture `ReturnSpawnIntegration` injeta uma falha real de gravação no arquivo de estado, verifica que o retorno antigo permanece reservado e que os comandos continuam bloqueados, restaura o arquivo e confirma a saída depois da correção. Esse erro de filesystem é esperado no teste, não é uma falha espontânea do produto. Também verifica destino original válido, dimensão ausente, posição sem chão, posição fora do limite e origem obstruída; prioridade do spawn configurado, busca próxima, destino alternativo também inseguro, reserva preservada na falha, mensagem detalhada, ausência de desvio para autenticação, liberação de comandos depois do retorno e posição gravada em playerdata real. O inventário de controle é preservado.

Um conjunto carrega **Essentials 0.5.0-guard.1 e Guard 0.5.3-alpha.1** reais. O outro não carrega esses módulos e verifica o spawn vanilla. O Guard está presente no primeiro ensaio, mas não foi criada uma região específica de negação de entrada. As decisões e métodos originais de proteção foram preservados.

A regressão de captura/quiz utiliza o CRZquiz original enviado anteriormente, não alterado ou redistribuído. Os resultados JSON acompanham este relatório. Não houve novo teste de cliente gráfico, servidor Windows ou desligamento abrupto durante a escrita.

## Reproduzir

Use Java 21, os JARs oficiais de dependências e o fluxo de build documentado em [0.1.23](../capturefix-0.1.23/README.md). O produto fica em `build/libs/Zorykai-Raids-0.1.25-returnspawn.1-fabric-1.21.1.jar`; o candidato dos fontes antigos permanece em `build/intermediates` e não deve ser instalado.

Em servidor descartável com o produto e `build/integration/shirokai-raids-integration.jar`, inicie com `-Dshirokai.integration=true -Dshirokai.integration.mode=returnspawn`. Com Essentials/Guard instalados ou ausentes, grava `returnspawn-results.json` e encerra. Para a regressão, o modo é `quizguard`, com o JAR original do quiz.

Não instale a fixture em produção. Não apague `state.json` para remover a pendência; se o spawn também falhar, a mensagem e o console informam o motivo e os dados continuam disponíveis para correção.

## Resultados finais

- [129 testes JUnit](unit-results.json), zero falhas.
- [50 verificações de retorno com Essentials/Guard](essentials-guard-results.json), aprovadas.
- [50 verificações com spawn vanilla](vanilla-spawn-results.json), aprovadas.
- [87 verificações de regressão de captura/quiz](capture-quiz-regression.json), aprovadas.
- Zero clientes gráficos reais. As três execuções usaram o mesmo SHA-256 do produto distribuído. [Trechos de execução](runtime-evidence.txt).
