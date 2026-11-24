# Zorykai Raids 0.1.28-manualrewards.1

[Baixar JAR corrigido](https://github.com/istwi/Shirokai-Raids/raw/refs/heads/main/releases/v0.1.28-manualrewards.1/Zorykai-Raids-0.1.28-manualrewards.1-fabric-1.21.1.jar) · [Baixar código e testes](https://github.com/istwi/Shirokai-Raids/raw/refs/heads/main/releases/v0.1.28-manualrewards.1/zorykai-raids-0.1.28-manualrewards.1-codigo.zip)

Minecraft 1.21.1, Fabric, Java 21 e Cobblemon 1.8.3. Correção feita sobre o JAR completo 0.1.27-itemrewards.1, preservando a personalização Zorykai.

## Mudança solicitada: resgate pelo jogador

O encerramento da raid continua sorteando e reservando os prêmios com as mesmas chances, quantidades e destinatários. O inventário não recebe essas recompensas automaticamente no encerramento, no login nem por varredura de jogadores offline.

O jogador usa `/raid recompensas`:

- A lista principal mostra pendências antigas e novas, com paginação, prêmio, quantidade e identificação da raid/transação.
- Clique em uma recompensa nova para pedir apenas aquele prêmio.
- O botão **Resgatar recompensas disponíveis**, ou `/raid resgatar`, solicita as entregas disponíveis do próprio jogador. As entregas novas autorizadas são processadas uma por vez, respeitando o orçamento da fila.
- Sem espaço, com inventário bloqueado ou integração indisponível: o prêmio continua guardado; libere a condição e faça outro pedido. Não fica tentando indefinidamente em segundo plano.
- Em processamento: aguarde. Em conferência (`UNCERTAIN`): o jogador não pode repetir; exige verificação administrativa.
- Use o botão **Atualizar** para reler a lista e a situação atual.

Pedidos rápidos são limitados. Fechar a tela não desfaz um pedido já autorizado; desconectar cancela a fila de solicitações ainda não iniciadas. Uma operação externa já iniciada continua sendo conciliada pelo journal, sem repetir o efeito.

O botão antigo só acionava as reservas legadas, e o papel vazio só contava essas reservas. Por isso era possível ver “Nenhuma recompensa pendente” e “Itens pendentes: 0” mesmo havendo prêmios modulares. A tela e o resgate agora usam ambas as filas. A mensagem de pedido não é tratada como confirmação de entrega: cada conclusão é confirmada após persistir o recibo.

## Correção do excesso de tentativas e gravações

Antes, a fila marcava e salvava `IN_FLIGHT`, chamava o provedor, recebia `RETRY` para jogador offline/sem espaço, voltava a `PENDING`, salvava de novo e registrava outra linha no console. Isso repetia para as pendências e podia gerar muitas gravações de todo o estado.

Agora o despachante fica ocioso se não houver pedido explícito. A elegibilidade e o espaço são verificados antes da barreira durável. Uma falha temporária não fica gerando `Entrega: PENDING`; aguarda nova ação do jogador. Há proteção de intervalo também no journal e nos pedidos manuais. A manutenção de recibos não entrega prêmios automaticamente.

## Dados preservados

Não apague pendências, recibos nem o arquivo de estado. As reservas existentes continuam com os mesmos UUIDs, prêmios, quantidades e destinatários. Não há novo sorteio das pendências. Reservas incertas não são promovidas a pagas ou reexecutadas automaticamente.

A serialização existente é mantida. A política de pedidos e intervalos é estado transitório; após reiniciar, o jogador faz novo pedido pelo menu. Itens continuam usando o mesmo recibo por transação salvo com o inventário. Crates mantém a fábrica/assinatura oficial; moeda mantém o provedor Impactor e suas regras de incerteza.

## Instalação

1. Pare o servidor e faça backup do JAR, de `config/shirokai-raids/` e de `<mundo>/data/shirokai-raids/`.
2. Substitua o JAR anterior pelo `Zorykai-Raids-0.1.28-manualrewards.1-fabric-1.21.1.jar`; mantenha apenas uma versão do Raids instalada.
3. Preserve os JSONs de recompensas e o `state.json` existente. Não é necessário substituir as tabelas para habilitar a correção.
4. Inicie, finalize a captura/retorno da raid e teste `/raid recompensas` com espaço no inventário.

As novas raids continuam com os sorteios e chances já configurados. Não houve alteração no servidor de produção. O erro separado `EssentialsBridge.cosmeticsProtocol()` pertence à integração Core/Essentials e não é corrigido por este JAR.

## Validação e código

Os resultados automatizados acompanham esta entrega. O ensaio Fabric usa entidades/inventários/arquivos reais e jogadores simulados; não houve cliente gráfico humano nem teste na VPS. O arquivo `.audit.json` identifica o JAR base, o hash final e as classes substituídas/preservadas.

O ZIP de código inclui fontes, testes, Gradle e a ferramenta de montagem sobre a distribuição completa 0.1.27. Isso preserva retornos, autenticação, batalha/captura, mensagens privadas de dano e catálogo que não estão integralmente nas fontes antigas da branch principal. Não se trata de substituir o mod completo por uma compilação das fontes antigas.

### Resultado final

143 testes JUnit e 296 verificações em runtimes Fabric isolados passaram: 25 de fila manual/menu/reconexão, 252 de itens/recibos e 19 de catálogo/chances. Todos usaram exatamente o JAR com SHA-256 `bdd74f996482189123d4f5d75a1024cd48e467a089e69f334a3e324c003212e6`. O ensaio de fila incluiu 1.000 reservas para jogadores offline, sem gravações ou tentativas de entrega enquanto não havia pedido. As simulações não substituem a validação visual no cliente/modpack real.
