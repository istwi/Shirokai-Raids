# Shirokai Raids

Projeto independente para Minecraft Java 1.21.1, Fabric e Cobblemon 1.8.3. Não deriva do GTS-Shirokai.

**Estado: 0.1.0-dev, em desenvolvimento. Não há Release v1.0.0 aprovada.** O JAR compila e o ciclo básico do boss foi testado em servidor dedicado; combate com três jogadores e distribuição real de recompensas ainda precisam de validação.

## Funcionamento

Um boss Pokémon aparece a cada duas horas em uma arena configurada. Jogadores enviam seus Pokémon e usam `/raid atacar` para iniciar ataques automáticos em tempo real. O boss tem HP virtual compartilhado, contribuição individual e recompensas por peso/faixa de dano. A integração não cria batalhas Showdown para simular a raid.

## Compilar

Requer JDK 21 completo, não apenas JRE:

```sh
./gradlew build
python3 scripts/verify_jar.py --version 0.1.0-dev
```

Windows: `gradlew.bat build`. O JAR é `build/libs/Shirokai-Raids-0.1.0-dev-fabric-1.21.1.jar`, acompanhado de `SHA256SUMS` após a validação. Para o ambiente cloud, use `bash scripts/setup-cloud.sh`.

## Instalação e servidor de teste

Instale Fabric Loader 0.17.2+, Fabric API 0.116.6+1.21.1, Fabric Language Kotlin 1.13.6+kotlin.2.2.20 e Cobblemon 1.8.3. Coloque o JAR em `mods/`. Faça backup antes de usar em qualquer mundo com dados importantes.

No ambiente cloud, `bash scripts/prepare-test-server.sh` prepara `/workspace/shared/raids-test-server` usando artefatos oficiais já resolvidos. Com a EULA aceita para esse servidor:

```sh
source /workspace/Shirokai-Raids/scripts/cloud-env.sh
cd /workspace/shared/raids-test-server
read -r -a raids_jvm_options <<< "${GRADLE_OPTS:-}"
java "${raids_jvm_options[@]}" -Xms512M -Xmx3G -jar fabric-server-launch.jar nogui
```

A configuração inicial desabilita aparições até um administrador usar `/raidadmin setspawn`. A arena deve estar em um chunk carregado; o mod não força carregamento ilimitado. A participação exige entrar fisicamente na arena, sem teleporte automático.

## Comandos e permissões

| Comando | Função | Permissão |
| --- | --- | --- |
| `/raid`, `/raid tempo` | Estado e próxima ocorrência | shirokai.raids.use |
| `/raid entrar` | Mostrar participação na arena | shirokai.raids.join |
| `/raid atacar` | Selecionar Pokémon enviado elegível | shirokai.raids.attack |
| `/raid sair` | Parar ataques e preservar contribuição | shirokai.raids.use |
| `/raid dano`, `/raid top` | Dano aplicado e ranking | shirokai.raids.use |
| `/raid recompensas` | Resgatar um stack pendente | shirokai.raids.rewards |
| `/raidadmin iniciar <boss>`, `parar`, `setspawn`, `reload`, `status`, `listar` | Administração | shirokai.raids.admin |

Jogadores têm permissões de participação por padrão. Administração exige OP 2 por padrão. Se LuckPerms estiver instalado, negações explícitas são respeitadas. Resgates têm intervalo de cinco segundos; inventário cheio preserva itens. Reservas interrompidas exigem conferência administrativa, sem pagamento automático repetido.

Configurações geradas em `config/shirokai-raids/config.json`, `bosses/rayquaza.json` e `rewards/default.json`. Atualizações não sobrescrevem arquivos personalizados. Exemplos estão em [docs/examples](docs/examples).

Consulte [auditoria da API](COBBLEMON_API_AUDIT.md), [arquitetura e limitações](docs/ARQUITETURA.md), [validação executada](docs/VALIDACAO.md), [teste obrigatório com três jogadores](docs/TESTE_DE_ACEITACAO.md), [requisitos completos](docs/REQUISITOS.md) e [processo de Releases](release/README.md).

Builds de desenvolvimento e PRs geram Artifacts temporários em Linux e Windows. Tags SemVer oficiais disparam build, testes e validação de metadados antes de publicar o JAR e checksum. Nenhuma tag deve ser criada antes da aprovação da atualização.
