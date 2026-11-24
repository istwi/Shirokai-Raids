# Zorykai Raids 0.1.34-autocontrol.1

[Baixar JAR](https://github.com/istwi/Shirokai-Raids/raw/refs/heads/main/releases/v0.1.34-autocontrol.1/Zorykai-Raids-0.1.34-autocontrol.1-fabric-1.21.1.jar) · [Pacote completo: código, instruções e testes](https://github.com/istwi/Shirokai-Raids/raw/refs/heads/main/releases/v0.1.34-autocontrol.1/zorykai-raids-0.1.34-autocontrol.1-completo.zip)

Comandos novos, somente para staff com `shirokai.raids.admin` ou console:

```
/raidadmin evento automatico pausar
/raidadmin evento automatico ligar
/raidadmin evento automatico status
```

Há sugestões no TAB; sem operação, mostra status. No console, omita a barra.

Pausar salva `automatic.enabled=false` em `config/shirokai-raids/events.json`, descartando oportunidades ainda não sorteadas. A raid ativa, captura, votação e evento já reservado continuam. Ligar salva `true`, mantendo chance e lista configuradas e sem repetir encerramentos do período pausado. Não precisa de reload após esses comandos.

Respostas privadas usam o prefixo existente. Em erro de configuração/leitura/escrita, não confirma alteração não salva. Chance padrão continua 1% fixo; os comandos não alteram o percentual.

[Instruções completas](INSTRUCOES.md) · [Resultados e limitações dos testes](TESTES.md).

Pare o servidor, faça backup e substitua somente o JAR antigo. Mantenha configurações e dados.

189 testes unitários e 100 checks em Fabric isolado aprovados. Veja `TESTES.md` para evidência e limites; sem validação visual ou aplicação em produção.

SHA-256 do JAR: `5d1902d509084293ad9d659802d66caabc4b60cebc8a695eec7c22cdfadeeb11`.
