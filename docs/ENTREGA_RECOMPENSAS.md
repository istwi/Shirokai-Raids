# Entrega de recompensas — 0.1.5-dev

A derrota global grava `completed` e todos os drops sorteados em `pending` antes de permitir inserções. A lista de contribuintes vem do acumulado do evento, incluindo quem saiu ou desconectou. Entregar não sorteia novamente nem depende de uma sessão de batalha ainda aberta.

## Protocolo

Tudo ocorre na thread do servidor. O inventário principal é planejado com stacks do mesmo item e componentes, respeitando limites de stack e slots vazios. Armadura e offhand não são alterados. Itens ausentes no registro ficam pendentes, sem conversão ou descarte.

1. Retirar somente a quantidade que cabe de `pending`, criar uma reserva em `deliveries` com UUID novo e jogador proprietário, e gravar `state.json` atomicamente.
2. Aplicar as inserções planejadas e adicionar a tag `shirokai_delivery:<UUID>` ao jogador. Falha normal na aplicação restaura os slots e remove o recibo; a reserva permanece.
3. Salvar apenas esse jogador pelo método vanilla `PlayerList.save`, acessado por Invoker. Inventário e tags fazem parte do mesmo NBT. Como esse método pode absorver erros de IO, reler o `.dat` e exigir a presença do recibo antes de prosseguir.
4. Remover a reserva e gravar `state.json`. Somente depois retirar o recibo da memória. Recibos órfãos de transações já confirmadas são limpos na próxima tentativa e persistem até o próximo salvamento do jogador.

No reinício, se uma reserva tem recibo carregado do jogador, os itens não são inseridos novamente: apenas se confirma a reserva. Sem recibo, a entrega ainda precisa ser aplicada. Se a reserva inteira não couber naquele momento, ela aguarda; não se perde seu saldo. Novas entregas podem ser parciais, deixando o restante em `pending`.

Uma falha em qualquer armazenamento interrompe novas entregas e desativa raids na instância. Corrija o problema de armazenamento antes de reiniciar. O registro é validado no carregamento; dados inválidos são preservados e não são substituídos por um estado vazio.

## Tentativas e compatibilidade

- Vitória agenda todos os contribuintes; jogadores offline mantêm seus itens no registro.
- Login agenda a recuperação. A cada cinco segundos, jogadores online com pendências voltam à fila.
- Cada tick processa no máximo um jogador e quatro transações. `/raid recompensas` usa o mesmo protocolo e mantém cooldown de cinco segundos. Esse limite reduz trabalho por passagem, mas não comprova desempenho com 100 pessoas; gravações continuam síncronas.
- Inventário cheio, item removido por outro mod ou limite vanilla de tags atingido deixam a recompensa pendente.
- `uncertain` é o registro legado sem recibo: nunca é convertido em uma reserva nova nem pago automaticamente. Conferência administrativa com backup e logs continua necessária.
- Configurações, tabelas antigas e pendências existentes são preservadas. O campo novo `deliveries` inicia vazio quando ausente em um estado antigo.

## Limites de recuperação

O protocolo cobre interrupção do processo entre as etapas, desde que o inventário e o recibo sejam restaurados juntos pelo armazenamento vanilla e o registro de recompensas mantenha sua gravação atômica. Não é uma transação distribuída com garantia contra perda física de disco, corrupção de NBT, fallback para `player.dat_old`, rollback independente de um arquivo, alteração administrativa de recibos ou mods que substituam o salvamento vanilla. Faça backups coerentes de `data/shirokai-raids/state.json` **e** `playerdata` com o servidor parado. Não apague recibos manualmente para forçar resgates.

O recibo é uma tag vanilla de entidade, somente no servidor; não requer pacote ou mod adicional no cliente. O menu seguro de pendências é uma próxima etapa.
