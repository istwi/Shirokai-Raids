# Zorykai Raids 0.1.31-eventdisplay.1

[Baixar JAR](https://github.com/istwi/Shirokai-Raids/raw/refs/heads/main/releases/v0.1.31-eventdisplay.1/Zorykai-Raids-0.1.31-eventdisplay.1-fabric-1.21.1.jar) · [Pacote completo: código, instruções e testes](https://github.com/istwi/Shirokai-Raids/raw/refs/heads/main/releases/v0.1.31-eventdisplay.1/zorykai-raids-0.1.31-eventdisplay.1-completo.zip) · [Código-fonte](https://github.com/istwi/Shirokai-Raids/raw/refs/heads/main/releases/v0.1.31-eventdisplay.1/zorykai-raids-0.1.31-eventdisplay.1-codigo.zip)

Fabric Minecraft 1.21.1, Java 21, Cobblemon 1.8.3. Mantém os eventos da 0.1.30, resgate manual e confirmação de recibos.

- Mensagens dos eventos usam o prefixo configurado das outras mensagens. Configurar bônus gera resposta privada ao staff, sem anunciar cada alteração globalmente; o início da raid especial tem um aviso curto.
- Boss bar alterna 4 segundos de evento e 8 segundos do texto normal, sem alterar progresso/espectadores. Tempos e templates configuráveis.
- Configuração, início e consulta ficam em `/raidadmin`, com sugestões no TAB para eventos e parâmetros. `/raid votar` continua público.
- Helds competitivos da lista protegida não se repetem na próxima vitória elegível do jogador, podendo voltar na seguinte. No máximo uma unidade do mesmo held por raid; proteção compartilhada entre V2 e legado e persistida após reinício. Demais prêmios mantidos; pendências antigas não são recalculadas.

[Instruções completas](INSTRUCOES.md) · [Resultados e limites dos testes](TESTES.md) · [Auditoria](AUDITORIA.json).

179 testes unitários e 402 checks em seis fixtures Fabric aprovados, incluindo dois SIGKILL entre três JVMs. Sem validação visual com cliente humano ou alteração do servidor de produção.

Pare o servidor, faça backup e substitua somente o JAR anterior. Mantenha as configurações/dados existentes. `NOVAS-MENSAGENS.json` contém chaves opcionais para mesclar em `messages.json`; não substitua o arquivo inteiro por esse trecho.

SHA-256 do JAR: `e1c2d4e8bb3963112985203842c58f41d96efb42707f537623e262912f8b7f16`.
