# Validação 0.1.5-dev — entrega automática

Esta etapa implementa entrega direta no inventário e recuperação de novas reservas com recibos vanilla. Preserva o combate tradicional e as configurações existentes. Não é uma Release estável nem conclusão do escopo World Boss.

## Verificações executadas

- JDK Temurin 21.0.13, Gradle 8.14.3, Minecraft 1.21.1, Fabric Loader 0.17.2, Fabric API 0.116.6+1.21.1 e Cobblemon 1.8.3.
- `bash gradlew --no-daemon build`: passou; 50 testes Java, zero falhas, erros ou ignorados. Inclui 16 casos de entrega e dois novos de compatibilidade/validação de estado.
- Os testes de entrega verificaram inventário cheio, item indisponível, quantidade parcial, contribuinte offline, repetição, orçamento por tentativa, preservação do registro legado `uncertain` e item já consumido após o recibo salvo.
- Oito pontos de interrupção foram simulados nos testes Java: antes/depois da reserva, antes/depois da inserção, antes/depois de salvar o jogador e antes/depois da confirmação do registro. Cada caso recarregou os arquivos persistidos e conferiu conservação de itens, saldo e ausência de replay. O erro de salvamento do jogador sem exceção também é coberto.
- `python3 scripts/run_showdown_audit.py`: oito verificações históricas no simulador oficial passaram.
- `python3 scripts/run_traditional_probe.py`: 15 verificações do adaptador embarcado, oito callbacks isolados, seis verificações históricas do motor e protótipo de três sessões Showdown/coordenador Java passaram.
- `python3 scripts/verify_jar.py --version 0.1.5-dev`: passou; metadados, mixins, Java 21, adaptador e ausência das fixtures/conteúdo Raid Dens no JAR distribuível.
- `bash scripts/prepare-test-server.sh`: passou pelo modo de Loader local da API oficial do instalador, com checksums de instalador, Loader, bibliotecas e intermediary. O catálogo `meta.fabricmc.net` estava bloqueado por HTTP 403; nenhuma política cloud foi alterada para executar o teste.

## Integração em servidor real

`bash scripts/run-challenge-integration.sh` passou em `/workspace/shared/raids-challenge-test.PK2wxUjt`. Servidor Fabric/Cobblemon real, três FakePlayers de combate, nenhum cliente Minecraft. Resultado atual com `passed: true` em `challenge-integration-result.json` e `reward-recovery-result.json`; logs em `integration.log`.

O fluxo conferiu desafio padrão, pacote da interface, movimentos manuais, PP, troca, entrada tardia, saída/retorno com contribuição acumulada, desconexão, soma global de 3.000 HP e encerramento conjunto. Escala 2,5, multiplicador 1,5, drops personalizados e reload inválido continuaram funcionando; uma batalha selvagem comum após a raid também foi iniciada.

Para recompensas, um participante tinha todos os slots ocupados e um stack de 63 maçãs. Recebeu uma maçã, mantendo três pendentes; ao liberar espaço e executar o callback de entrada, recebeu o saldo. Outro participante foi removido do lookup online antes da derrota: manteve quatro maçãs pendentes e as recebeu ao voltar ao lookup e executar o callback de entrada. O terceiro recebeu quatro automaticamente. Novas tentativas pelo comando não duplicaram itens. Os arquivos vanilla dos jogadores continham inventário e recibos.

A fixture separada de recuperação exerceu seis fronteiras com `PlayerRewardTarget` real: depois da reserva, depois da inserção, antes/depois do salvamento do jogador e antes/depois da confirmação. Descartou inventário e tags em memória, recarregou o NBT salvo pelo Minecraft e o JSON do registro, e retomou a entrega. Cada caso terminou com exatamente quatro maçãs, nenhuma reserva/saldo restante e zero itens adicionais na repetição. Foram interrupções simuladas por exceção e recarga de arquivos, **não** encerramentos forçados da JVM nem ensaios de falha física de disco.

## Reprodução

```sh
source scripts/cloud-env.sh
bash gradlew --no-daemon build
python3 scripts/run_showdown_audit.py
python3 scripts/run_traditional_probe.py
python3 scripts/verify_jar.py --version 0.1.5-dev
bash scripts/prepare-test-server.sh
# EULA já autorizada pelo usuário somente para o servidor descartável de testes.
bash scripts/run-challenge-integration.sh
```

O script cria um mundo novo e valida os dois resultados dessa execução. A fixture de recuperação pertence ao source set de integração e não é distribuída no mod.

## Limites e pendências

Veja [o protocolo de entrega](ENTREGA_RECOMPENSAS.md). Reservas antigas ambíguas não podem ser reconciliadas automaticamente; não há comando administrativo de reconciliação nesta etapa. Backups incoerentes, corrupção, rollback de apenas um armazenamento e alterações de recibos por administradores/outros mods não têm garantia de entrega única.

As gravações e releituras são síncronas, limitadas a um jogador e quatro transações por passagem automática. Não foram medidos TPS/MSPT ou capacidade para 10, 25, 50 ou 100 jogadores. O primeiro ensaio apresentou aviso de atraso de ticks; isso não constitui medição de carga.

Nenhum cliente real participou. O login/desconexão da fixture chama callbacks reais em jogadores simulados, sem reconectar uma conexão TCP. Interface visual, animações, captura real, compatibilidade com outros mods e aceite multiplayer continuam pendentes. Menu de pendências e demais itens de lobby/chunks/agendamento/permissões exigem etapas próprias. Não foi criada Release oficial nem publicada configuração cloud.

O Actions anterior `42162430907`, referente ao commit `8b4040b`, foi consultado e estava `completed/success`; esse resultado não valida o código desta etapa.

## Artefato

`build/libs/Shirokai-Raids-0.1.5-dev-fabric-1.21.1.jar`

SHA-256: `83ac0e66c2727ee8bc00b3b28623e45a0231999c0a1af9c90902920522dfa7c5`
