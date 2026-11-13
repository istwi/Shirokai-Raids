# Menu administrativo de recompensas — 0.1.18-dev

Abra com `/raidadmin`. Também funcionam `/raidadmin menu`, `/raidadmin recompensas` e `/raidadmin recompensas <id-do-boss-ou-override>`.

O menu exige `shirokai.raids.admin` (OP nível 2 como fallback sem provedor de permissão). A permissão é revalidada durante o uso; revogá-la fecha a consulta. Somente os comandos de consulta desse menu são liberados para administradores dentro da raid, sem liberar os demais comandos bloqueados.

## Navegação

Na primeira linha:

- Os cinco perfis escolhem dificuldade padrão. A seta indica o perfil consultado; escolher outro limpa o filtro de boss.
- Categoria alterna **Gerais**, **TOP 3** e **Itens antigos**.
- Posição alterna 1, 2, 3, 4, 5, 10, 25, 50 e 100.
- Contribuição alterna valores predefinidos e inclui o mínimo geral configurado. O padrão da prévia é 10%; não é a contribuição real do administrador.
- Boss abre uma lista paginada com os bosses cadastrados, overrides configurados e a raid em andamento. Bosses automáticos sem override usam os perfis padrão.

Na segunda linha, passe o mouse nos resumos de **tipo** e **raridade**, na chance de nenhum prêmio, nos multiplicadores e nas explicações. Os prêmios aparecem abaixo com quantidade, chance, elegibilidade e disponibilidade da integração/item. As setas inferiores mudam de página.

Se existe raid em andamento, a abertura padrão consulta suas tabelas preservadas no início. Mesmo após `rewardreload`, essa visão mantém o snapshot da raid ativa; escolher um perfil padrão consulta a configuração atual para as próximas raids. **Atualizar** relê os dados já carregados em memória. Para aplicar arquivos JSON alterados, use `/raidadmin rewardreload` primeiro. O menu não lê alterações ainda não recarregadas nem modifica configurações.

## O que significa a porcentagem

**Gerais:** cada prêmio tem um sorteio independente, após os filtros de contribuição e posição. Por isso, as porcentagens dos vários prêmios não precisam somar 100%. Para tipo/raridade, o resumo mostra a probabilidade de ganhar **ao menos um** naquela categoria. Dois fragmentos independentes com chances de 30% e 40% resultam em 58% de receber ao menos um fragmento, e não 70%.

**TOP 3:** a chance inicial de cada prêmio é:

```
chance da posição × peso / soma dos pesos elegíveis × chance do prêmio
```

Exemplo dos padrões Normais: TOP 1 tem 30% de participar do sorteio; três prêmios com pesos iguais e chance individual de 100% resultam em 10% para cada prêmio e 70% de nenhum especial. TOP 2 tem 6% por prêmio. Fora do TOP 3 a chance especial é zero. Entradas com chance individual zero continuam ocupando seu peso, conforme a regra efetiva do motor.

**Itens antigos:** percentuais são por sorteio de raridade/item, não por vitória inteira. A prévia mostra o máximo de sorteios e o limite de itens. Quantidades exibidas nessa categoria são as bases configuradas; o limite total e o saldo dos sorteios anteriores podem reduzi-las. A raridade acompanha a faixa da contribuição simulada.

As raridades modulares vêm do campo numérico `rarity` nos prêmios. O padrão atual é zero e aparece como **Padrão (raridade 0)**. O menu não inventa nomes de raridade que não estejam definidos no sistema. As tabelas antigas preservam as categorias `common`, `rare`, `epic`, `legendary` ou seus nomes personalizados. **Raridade da recompensa e dificuldade do boss são conceitos separados.**

As probabilidades são do sorteio inicial, antes das políticas de cosméticos duplicados e compensações. Sem dados de propriedade de um jogador específico, não se promete uma porcentagem final pós-REROLL. O menu informa as políticas configuradas.

Integração indisponível não apaga o prêmio do sorteio: a entrega efetiva fica pendente. As quantidades modulares são calculadas usando a contribuição escolhida, o multiplicador da dificuldade, os overrides e os tetos atuais, com a mesma função usada pela entrega.

## Consulta segura

Abrir ou navegar não executa sorteios, não cria contas, não entrega recompensas, não altera o contador do catálogo e não escreve no estado das recompensas. Ícones são informativos e não podem ser retirados por clique, shift, teclas da hotbar, arraste ou descarte. O menu não serve para editar ou gerar prêmios.

Título configurável em `messages.json`: `admin.recompensas.titulo`. O padrão usa o tema SHIROKAI em negrito.
