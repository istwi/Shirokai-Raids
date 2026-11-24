# Zorykai Raids 0.1.33-autoevents.1

[Baixar JAR](https://github.com/istwi/Shirokai-Raids/raw/refs/heads/main/releases/v0.1.33-autoevents.1/Zorykai-Raids-0.1.33-autoevents.1-fabric-1.21.1.jar) · [Pacote completo: código, configuração e testes](https://github.com/istwi/Shirokai-Raids/raw/refs/heads/main/releases/v0.1.33-autoevents.1/zorykai-raids-0.1.33-autoevents.1-completo.zip) · [Instruções completas](INSTRUCOES.md)

Sorteio de evento para a próxima raid, depois do encerramento e da liberação das capturas/retornos.

- **1% fixo**, configurável; sem acúmulo, escalonamento ou garantia após muitas tentativas. Um teste global por encerramento elegível, não por jogador/tipo/tick.
- Vitória ou tempo esgotado com participação são elegíveis; cancelamento administrativo/reinício não cria tentativas. Desafio Duplo só considera o encerramento completo.
- Se sair evento, salva e anuncia uma frase curta do Arceus. O boss é sorteado no horário normal da próxima raid; não inicia raid extra.
- Escolha os tipos permitidos e seus valores em `config/shirokai-raids/events.json`, bloco `automatic`. `enabled` liga/desliga cada tipo; `chancePercent` controla os 1% globais; `weight` distribui a escolha entre os habilitados.
- Recarregue com `/raidadmin evento reload`. Consulte com `/raidadmin eventos`. Configuração manual tem prioridade e não é sobrescrita.

`events-exemplo.json` mostra o arquivo completo com os padrões. O bloco ausente é acrescentado automaticamente aos arquivos antigos; não é necessário substituir suas configurações. Veja `INSTRUCOES.md` para valores, elegibilidade, snapshots e desativação.

Exemplo de anúncio:

> ✦ ZORYKAI RAID » Arceus sorriu para os treinadores! A próxima raid terá Sorte Shiny.

189 testes unitários e 167 checks em Fabric isolado aprovados; consulte `TESTES.md` para evidência e limites. Sem validação visual com o modpack de produção.

Pare o servidor, faça backup e substitua o JAR antigo por esta versão. Mantenha configurações e dados; não restaure um backup antigo de recompensas sobre prêmios já resgatados. Sem alterações no servidor de produção nesta entrega.

SHA-256 do JAR: `38d5ddec703ba338213d735e8f5c27a227e71aad25e94807a7d5a67572668fb6`.
