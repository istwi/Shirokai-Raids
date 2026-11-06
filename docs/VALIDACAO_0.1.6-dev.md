# Validação 0.1.6-dev — histórico e ranking global

## Comportamento

`/raid menu` e `/raid historico` abrem o histórico pessoal. `/raid ranking` abre o ranking global público. Ambos usam janela de baú vanilla, com 18 entradas por página e navegação inferior; nenhum mod adicional no cliente. A consulta não resgata drops nem modifica os itens do jogador.

Cada evento do histórico mostra boss, data/fuso, resultado, duração, dano efetivo, contribuição sobre o HP inicial, posição entre contribuintes, quantidade total de participantes, drops sorteados e quantidade ainda pendente de entrega. O botão **Seus totais** atualiza a página. O menu é uma fotografia dos dados ao abrir/atualizar, sem atualização automática de tooltips já abertos.

Os rankings globais têm categorias de participações, dano acumulado e vitórias. Todos podem ver os totais de todos os jogadores, inclusive offline; o histórico detalhado permanece pessoal. Os dados são associados ao UUID, com o último nome registrado em um evento. Empates usam uma ordem estável por UUID e posições sequenciais.

## Contagem e persistência

- Uma participação significa uma batalha aceita pelo servidor. Só passar pela arena não conta; reentrar no mesmo evento não conta novamente. Participantes sem dano também aparecem, com posição **sem dano**, sem direito fictício a drops.
- Totais são consolidados ao encerrar o evento. Vitória significa que o boss foi derrotado em um evento do qual participou, inclusive quem saiu antes do fim. O dano acumulado inclui eventos vencidos, cancelados, expirados e interrompidos.
- O histórico guarda os 20 últimos eventos por jogador; essa retenção não apaga totais globais nem pendências de recompensas antigas.
- Boss/horário/participantes e o dano do snapshot são persistidos junto ao estado. Entrada aceita salva imediatamente; snapshots de dano continuam a cada dez segundos e ao usar `/raid sair`.
- Encerramento grava resumo, totais, drops e limpeza do evento ativo no mesmo `state.json`. No reinício, um evento ainda persistido é marcado como interrompido usando o último snapshot; limpar esse evento e somar os totais também fazem parte de uma gravação. Repetir a recuperação não duplica participações. A data de uma interrupção é o momento da recuperação, não uma medição do instante exato da queda.
- Instalações antigas começam com arquivo de histórico vazio. O snapshot antigo de dano e o ledger `completed` não contêm dados suficientes para reconstruir eventos; nenhum histórico anterior é inventado.
- `menuTimeZone` tem padrão `America/Sao_Paulo`; o exemplo de configuração foi atualizado. Configuração existente é preservada e fuso inválido rejeita o reload antes de substituir o válido.
- Permissões opcionais `shirokai.raids.history` e `shirokai.raids.ranking`, com fallback permitido e sujeitas a `shirokai.raids.use`. Menus verificam disponibilidade/permissão ao navegar e continuam inválidos quando a persistência é suspensa.

## Verificações executadas

- `bash gradlew --no-daemon build`: passou; 56 testes Java, zero falhas, erros ou ignorados.
- Seis novos testes de arquivo: resumo com dano/ranking/drops, reentrada única, retenção sem perder totais, nome alterado no mesmo UUID, ordenação por cada métrica/empate, serialização e recuperação sem contagem repetida, estado antigo sem histórico inventado e rejeição de dados/fuso inválidos.
- `python3 scripts/run_showdown_audit.py` e `python3 scripts/run_traditional_probe.py`: passaram. Oito verificações históricas do simulador, 15 do adaptador, oito callbacks isolados, seis verificações históricas do motor e o protótipo de três sessões/coordenador.
- `python3 scripts/verify_jar.py --version 0.1.6-dev`: passou; a fixture de menu também está excluída do JAR distribuível.
- `bash scripts/run-challenge-integration.sh`: passou em `/workspace/shared/raids-challenge-test.QfMGanmn`. Os três resultados atuais têm `passed: true`: `challenge-integration-result.json`, `reward-recovery-result.json` e `raid-menu-result.json`.

A integração de combate real Fabric/Cobblemon verificou que os três jogadores, incluindo quem saiu/voltou/desconectou, somaram 3.000 de dano no ranking global, uma participação e uma vitória para cada um, além de quatro maçãs no histórico pessoal de cada contribuinte. As verificações anteriores de combate, entrega automática/parcial/offline e seis cenários de recarga de playerdata continuaram passando.

A fixture de menu usa `ServerPlayer` vanilla com conexão inerte para executar os métodos reais de abrir/criar/fechar menus. Fabric `FakePlayer` desativa `openMenu`, por isso não serve diretamente para esse ensaio. Conferiu os comandos, informações do item, histórico pessoal, navegação entre duas páginas, três ordenações globais e fechamento por indisponibilidade. Exerceu 105 combinações de tipo de clique, slot e botão, incluindo shift, arraste, troca com hotbar/offhand, descarte e clone; inventário, cursor, ícones e registro/recompensas permaneceram iguais. Fechar devolveu o cursor corretamente.

O remapeamento da fixture recebeu as classes principais no classpath, necessário para resolver métodos Minecraft herdados por `RaidMenu`. A primeira execução identificou essa ausência e falhou; a execução acima passou após a correção, sem suprimir as verificações.

## Limites

Nenhum cliente real participou: renderização, hover, aparência e comandos durante a interface de batalha precisam ser observados no cliente Cobblemon normal. O ensaio de conexão inerte verifica o lado servidor dos menus, sem renderizar uma tela nem conectar pela rede.

Não houve carga com 10, 25, 50 ou 100 jogadores. Totais de todos os jogadores e históricos limitados por jogador continuam no arquivo de estado com gravação síncrona; não são evidência de capacidade. O ensaio apresentou avisos de atraso de ticks, sem métricas suficientes para atribuição de causa ou conclusão de desempenho. Falhas físicas de armazenamento, backups incoerentes e limitações de recompensas legadas continuam descritos em `ENTREGA_RECOMPENSAS.md`.

Não foi criada Release oficial nem alterada/publicada configuração cloud. O Actions da etapa anterior (`42176457348`, commit `aca65f9`) foi consultado e estava `completed/success`; isso não substitui o resultado remoto desta versão.

## Artefato

`build/libs/Shirokai-Raids-0.1.6-dev-fabric-1.21.1.jar`

SHA-256: `5962e14e3f4ec9a38afa16279758750b85a4ea2687c35bd6f4846a7bfd6c0430`
