# Validação 0.1.13-dev

Build local de desenvolvimento, ainda sem aceite com clientes humanos.

## Comportamento atual

Entrada exige inventário/hotbar, mão secundária e cursor vazios, permitindo armadura e acessórios equipados. Não há novos backups de inventário. Após a vitória, somente participantes presentes e com dano positivo recebem uma batalha nativa privada, com prazo fixo de 120 segundos desde a morte do boss. Não existe comando ou oportunidade de captura futura. O prazo de retorno dos participantes remanescentes na arena é de 600 segundos desde a vitória, persistido por evento. Consulte [regras e limites](CAPTURA_RAID.md).

## Verificações

- Build Gradle aprovado; 79 testes JUnit, sem falhas, erros ou ignorados. Cobrem quantidades por ranking, configuração, validade do registro de captura, remoção do comando e limites de tempo/localização do retorno, inclusive serialização do prazo.
- Verificador do JAR aprovado: estrutura, metadados, Java 21 e SHA-256.
- Auditoria Showdown: oito verificações aprovadas. Probe tradicional com três sessões Showdown/coordenador Java aprovado.
- Captura dedicada: **38 verificações aprovadas** no Minecraft 1.21.1/Fabric/Cobblemon 1.8.3, incluindo rejeição de itens sem apagá-los, armadura permitida e preservada, ausência de novo backup, recuperação de inventário do build local anterior, comando removido e exclusão de ausentes.
- Duas batalhas privadas reais e independentes, com adversários próprios. Movimento aplicado, HP reduzido e PP consumidos no Showdown; PP preservados ao encerrar. A inicialização do adversário de captura é ajustada somente para essas cópias, mantendo as batalhas normais separadas.
- Falha com chance de 2% e semente controlada; sucesso com chance de 100% como controle positivo do caminho nativo. A fixture responde ao pacote normal de passagem de turno exigido pelo cliente Cobblemon ao lançar uma bola, usando o handler padrão.
- Uma única captura na equipe/PC, orçamento debitado, bolas restantes removidas e armadura preservada.
- Limite de dois minutos verificado imediatamente antes e no prazo, inclusive com sucesso calculado e animação ainda em andamento. Projétil e tarefas cancelados; após aguardar o restante da animação, nenhum Pokémon foi entregue fora do prazo. Não foi possível retomar a oportunidade encerrada.
- Retorno verificado imediatamente antes e aos dez minutos, com restauração da origem e remoção do vínculo da raid. As verificações temporais usam avanço controlado do parâmetro de relógio das mesmas rotinas de produção; não aguardam dez minutos reais.

## Regressões de servidor

O desafio coletivo dedicado passou com três sessões controladas, transição para batalhas de captura, exclusão do contribuinte ausente e entregas após a saída. Também passaram os seis cenários de recuperação de recompensas, 105 cliques de leitura e 105 cliques no menu de pendências.

Os scripts `run-challenge-integration.sh` e `run-teleport-integration.sh` verificam o combate coletivo, transição para captura, saída, recompensas e teleporte. A fixture de desafio registra participantes online de forma coerente com o runtime, exclui o jogador ausente da captura, encerra as novas batalhas ao sair e só depois exercita entregas parciais/offline/login, seis cenários de recuperação e menus de leitura.

## Limites

Os participantes são controlados, sem clientes humanos. A renderização das cópias, mira, tela de batalha e interação com acessórios de outros mods ainda precisam de conferência no cliente real. Não há novo aceite de capacidade para 100 participantes. A chance padrão é fixa por bola; HP e status não a aumentam. Interromper uma captura ou terminar seu prazo durante a animação perde a oportunidade. Arquivos da raid e armazenamento Cobblemon não formam uma transação atômica.

Os servidores são descartáveis, com EULA aceita explicitamente pelo usuário. A fixture usa forceload somente no seu mundo de teste, para atualizar entidades sem clientes; o mod usa tickets próprios. Essa evidência é local, sem publicação ou execução do GitHub Actions desta versão.
