# Zorykai Raids 0.1.30-events.1

[Baixar JAR](https://github.com/istwi/Shirokai-Raids/raw/refs/heads/main/releases/v0.1.30-events.1/Zorykai-Raids-0.1.30-events.1-fabric-1.21.1.jar) · [Pacote completo: JAR, código, instruções e testes](https://github.com/istwi/Shirokai-Raids/raw/refs/heads/main/releases/v0.1.30-events.1/zorykai-raids-0.1.30-events.1-completo.zip) · [Código-fonte](https://github.com/istwi/Shirokai-Raids/raw/refs/heads/main/releases/v0.1.30-events.1/zorykai-raids-0.1.30-events.1-codigo.zip)

Fabric Minecraft 1.21.1, Java 21, Cobblemon 1.8.3. Base: distribuição completa 0.1.29-receipts.1, preservando a personalização Zorykai.

Adiciona seleção da raridade atual/próxima e nove eventos: Chuva de Shinies, Genética Perfeita, Caçada Recompensada, Fortuna de Tokens, Invasão Lendária, Raid Elemental, Votação da comunidade, Boss Enfurecido e Dupla Ameaça.

Eventos são ativados por administrador para a próxima raid; não são sorteados automaticamente. `/raid eventos` consulta a programação. `/raid evento cancelar` cancela apenas programação futura. As recompensas continuam com resgate manual e verificação de recibos; a Dupla Ameaça aguarda capturas e retornos antes da segunda etapa.

- [Instruções e todos os comandos](INSTRUCOES.md)
- [Resultados e limites dos testes](TESTES.md): 166 testes unitários e 360 checks em Fabric isolado, incluindo dois reinícios forçados. Sem teste visual ou aplicação no servidor de produção.
- [Auditoria do empacotamento](AUDITORIA.json)
- [Configuração de tempos de votação e dupla](events.json)

Os bônus de recompensa atuam em `rewards-v2`; as tabelas legadas por peso ficam intactas. `rewards-v2/integrations.json` com `enabled=true` e `legacyItems=false` usa apenas os sorteios novos. Não apague `rewards/`: os arquivos ainda são carregados. Alterações de tabela valem para próximas raids; uma raid iniciada preserva seu snapshot.

Pare o servidor e faça backup da configuração e dos dados antes de substituir o JAR antigo. Instale somente uma versão do mod. O ZIP do código inclui as fontes alteradas, os testes, as ferramentas de patch e o binário base fixado; não reconstrua a distribuição recente usando somente as fontes históricas da raiz do repositório.

SHA-256 do JAR: `347d0e9c623d1cd631059467cc32c8360a302be5c3a5b460fcc8758c34886160`.
