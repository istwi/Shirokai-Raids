# Critério atual: três clientes com batalhas tradicionais

**Pendente. Não executado. A migração não está implementada.** Teleporte com FakePlayer e probes do simulador não substituem este roteiro.

Só iniciar o aceite do combate depois de um protótipo resolver os impedimentos de COBBLEMON_MULTIPLAYER_AUDIT.md.

1. Instalar a build candidata em servidor dedicado Minecraft 1.21.1, Fabric, Java 21 e Cobblemon 1.8.3, com backup do mundo e dos dados da raid.
2. Conectar três clientes reais com contas distintas. Registrar commit, versões e configuração.
3. Configurar a arena, iniciar Rayquaza e executar `/raid` nos três clientes. Conferir posições seguras, dimensões válidas, boss visível, ausência de colisão e bloqueio quando não existir destino seguro.
4. Enviar Garchomp, Mewtwo e Zacian. Pressionar a tecla normal de desafio em cada cliente. Confirmar interface genuína do Cobblemon, quatro movimentos, PP, seleção manual e ausência de `/raid atacar`.
5. Escolher movimentos diferentes; confirmar dano de três sessões/participantes no mesmo HP global, sem contar mensagens públicas/privadas duas vezes. Somatório de contribuição deve igualar o HP global perdido, descontando apenas regras de cura explicitamente documentadas.
6. Testar miss, proteção, imunidade, multihit, status residual, habilidades, STAB, efetividade, drain, recoil, cura, troca e desmaio. Mecânicas não suportadas precisam de política explícita e não podem corromper outras sessões.
7. Entrar com um participante novo durante a raid. A entrada não pode reiniciar ou tomar o battleId de outra sessão.
8. Sair, fugir, desconectar e reconectar um jogador; manter HP global e contribuição. Batalhas normais fora da raid devem continuar funcionando.
9. Derrotar o boss: fechar admissão e ataques, encerrar todas as sessões, aplicar só o HP restante do último golpe e registrar uma única vitória global. Não distribuir prêmio global por vitória local.
10. Testar timeout com batalhas abertas. Confirmar encerramento, UI fechada, ausência de referências/bosses órfãos, sem recompensa indevida.
11. Conferir `/raid top`, `/raid dano`, faixas de contribuição e sorteios individuais. Inventário cheio e desconexão devem preservar recompensas.
12. Reiniciar servidor antes/depois da vitória e durante um resgate em mundo descartável; não duplicar pagamento. Reconciliar reservas ambíguas conforme política persistente.
13. Executar builds e testes em Linux/Windows e validar JAR, metadados e checksum. Aprovar a Release somente com evidências dos passos acima.

Os testes de teleporte implementados nesta entrega são específicos para busca segura e comandos, sem prova de interação R ou UI de batalha.
