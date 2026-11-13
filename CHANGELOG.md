# Histórico de alterações

## 0.1.12-dev

- Chat e títulos de BossBar aceitam cores Minecraft (`&a`, `§a`), negrito (`&l`), reset (`&r`) e hexadecimal (`&#RRGGBB`).
- BossBar azul pública mostra o tempo até a próxima raid automática e esvazia proporcionalmente ao intervalo, substituindo os avisos periódicos no chat.
- Ao nascer o boss, a contagem sai e a barra de HP existente continua para os participantes na arena, com o alerta de pouca vida.
- Contagem removida ao desativar o automático, perder persistência ou encerrar o servidor; desconexões removem jogadores da barra.
- Guia de personalização atualizado. Build local, 75 testes JUnit, auditoria Showdown, ensaio de três sessões e verificação do JAR passaram. Conferência visual com cliente real permanece pendente.

## 0.1.11-dev

- Mensagens do chat, menus e BossBar editáveis em `config/shirokai-raids/messages.json`, com variáveis e reload; textos personalizados são preservados e chaves omitidas usam o padrão.
- Menu `/raid` com fundo cinza e três estrelas centrais: entrar, informações/atualizar e sair.
- BossBar alterna roxo/vermelho a cada 10 ticks com 20% de vida ou menos.
- Avisos naturais, como `Faltam 5min para a próxima raid.`, sem unidades zeradas.
- Cadastro de bosses por arquivos JSON documentado com exemplo de Mewtwo; sorteio uniforme dos bosses cadastrados no agendamento automático, cujo intervalo padrão é de duas horas.
- Carregamento da lista testado contra IDs duplicados, valores inválidos e lista vazia; catálogo validado antes de substituir mensagens em uso.
- Compilação e 73 testes JUnit passaram localmente. Validação visual com clientes reais permanece pendente.

## 0.1.10-dev

- Contadores de evento, menu, BossBar, avisos, status e histórico agora usam horas, minutos e segundos, por exemplo `1h 05min 30s`.
- Testes cobrem segundos, transições de minuto/hora, valores negativos e durações acima de 24 horas.
- Configuração e argumentos administrativos continuam em segundos.

## 0.1.9-dev

- `/raid` e `/raid menu` abrem informações do evento com botões de entrada, saída e atualização.
- Entrada salva dimensão, posição e orientação em disco antes do teleporte; saída pelo menu ou `/raid sair` retorna à origem, inclusive após desconexão, reinício ou término do evento.
- Somente participantes dentro da área podem desafiar o boss e aplicar dano; sair da área encerra a batalha sem apagar a posição de retorno.
- Enquanto a posição de retorno estiver registrada, comandos ficam bloqueados, exceto `/raid`, `/raid menu` e `/raid sair`.
- Contadores do evento, BossBar, avisos e histórico exibidos em minutos; configuração e argumentos administrativos mantêm segundos.
- Compilação, 67 testes automatizados e estrutura do JAR validados em Linux. Validação com clientes Minecraft reais permanece pendente.

## 0.1.8-dev

- Contorno luminoso nativo do Minecraft ativado na entidade pública do boss ao aparecer, sem exigir mod adicional no cliente nem alterar equipes do scoreboard.
- Aparência do contorno no modelo Cobblemon ainda depende da conferência com cliente real.

## 0.1.7-dev

- Lobby em dimensão existente com posição/orientação configuradas, busca segura e cooldown; `/raid entrar` também conduz à arena quando necessário.
- Ticket temporário da arena, liberado em término, cancelamento e falhas, sem alterar `/forceload` de outros sistemas. Limpeza de bosses órfãos e cancelamento de boss removido/deslocado.
- Menu de pendências com botão de entrega usando o mesmo journal e cooldown do comando `/raid resgatar`.
- Conferência administrativa auditada de reservas antigas, sem repetição de devolução e sem atribuição automática a proprietário desconhecido.
- Controles persistentes de intervalo, duração, limite e agendamento; alterações isoladas até validar/salvar. Dimensões inválidas no reload preservam a configuração anterior.
- Avisos sem rajada após pausas; intervalos curtos preservam avisos maiores como inativos. Status inclui MSPT médio e heap para os ensaios finais.
- Guia de instalação, permissões, SkiesClear e roteiro de teste final. Clientes reais, capacidade e compatibilidade com outros mods continuam sem aceite.

