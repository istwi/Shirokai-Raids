# Validação 0.1.7-dev — preparação operacional

Esta versão acrescenta as funções operacionais pendentes do escopo vigente e um guia para o teste final. Não inclui geração de arena/dimensão nem conteúdo Raid Dens. Não representa aceite com clientes reais ou capacidade para 100 pessoas.

## Implementado

- Lobby configurável em dimensão existente, orientação, busca segura, bloqueio em batalha/veículo e cooldown. `/raid entrar` conduz à arena quando o jogador ainda não está nela.
- Ticket temporário de chunks exclusivo da raid, limitado pelo raio da arena e liberado em finalização, cancelamento e falha. O mod não altera o estado vanilla de `/forceload`. Boss órfão com tag atual/legada é limpo; Pokémon comum sem tag é preservado. Boss removido, morto ou transferido de mundo cancela o evento.
- Configuração por comandos de intervalo, duração, limite e ativação automática. Validação/cópia antes de salvar; posições e orientações são preservadas. Reload valida dimensões e itens antes de substituir o estado válido.
- Avisos sem rajada após pausa/reinício. Avisos maiores que um intervalo curto permanecem no arquivo, mas ficam inativos até o intervalo comportá-los. Reagendamento por comando é persistido.
- Menu de pendências com paginação, atualização, navegação e botão de entrega; os ícones não são transferíveis. O botão e `/raid resgatar` compartilham journal e cooldown.
- Conferência administrativa de reservas legadas com auditoria persistente. Confirmação não entrega itens; devolução comprovada cria pendência uma única vez. Proprietário desconhecido não pode receber devolução automática nem ser substituído por outro jogador. Motivo obrigatório.
- Status administrativo com configurações, sessões, ticket, pendências, MSPT médio e heap. Guia de instalação, permissões, SkiesClear, recuperação e critérios finais atualizado.

## Testes executados

- `bash gradlew --no-daemon build`: passou; **63 testes Java**, zero falhas/erros/ignorados. Os sete novos casos cobrem avisos normais/após pausa, intervalo curto preservando avisos, cópia/validação de configuração e reconciliação persistente sem replay, incluindo motivo e proprietário inválidos.
- `python3 scripts/run_showdown_audit.py`: oito verificações históricas do simulador oficial passaram.
- `python3 scripts/run_traditional_probe.py`: 15 verificações do adaptador, oito callbacks isolados, seis verificações históricas do motor e três sessões ligadas ao coordenador Java passaram.
- `python3 scripts/verify_jar.py --version 0.1.7-dev`: passou. Fixtures de operação/menu/recuperação continuam fora do JAR distribuível.
- `bash scripts/run-challenge-integration.sh`: passou em `/workspace/shared/raids-challenge-test.qnjx8fX3`, Fabric/Cobblemon real com participantes controlados. Resultados atuais `challenge-integration-result.json`, `reward-recovery-result.json`, `raid-menu-result.json` e `operations-result.json`: todos `passed: true` e `realClients: 0`.
- `bash scripts/run-teleport-integration.sh`: passou em `/workspace/shared/raids-teleport-test.tS9flI0w`: teleporte à arena, colisão, distância do boss, cooldown, ausência de piso, informação sem deslocamento e ausência de raid.

A integração operacional conferiu persistência dos quatro controles administrativos, lobby com posição/orientação, cooldown, negação de administração a jogador comum, rejeição de dimensão inexistente preservando a configuração válida, presença real do ticket no `DistanceManager`, sua remoção após cancelamento e preservação de `/forceload` externo. Também conferiu limpeza da tag atual e preservação de Pokémon não marcado.

Foram exercidas 105 combinações de cliques no menu de pendências sem alterar inventário/cursor. O botão entregou quatro maçãs pelo journal real e a repetição não duplicou. A devolução administrativa registrou auditoria e deixou exatamente três diamantes pendentes; a repetição foi rejeitada. As 105 verificações anteriores do menu de histórico/ranking e os seis cenários de recarga real de playerdata continuaram passando.

O ensaio inicial detectou a rejeição de intervalos curtos por avisos antigos maiores que o intervalo; essa validação foi corrigida sem apagar configurações. Outra execução detectou uma limitação da fixture: emprestar a conexão inerte de um FakePlayer a um ServerPlayer faz o teleporte alterar o jogador dono da conexão. A fixture passou a usar FakePlayer com sua própria conexão para teleporte e ServerPlayer vanilla para menus, sem mudar os critérios de segurança do mod.

## Ainda depende do teste final

O guia `PREPARAR_SERVIDOR.md` e o roteiro `TESTE_DE_ACEITACAO.md` listam os passos finais. As coordenadas de arena/lobby pertencem ao mundo do usuário e precisam ser definidas nele com `setspawn`/`setlobby`; nenhum mundo de produção foi alterado aqui.

Não houve clientes reais, observação visual de R/interface/animações, ensaio de capacidade progressiva nem testes com LuckPerms/SkiesClear instalados. O fallback de permissões e a tag foram exercitados, não a integração em runtime com esses mods. Captura real por Pokébola e teleporte entre dimensões com cliente também continuam pendentes.

Gravações críticas permanecem síncronas e limitadas por passagem. Isso preserva a ordem do journal, mas exige medir latência/MSPT sob carga; o limite 100 não é evidência de suporte. A integração apresentou avisos de atraso de ticks, sem métricas suficientes para uma conclusão de desempenho. As restrições atuais do adaptador e os limites de recuperação de arquivos continuam documentados nas validações anteriores e no protocolo de recompensas.

Nenhuma configuração cloud foi alterada/publicada. O artefato é uma pré-release de desenvolvimento, não uma Release estável.

## Artefato

`build/libs/Shirokai-Raids-0.1.7-dev-fabric-1.21.1.jar`

SHA-256: `865d1461bd6996c0762c0a819b026ce2e0bd39ad448c23928d1271830a126bfd`
