# Validação 0.1.32-eventnames.1 — 24/11/2026

JAR SHA-256: `7fef85911526c49d8c41139f70f9ddb062df1b1416d166f16834be9fdf7a22f1`.

Build Gradle/JDK 21 concluído com sucesso. **179 testes JUnit aprovados**, sem falhas, erros ou ignorados. **113 checks nativos aprovados** em três fixtures Fabric, todas carregando exatamente o JAR acima com `-Xverify:all`, Minecraft 1.21.1 e Cobblemon 1.8.3.

- **EventDisplayIntegration: 42 checks.** Novos nomes na boss bar real e prefixo de chat, sugestões no TAB, staff/publicação de voto, mensagens de configuração privadas, retorno ao componente normal após 4s, ausência de nova exibição aos 12s e 59,999s e próximo evento aos 60s. Verifica barra ativa, barra de próxima raid e preservação de progresso/espectadores. Também mantém verificações do cooldown de helds na V2 e legado, quantidade unitária e consumíveis.
- **EventsIntegration: 61 checks.** Regressão dos nove eventos, snapshots, IVs no boss/alvo de captura, raridades, tipos, voto, resgate manual e portas de segurança da sequência. Quatro checks adicionais verificam o Desafio Duplo sem raridades fixas, primeiro boss real sorteado, segundo sorteio independente com exclusão da primeira espécie e segundo boss real diferente no catálogo padrão. A versão com duas raridades explícitas continua testada.
- **EventsRestartIntegration: 10 checks.** Três JVMs e dois SIGKILL. Mantém prêmios, retorno offline, cooldown de helds e modificadores armados; não recria boss/captura nem retoma uma sequência interrompida.

A comparação com o JAR 0.1.31 confirma que somente cinco classes de eventos/comandos/apresentação e a versão em `fabric.mod.json` mudaram. Todas as demais entradas são idênticas byte a byte, incluindo a classe principal completa, captura, retorno, Guard, autenticação, combate tradicional, journal, recibos e política de held items. O arquivo `COMPARACAO-0.1.31.json` registra essa conferência. Os 402 checks da 0.1.31 pertencem àquela versão anterior e não foram contados novamente como execução desta entrega.

O teste da barra usa tempo controlado para verificar precisamente o limite de um minuto. Jogadores são simulados e as entidades/servidor são reais; não houve observação por cliente humano, teste do modpack completo ou alteração em produção. Isso não comprova ausência total de bugs. Os testes e comparações não demonstram uma captura humana real terminando e retornando; esse fluxo permanece o mesmo código da versão anterior.

Arquivos JSON por fixture, XML JUnit e log do build estão em `testes/`. A configuração é opcional: arquivos existentes sem `eventBarIntervalSeconds` recebem o padrão de 60s, mesmo que ainda tenham o antigo `normalBarSeconds=8`.