## 0.1.6-dev

- Menu vanilla de histórico pessoal com 20 eventos: boss, data, resultado, duração, dano, contribuição, posição, participantes e drops sorteados/pendentes.
- Ranking global público por participações, dano acumulado e vitórias, com paginação e totais persistentes por UUID, inclusive para jogadores offline.
- Entrada e retorno ao mesmo evento contam uma participação; encerramento e recuperação após reinício registram os totais junto ao estado, sem contar o mesmo evento novamente.
- Menus somente de consulta, sem transferência de ícones ou alteração do inventário, com permissões opcionais e fuso horário configurável.
- Dados começam nesta versão; raids antigas sem histórico não são reconstruídas. Validação visual com clientes reais continua pendente.

## 0.1.5-dev

- Entrega automática no inventário após vitória global e login, com novas tentativas periódicas e manuais; saldo parcial e contribuintes offline preservados.
- Reservas com recibo salvo junto ao inventário vanilla e confirmação por releitura do arquivo, permitindo reconciliar interrupções sem repetir itens. Reservas antigas ambíguas continuam sem replay automático.
- Testes de inventário cheio, entrega parcial, offline, repetição, falha silenciosa de salvamento e interrupções em cada fronteira de gravação.
- Preparação do servidor descartável pelo modo oficial de Loader local, com versões e checksums verificados, sem consulta ao catálogo de versões.
- Menu de pendências, clientes reais e ensaios de capacidade continuam pendentes; nenhuma Release estável.

## 0.1.4-dev

- Multiplicador de dano por boss, preservando proteções nativas e isolamento entre sessões.
- Sorteio em duas etapas com faixas de contribuição, probabilidades de raridade e pools ponderados; compatibilidade com tabelas antigas.
- Validação dos itens configurados e rejeição de reload inválido sem perder a configuração válida anterior.
- Persistência imediata ao usar `/raid sair` e teste de retorno ao mesmo evento com contribuição acumulada.
- Testes de vida, tamanho, dano e drops configurados em servidor descartável com três jogadores simulados.

## 0.1.3-dev

- Integração somente servidor ao desafio padrão do Cobblemon, usado pela tecla R.
- Batalhas reais independentes com equipe e escolha manual de movimentos, troca e PP.
- Remoção do comando e dos loops de ataques automáticos.
- Dano reservado no coordenador global no ponto nativo de aplicação, sem crédito de overkill e mantendo drain/recoil com dano efetivo.
- Sincronização de HP e encerramento das sessões, com flush do PP real antes da parada.
- Mixins restritos ao boss de raid para inicializar o pacote da interface e permitir trocas sem gerar cópias físicas do boss.
- Limite configurável de 1 a 100 sessões; capacidade para 100 jogadores ainda não comprovada.
- Testes locais de integração em servidor descartável e dez verificações do adaptador no simulador oficial.
- Tag `shirokai_raid_boss` aplicada ao boss público, mantendo a identificação legada.

## 0.1.2-dev

- Auditoria do Raid Dens 0.13.4, licenças, Cobblemon 1.8.3 e SkiesClear.
- Coordenador autoritativo com testes de admissão, replay, overkill, saída e término.
- Protótipo com três sessões Showdown em Node ligadas ao coordenador Java.
- Registro do escopo sem itens ou blocos Raid Dens, somente servidor.

## 0.1.1-dev

- Base preservada: teleporte seguro, persistência, comandos e auditoria multiplayer inicial.
