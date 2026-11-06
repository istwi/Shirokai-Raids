# Histórico de alterações

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
