# Shirokai Raids

Projeto independente para Minecraft Java 1.21.1, Fabric e Cobblemon 1.8.3.

**Estado: 0.1.2-dev, auditoria e protótipo de simulador. Não há Release estável aprovada.**

A instalação deve funcionar **somente no servidor**, com teto configurável de **100 jogadores em combate simultâneo**. Esse teto ainda não tem validação de desempenho no Minecraft. O [escopo vigente](docs/ESCOPO_WORLD_BOSS.md) exclui itens, blocos, cristais e geração de mundo do Raid Dens. O Shirokai continua independente. As recompensas devem usar itens existentes dos mods configurados, entregues diretamente.

Esta versão acrescenta um coordenador central com testes de replay, overkill, saída e encerramento único. O protótipo conecta três sessões reais do simulador oficial 1.8.3 ao coordenador Java e verifica entrada tardia e contribuição. Ele é uma fixture de teste, não uma integração de produção com o servidor/cliente Minecraft. Veja [validação 0.1.2-dev](docs/VALIDACAO_0.1.2-dev.md).

O objetivo atual é utilizar a interface tradicional Cobblemon/Showdown, seleção manual de movimentos e um World Boss com HP global. A [auditoria multiplayer](docs/COBBLEMON_MULTIPLAYER_AUDIT.md) encontrou impedimentos na API existente: MULTI não oferece três jogadores cooperativos com entrada contínua, e sessões separadas não compartilham HP/KO automaticamente. A substituição do combate ainda não foi implementada nem validada.

Conforme a condição de auditar antes de substituir, o combate automático da 0.1.0-dev permanece no código **como legado provisório**. Não é o comportamento definitivo solicitado. Esta build acrescenta teleporte seguro e ferramentas de auditoria; pressionar R ainda não inicia uma raid tradicional multiplayer.

## Funcionamento

O agendamento, a identidade do boss, HP virtual, contribuição individual e caixa postal de recompensas são preservados. Com raid ativa, `/raid` procura um destino seguro na arena e teleporta o jogador; sem raid, informa o agendamento. `/raid tempo` continua sendo apenas consulta.

O sistema legado usa `/raid atacar`. Ele não representa uma batalha tradicional e não deve ser apresentado como atendimento ao novo critério de aceitação. A futura migração precisa provar criação de sessões válidas, interface real, HP autoritativo e encerramento seguro antes de substituir esse código.

## Compilar

Requer JDK 21 completo, não apenas JRE:

```sh
./gradlew build
python3 scripts/verify_jar.py --version 0.1.2-dev
python3 scripts/run_traditional_probe.py
```

Windows: `gradlew.bat build`. O JAR é `build/libs/Shirokai-Raids-0.1.2-dev-fabric-1.21.1.jar`, acompanhado de `SHA256SUMS` após a validação. Para o ambiente cloud, use `bash scripts/setup-cloud.sh`.

## Instalação e servidor de teste

Instale Fabric Loader 0.17.2+, Fabric API 0.116.6+1.21.1, Fabric Language Kotlin 1.13.6+kotlin.2.2.20 e Cobblemon 1.8.3. Coloque o JAR em `mods/`. Faça backup antes de usar em qualquer mundo com dados importantes.

No ambiente cloud, `bash scripts/prepare-test-server.sh` prepara `/workspace/shared/raids-test-server` usando artefatos oficiais já resolvidos. Com a EULA aceita para esse servidor:

```sh
source /workspace/Shirokai-Raids/scripts/cloud-env.sh
cd /workspace/shared/raids-test-server
read -r -a raids_jvm_options <<< "${GRADLE_OPTS:-}"
java "${raids_jvm_options[@]}" -Xms512M -Xmx3G -jar fabric-server-launch.jar nogui
```

A configuração inicial desabilita aparições até um administrador usar `/raidadmin setspawn`. A arena deve estar em um chunk carregado; o mod não força carregamento ilimitado. `/raid` teleporta apenas para chunks já carregados, com piso e espaço livre. Não há fallback para dentro do boss, do vazio ou de blocos. Jogadores em batalha, veículos, mortos ou espectadores não são teleportados; há cooldown de cinco segundos.

## Comandos e permissões

| Comando | Função | Permissão |
| --- | --- | --- |
| `/raid` | Teleporte seguro se houver raid; senão, próxima ocorrência | shirokai.raids.use + shirokai.raids.join |
| `/raid tempo` | Estado e próxima ocorrência, sem teleporte | shirokai.raids.use |
| `/raid entrar` | Mostrar participação na arena | shirokai.raids.join |
| `/raid atacar` | Comando legado provisório, sem interface tradicional | shirokai.raids.attack |
| `/raid sair` | Parar ataques e preservar contribuição | shirokai.raids.use |
| `/raid dano`, `/raid top` | Dano aplicado e ranking | shirokai.raids.use |
| `/raid recompensas` | Resgatar um stack pendente | shirokai.raids.rewards |
| `/raidadmin iniciar <boss>`, `parar`, `setspawn`, `reload`, `status`, `listar` | Administração | shirokai.raids.admin |

Jogadores têm permissões de participação por padrão. Administração exige OP 2 por padrão. Se LuckPerms estiver instalado, negações explícitas são respeitadas. Resgates têm intervalo de cinco segundos; inventário cheio preserva itens. Reservas interrompidas exigem conferência administrativa, sem pagamento automático repetido.

Configurações geradas em `config/shirokai-raids/config.json`, `bosses/rayquaza.json` e `rewards/default.json`. Atualizações não sobrescrevem arquivos personalizados. Exemplos estão em [docs/examples](docs/examples).

Consulte [auditoria da API](COBBLEMON_API_AUDIT.md), [arquitetura e limitações](docs/ARQUITETURA.md), [validação desta build](docs/VALIDACAO_0.1.1-dev.md), [teste obrigatório com três jogadores](docs/TESTE_DE_ACEITACAO.md), [requisitos atuais de combate tradicional](docs/REQUISITOS_COMBATE_TRADICIONAL.md) e [processo de Releases](release/README.md).

Builds de desenvolvimento e PRs geram Artifacts temporários em Linux e Windows e executam oito verificações no simulador oficial. A fixture `build/integration/shirokai-raids-integration.jar` é exclusiva de testes em mundo descartável; não é incorporada ao mod distribuível e não deve ser instalada em produção. Tags SemVer oficiais disparam build, testes e validação de metadados antes de publicar o JAR e checksum. Nenhuma tag deve ser criada antes da aprovação da atualização.
