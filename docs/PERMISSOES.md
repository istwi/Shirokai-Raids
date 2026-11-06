# Permissões — Shirokai Raids

Instalação somente no servidor, Minecraft 1.21.1/Fabric/Cobblemon 1.8.3. A versão escolhida para o ensaio com o plugin real é **LuckPerms Fabric 5.4.140**. O relatório de execução deve ser consultado separadamente; esta tabela descreve o contrato do código.

Sem LuckPerms, o acesso normal é permitido e administração exige OP 2. Com LuckPerms, `true` concede, `false` nega mesmo para OP e `undefined` usa o padrão da tabela. Se o LuckPerms está instalado mas o usuário ainda não está carregado ou a API está indisponível, a checagem falha fechada; não vira acesso administrativo por acidente. Console confiável exige nível de comando 2 e não é tratado como jogador.

| Node | Padrão sem definição LP | Recursos |
| --- | --- | --- |
| `shirokai.raids.use` | Permitido | Raiz `/raid` e acesso geral; também exigido nas funções abaixo |
| `shirokai.raids.join` | Permitido | `/raid`, `/raid entrar` para arena e desafio nativo do boss |
| `shirokai.raids.lobby` | Permitido | `/raid lobby` |
| `shirokai.raids.rewards` | Permitido | `/raid recompensas`, `/raid pendentes`, `/raid resgatar` e botão de entrega |
| `shirokai.raids.history` | Permitido | `/raid menu`, `/raid historico` e aba de histórico |
| `shirokai.raids.ranking` | Permitido | `/raid ranking` e abas globais |
| `shirokai.raids.admin` | OP 2 | Toda a raiz `/raidadmin`, incluindo iniciar/parar, configuração e status |
| `shirokai.raids.reconcile` | OP 2 | `/raidadmin reservas`, confirmar/devolver; também exige `admin` |

`/raid sair`, `/raid tempo`, `/raid top` e `/raid dano` ficam sob o acesso geral `use`. `shirokai.raids.attack` é legado e não controla movimentos; não existe comando de ataque automático. Permissão para o menu não controla a elegibilidade aos drops: a entrega automática continua baseada na contribuição, inclusive para offline.

## Exemplo para jogadores

Escolha o grupo que realmente existe no seu servidor. Estes comandos configuram somente os nodes do mod:

```text
/lp group default permission set shirokai.raids.use true
/lp group default permission set shirokai.raids.join true
/lp group default permission set shirokai.raids.lobby true
/lp group default permission set shirokai.raids.rewards true
/lp group default permission set shirokai.raids.history true
/lp group default permission set shirokai.raids.ranking true
```

Sem definição de `admin`/`reconcile`, jogadores comuns são negados e OP 2 mantém o fallback administrativo. Evite colocar negações gerais no grupo `default` sem revisar a herança dos administradores. Se aplicou os negativos do exemplo anterior, remova-os conscientemente com `/lp group default permission unset shirokai.raids.admin` e o equivalente de `reconcile`, após conferir sua política.

## Administração

Use um grupo administrativo separado; revise herança/prioridades no LuckPerms antes de aplicar:

```text
/lp group admin permission set shirokai.raids.admin true
/lp group admin permission set shirokai.raids.reconcile true
```

**Não conceda `shirokai.raids.*` ao grupo comum**: o wildcard também abrange administração e reconciliação. Uma negação herdada de outro grupo pode prevalecer conforme a configuração do LuckPerms; use o verbose/check do próprio plugin para conferir a permissão efetiva. Não altere permissões de outros mods para configurar estas funções.

## Negação por jogador e por mundo

```text
/lp user NOME permission set shirokai.raids.join false
/lp user NOME permission unset shirokai.raids.join
/lp user NOME permission set shirokai.raids.join false world=the_nether
```

No LuckPerms Fabric 5.4.140 testado, o contexto observado foi `world=overworld` (sem `minecraft:`), além de `gamemode=survival`. Para o Nether, o exemplo usa `world=the_nether`. Confirme o nome do contexto `world` que a versão instalada do LuckPerms expõe. O teste automatizado registra os contextos efetivos; não presuma que todo servidor/plugin use os mesmos nomes. Ao investigar um bloqueio, confira também `use`, heranças, negativos e contextos, não apenas o node específico.

Menus checam permissão na abertura e navegação e invalidam acesso revogado. O teste em conexão controlada não substitui login real, grupos do seu servidor, proxies ou sincronização em banco externo.
