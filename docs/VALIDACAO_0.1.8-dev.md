# Validação 0.1.8-dev — contorno do boss

A entidade pública do boss recebe `setGlowingTag(true)` antes de entrar no mundo. É o estado nativo Glowing do Minecraft; não exige pacote/mod adicional no cliente, efeito de poção temporário ou equipe de scoreboard. A cor não foi personalizada e as equipes existentes não são alteradas. O efeito acompanha a entidade de boss e termina com sua remoção; o código não ativa brilho nos Pokémon comuns.

## Executado

- `bash gradlew --no-daemon build`: sucesso; 63 testes Java, zero falhas, erros ou ignorados.
- `python3 scripts/verify_jar.py --version 0.1.8-dev`: passou.
- `bash scripts/run-teleport-integration.sh`: passou em `/workspace/shared/raids-teleport-test.9bkOK9O3`, servidor Fabric/Cobblemon real. A fixture conferiu `hasGlowingTag()` e `isCurrentlyGlowing()` do boss criado, além dos testes existentes de teleporte/cooldown/colisão e cancelamento.

Nenhum cliente real participou. O estado luminoso foi confirmado no servidor, mas a aparência do contorno no modelo Cobblemon ainda precisa ser observada no jogo. Os demais limites de compatibilidade, persistência e capacidade da 0.1.7-dev permanecem. Não houve alteração de configuração cloud nem publicação de Release estável.

## Artefato

`build/libs/Shirokai-Raids-0.1.8-dev-fabric-1.21.1.jar`

SHA-256: `ff8b0d72a43797171dffd77c02ac91d58be821742838cb425df1634929181389`
