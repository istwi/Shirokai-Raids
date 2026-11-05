# Teste obrigatório antes de v1.0.0

Este roteiro ainda NÃO foi executado com três jogadores. Não marcar a Release como aprovada apenas por build ou inicialização.

Em servidor dedicado Fabric 0.17.2+, Minecraft 1.21.1, Fabric API 0.116.6+1.21.1 e Cobblemon 1.8.3:

1. Faça backup do mundo e das configurações. Instale o JAR de desenvolvimento e Fabric Language Kotlin 1.13.6+kotlin.2.2.20.
2. Entre com três clientes compatíveis e contas distintas. Configure a arena com `/raidadmin setspawn` e inicie `/raidadmin iniciar rayquaza`.
3. Cada jogador deve enviar um Pokémon com um movimento ofensivo suportado, entrar fisicamente na arena e executar `/raid atacar`.
4. Confira que o mesmo UUID de boss e a mesma BossBar recebem os três ataques, sem tela de batalha tradicional. Registre vídeo/logs e os UUIDs dos jogadores, Pokémon e boss.
5. Use `/raid dano` e `/raid top`: a soma do dano aplicado deve igualar a redução do HP. O último golpe deve contabilizar somente HP restante.
6. Tente captura, batalha tradicional, ataques fora do alcance, troca de proprietário, saída/entrada repetida e Pokémon em batalha. Não podem quebrar as proteções nem reiniciar cooldown.
7. Deixe o boss contra-atacar. Confira HP do Pokémon, desmaio/recall e ausência de dano a jogadores ou blocos.
8. Desconecte um participante após contribuir. Derrote o boss com os demais; reconecte o jogador e confira as recompensas pendentes.
9. Preencha o inventário e tente resgatar. Libere um slot e resgate: um stack por comando e cinco segundos entre resgates. Nenhum item pode cair no chão ou desaparecer por inventário cheio.
10. Reinicie após registrar a vitória e confira que não há novo pagamento. Interrompa um resgate em um mundo descartável e confira a reserva `uncertain`: não deve ser reaplicada automaticamente.
11. Confira término por tempo, rejeição de segunda raid, reinício com raid ativa, configuração inválida, recuperação do agendamento e ausência de bosses órfãos em chunks recarregados.
12. Execute o workflow de build em Windows e Linux e confira o JAR baixado, versão e SHA256SUMS.

Registre versões, commit, configuração, resultados esperados/observados e evidências. Corrija todos os problemas principais antes de criar o aceite de Release.
