# Zorykai Raids 0.1.32-eventnames.1

[Baixar JAR](https://github.com/istwi/Shirokai-Raids/raw/refs/heads/main/releases/v0.1.32-eventnames.1/Zorykai-Raids-0.1.32-eventnames.1-fabric-1.21.1.jar) · [Pacote completo: código, instruções e testes](https://github.com/istwi/Shirokai-Raids/raw/refs/heads/main/releases/v0.1.32-eventnames.1/zorykai-raids-0.1.32-eventnames.1-completo.zip) · [Código-fonte](https://github.com/istwi/Shirokai-Raids/raw/refs/heads/main/releases/v0.1.32-eventnames.1/zorykai-raids-0.1.32-eventnames.1-codigo.zip)

Fabric Minecraft 1.21.1, Java 21, Cobblemon 1.8.3.

- Nomes escolhidos: Sorte Shiny, IVs Perfeitos, Drop Turbinado, Tokens Extras, Chamado Lendário, Raid Elemental, Voz dos Treinadores, Boss Enfurecido e Desafio Duplo.
- Boss bar: 4 segundos de aviso e 56 segundos do título normal, um ciclo por minuto. Não envia mensagens periódicas no chat. O novo campo `eventBarIntervalSeconds=60` prevalece sobre o campo legado `normalBarSeconds`.
- `/raidadmin evento dupla` sorteia dois bosses pelo catálogo, sem fixar lendário/supremo. Evita repetir a primeira espécie quando existem outras candidatas válidas. Categorias explícitas e `aleatorio` continuam disponíveis no TAB.
- Preserva retorno após captura, intervalo de segurança da segunda etapa, comandos de staff, voto público, cooldown de held items e resgate manual de recompensas.

[Instruções completas](INSTRUCOES.md) · [Testes e limitações](TESTES.md) · [Comparação com 0.1.31](COMPARACAO-0.1.31.json).

179 testes unitários e 113 checks em Fabric isolado aprovados, incluindo dois SIGKILL entre três JVMs. Sem validação visual com cliente humano ou aplicação no servidor de produção.

Pare o servidor, faça backup e substitua somente o JAR anterior. Mantenha as configurações e dados existentes. Campos novos usam padrões quando ausentes; não é preciso apagar arquivos.

SHA-256 do JAR: `7fef85911526c49d8c41139f70f9ddb062df1b1416d166f16834be9fdf7a22f1`.
