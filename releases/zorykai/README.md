# Zorykai — variante visual

Base: main, commit 0375de86abdf828b412367cccf5860993254d6af, versão 0.1.20-guard.1.

O JAR desta pasta altera somente os nomes visuais nos campos `name` e `description`
de `fabric.mod.json` e em duas mensagens de `shirokai/messages.json`.
Todas as classes e os demais recursos permanecem idênticos ao JAR original.
O código-fonte não foi alterado; recompilar o projeto gera a variante original.

Instale este JAR no lugar do original; não mantenha os dois no servidor.
IDs, namespaces, comandos, permissões, pacotes e caminhos de configuração
continuam com os nomes técnicos originais para manter compatibilidade.
Mensagens já personalizadas no `messages.json` do servidor continuam prevalecendo.

Confira a integridade com `sha256sum -c SHA256SUMS.txt` nesta pasta.
