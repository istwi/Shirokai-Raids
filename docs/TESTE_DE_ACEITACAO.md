# Critério atual: três clientes com batalhas tradicionais

**Aceite com clientes reais pendente.** O combate tradicional e os controles operacionais estão implementados na versão de desenvolvimento; teleporte com FakePlayer, conexão inerte e probes do simulador não substituem este roteiro.

Prepare a instalação conforme `PREPARAR_SERVIDOR.md`. Registre resultados observados, incluindo falhas; não marque um requisito como aprovado com base apenas nos testes automáticos.

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

## Controles operacionais e menus

14. Configure lobby e arena por comando, com orientações diferentes. Confira `/raid entrar`, `/raid lobby`, cooldown, destino sem piso, região fora da borda e dimensão inválida. Observe também teleporte entre duas dimensões existentes.
15. Deixe o local sem jogadores durante um evento e confirme que o boss permanece funcional. Cancele/derrote e confirme a liberação dos tickets próprios, preservando `/forceload` externo. Reinicie com boss antigo salvo e confirme a limpeza apenas das entidades marcadas pelo mod.
16. Confira desativação/ativação automática, intervalo/duração/limite, avisos de 15/5/1 minutos e ambos os modos de reinício. Não devem surgir rajadas de avisos antigos nem várias raids acumuladas.
17. Abra histórico, ranking e pendências com clientes normais. Confira paginação, privacidade do histórico, persistência do ranking e ausência de ícones retirados por shift, arraste, teclas de hotbar/offhand, duplo clique e descarte. Use o botão de entrega com inventário cheio/parcial e reconecte.
18. Com LuckPerms real, teste permissões concedidas/negadas, jogadores comuns sem administração e reconciliação restrita. Com SkiesClear real, teste as duas blacklists e a sobrevivência apenas do boss marcado. Não modifique o GTS-Shirokai.
19. Apenas em backup descartável, confira reserva antiga comprovadamente recebida e outra comprovadamente não recebida. Registre o motivo; confirme que repetir a decisão não repete itens e que um proprietário desconhecido não recebe devolução automática.

## Ensaio progressivo de capacidade

Comece com 1 e 3 clientes; avance para 10, 25, 50 e 100 combatentes apenas após cada etapa funcionar. Ajuste `maxConcurrentPlayers` e o limite vanilla do servidor para a etapa. Todos precisam estar efetivamente em sessões independentes, escolhendo movimentos; inscritos ociosos não comprovam capacidade de combate.

Registre duração, hardware, heap, MSPT médio/percentis, TPS, CPU, latência de ações, sessões simultâneas e erros. `/raidadmin status` fornece MSPT médio/heap/sessões como auxílio. Compare entrada tardia, desconexões, consumo de PP, encerramento e entregas em cada escala. Uma etapa falha não é suporte aprovado para a seguinte.

Não houve ensaio com clientes reais nem capacidade comprovada para 100 pessoas. Anexe resultados reais, versões de mods e configuração antes de considerar uma Release estável.
