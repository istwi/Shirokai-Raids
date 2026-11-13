# Validação local — 0.1.15-dev

- Build aprovado; 83 testes JUnit, sem falhas, erros ou casos ignorados. Inclui migração dos padrões de mensagens e do sufixo legado do boss, preservando textos personalizados.
- Captura dedicada: 51 verificações aprovadas, incluindo equipe de contorno ciano, cópia privada, R, BossBar individual, captura nativa, mensagens com prefixo, bolas esgotadas e prazo absoluto.
- Combate coletivo: três sessões controladas; boss com contorno vermelho, vitória, execução dos efeitos e limpeza da entrada de equipe ao remover o boss. Regressões de movimentos, PP, troca e contribuição aprovadas.
- Recompensas: seis cenários de recuperação; menu: 105 verificações de cliques; operações: 105 verificações de cliques pendentes. Entrega parcial/offline/login e proteção contra duplicação aprovadas.
- JAR validado por `scripts/verify_jar.py --version 0.1.15-dev`.

Resultados locais: `/workspace/shared/raids-capture-test.hDAEq0WC` e `/workspace/shared/raids-challenge-test.5XeRWePt`.

Os testes de servidor usam FakePlayers, sem clientes Minecraft reais. Não comprovam a aparência dos contornos, partículas, títulos e BossBars no cliente. A capacidade para 100 jogadores não foi medida. Artefato local de desenvolvimento.
