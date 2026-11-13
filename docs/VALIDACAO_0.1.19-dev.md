# Validação local — 0.1.19-dev

- Build Java 21 aprovado e 122 testes JUnit sem falhas, erros ou casos ignorados. Inclui migração dos padrões de mensagem, ausência da chance na introdução e contração positiva/contínua.
- Transição dedicada: 89 verificações aprovadas no servidor real. Boss permanece e contrai antes de desaparecer; cópias não aparecem na contração nem na pausa; ticket permanece até a revelação. Dano/desafio bloqueados no boss visual, nova raid recusada durante o efeito, cancelamento limpa entidade/ticket/direitos, saída e callback de desconexão removem chance pendente, prazo não é renovado e chat informa tempo restante sem chance por bola.
- Captura dedicada: 53 verificações aprovadas, incluindo remoção de direitos não iniciados ao sair/expirar, além de R manual, cópia privada, BossBar, animação, resultados, bolas e prazo absoluto.
- Regressão coletiva passou: três sessões nativas, golpes, PP, troca e contribuição; seis cenários de recuperação de recompensas, 105 verificações de menu, 206 de prévia administrativa e 105 de operações pendentes.

Resultados locais:

- `/workspace/shared/raids-victory-test.G9KJ9u6R`
- `/workspace/shared/raids-capture-test.0JLQy2Vb`
- `/workspace/shared/raids-challenge-test.Yw1Cv4Yc`

Os testes usam servidores Minecraft reais com FakePlayers. Validam estado, cronologia e chamadas nativas; não comprovam a aparência da animação num cliente humano. Capacidade para 100 clientes não foi medida.

Na execução de captura, o servidor concluiu com resultado JSON aprovado; o wrapper encontrou erro ao retomar porque o script foi atualizado enquanto estava em execução. O resultado nativo de 53 verificações foi conferido separadamente; `bash -n` confirmou o script final e o teste dedicado posterior de vitória passou com o wrapper final.
