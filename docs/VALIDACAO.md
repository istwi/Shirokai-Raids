# Validação executada

## Rede

Nova instância com política restrita atual, `observations_current: true` e estado `enforced`. Os domínios abaixo constam da política efetiva.

| URL exata | Resultado |
| --- | --- |
| https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml | HTTP 200 |
| https://api.modrinth.com/v2/project/cobblemon/version | HTTP 200 |
| https://artefacts.cobblemon.com/releases/com/cobblemon/fabric/maven-metadata.xml | HTTP 200 |
| https://maven.impactdev.net/repository/development/ | HTTP 200 |

Não houve 403 nesses quatro testes. O POM e o JAR exatos do Cobblemon 1.8.3 foram resolvidos pelo Maven oficial; portanto, não há evidência atual de erro de coordenada Maven nesses artefatos. HTTP 200 no índice ImpactDev isoladamente não comprova a existência de um artefato específico lá; foi utilizado o Maven artefacts.cobblemon.com.

Novos destinos retornaram `CONNECT tunnel failed, response 403` antes do TLS, e não estão na política ativa:

- https://launchermeta.mojang.com/mc/game/version_manifest_v2.json
- https://api.minecraftservices.com/publickeys
- https://sessionserver.mojang.com/

Esses são bloqueios do proxy/política do ambiente, não respostas 403 do servidor remoto nem erros de coordenada Maven. O instalador foi executado sem download automático do Minecraft; o server.jar veio do cache Gradle e foi validado pelo SHA-1 do manifesto Mojang oficial obtido via piston-meta. A autenticação online ainda precisa dos destinos adicionais.

## Build e testes

- Gradle 8.14.3, distribuição verificada pelo SHA-256 oficial.
- Wrapper substituído pelo binário exato do repositório oficial Gradle v8.14.3: SHA-256 `7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172`.
- JDK Eclipse Temurin 21.0.13+9, arquivo verificado pelo checksum oficial. O runtime Java original não continha javac.
- Truststore da sessão preservado. Nenhuma verificação TLS, assinatura ou checksum foi desativada.
- `gradlew build`: passou. Treze testes executados, zero falhas/erros: seis de estado e combate, três de tipos/recompensas, quatro de persistência/configuração.
- `scripts/verify_jar.py`: passou; metadados Fabric, versão, dependência Cobblemon, classes Java 21 e SHA-256 conferidos.
- Scripts shell conferidos com bash -n; os workflows YAML foram carregados. Isso não substitui execução do GitHub Actions.
- `scripts/setup-cloud.sh` e `scripts/prepare-test-server.sh` executados com sucesso.

## Teste funcional de servidor

O JAR distribuível inicializou com Cobblemon real em servidor dedicado. Foram executados pelo console:

- Criação de Rayquaza com HP virtual 10.000.000 e UUID persistido.
- NBT Unbattleable = 1b.
- Recusa de dano Minecraft externo (HP da entidade permaneceu 61.0f).
- Recusa de uma segunda raid ativa.
- Encerramento administrativo com remoção do boss.
- Reinício do servidor e novo spawn.
- Encerramento por tempo em configuração temporária de 30 segundos, remoção do boss e preservação do agendamento.

O teste não incluiu jogadores. Captura real, combate simultâneo, recompensas, LuckPerms e servidor Windows permanecem não validados. Os testes Windows estão configurados no CI, não foram executados localmente. Nenhum aceite de v1.0.0 foi criado.

Avisos upstream de mixins opcionais e datafixers foram observados no Cobblemon, mas não impediram inicialização. O modo dev gradlew runServer falha por metadados Kotlin remapeados; o fluxo do JAR de produção foi validado. Consulte as limitações em ARQUITETURA.md.
