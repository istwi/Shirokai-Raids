# Histórico de alterações

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
