# Teste real de reinício — 0.1.8-dev

Foram executadas **quatro JVMs independentes no mesmo mundo descartável**, com paradas normais e uma queda real por `SIGKILL`. Não foram somente releituras de JSON/NBT no mesmo processo. Resultado: os quatro boots passaram; não foi encontrada falha do mod nos cenários abaixo. Não foi necessário alterar a lógica do JAR distribuído.

## Boss e configurações

- O primeiro boot configurou arena em **x=12,25, y=200, z=-8,75**, orientação 37°/9°, e um lobby separado. Criou uma raid com dano controlado e desligou normalmente com o evento ainda ativo.
- O shutdown cancelou a raid, removeu o boss e registrou o histórico uma vez. No próximo processo não havia boss antigo nem evento retomado.
- Com o servidor desligado, foram editados HP de 12.345 para 23.456, escala de 2,5 para 3, multiplicador de 1,75 para 2, duração para 900s e limite para sete. O novo processo carregou essas configurações e o boss novo recebeu HP/escala configurados, com o multiplicador correto na definição carregada.
- As novas raids nasceram nas mesmas coordenadas salvas, com UUIDs diferentes do boss anterior e brilho nativo ativo. Arena, lobby e orientações não foram substituídos por valores padrão.
- Foram comparados hashes SHA-256 de todos os JSONs do Shirokai, antes/depois dos processos e entre o checkpoint dentro da JVM e sua saída. Os arquivos existentes ficaram idênticos, exceto pelas edições intencionais feitas pelo teste. Tabela personalizada e uma propriedade extra de anotação no arquivo do boss também permaneceram intactas.

**Comportamento confirmado:** reiniciar não retoma o mesmo boss com o HP antigo. O evento é cancelado/interrompido por projeto. O ponto configurado permanece e uma nova raid usa esse mesmo local. O teste não mudou essa política.

## Queda forçada e persistência

- No segundo processo foi iniciada outra raid. O mundo foi salvo com o boss ainda presente e o snapshot de contribuição persistido.
- A fixture preparou uma entrega cujo inventário e recibo já estavam salvos, mas cuja reserva ainda aguardava confirmação no ledger. O processo externo leu o marcador atômico da fase e aplicou `SIGKILL` àquela JVM; o retorno foi `-SIGKILL`, sem executar os hooks de shutdown.
- No terceiro boot, o teste observou o UUID do boss salvo sendo carregado e limpo. Não restou boss duplicado; um Pokémon comum de controle foi preservado, sem receber brilho do boss.
- A entrega foi recuperada usando o playerdata real gravado pelo processo anterior. Permaneceram **quatro maçãs**, sem nova inserção, e a reserva foi confirmada. Repetir a tentativa não criou itens.
- Uma pendência offline antiga de três diamantes e o ledger de evento concluído foram preservados.
- O histórico terminou com duas participações, zero vitórias e 3.333 de dano controlado: um evento cancelado por parada normal e outro interrompido por queda. O quarto boot não repetiu essa contabilização.
- Agendamento futuro foi preservado quando não havia um comando de início de nova raid. Piso e `/forceload` externo permaneceram; a limpeza do mod não os apagou.

## Reprodução e evidências

Após compilar e preparar o servidor descartável, com a EULA autorizada para esses testes:

```sh
source scripts/cloud-env.sh
bash gradlew --no-daemon build
bash scripts/run-restart-integration.sh
```

O script cria um diretório novo, usa a porta 25566 de forma sequencial e nunca reaproveita o mundo do usuário. A etapa de queda só mata o processo Java criado por ele, depois do marcador válido. Resultados antigos não são aceitos. A implementação do harness de `SIGKILL` é específica de Linux.

Execução aprovada: `/workspace/shared/raids-restart-test.2e__i3q3`. Os logs `prepare.log`, `normal-and-crash.log`, `recover.log` e `verify-again.log`, os resultados por fase e `restart-integration-result.json` ficaram preservados nesse diretório. Um resumo estruturado com os hashes está em [`evidence/restart-0.1.8-dev.json`](evidence/restart-0.1.8-dev.json).

O build passou, e o verificador confirmou que a fixture permanece fora do JAR distribuível. O JAR 0.1.8-dev continuou com SHA-256 `ff8b0d72a43797171dffd77c02ac91d58be821742838cb425df1634929181389`.

## Limites

Contribuição e a janela de entrega foram preparadas de forma controlada para atingir os pontos de persistência; este teste não substitui o teste de combate com clientes reais. Não houve autenticação/login TCP de jogadores. O playerdata foi carregado em um novo processo usando a serialização vanilla.

O cenário usa agendamento futuro e a política `SKIP_MISSED`; não executou ocorrências vencidas com `START_ONCE`. Também não simulou perda física de disco, corrupção ou restauração de backups incoerentes. `SIGKILL` valida perda do processo, não desligamento da máquina/armazenamento. A aparência do contorno, mods externos e capacidade para 100 jogadores continuam dependendo dos testes próprios.
