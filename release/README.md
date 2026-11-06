# Processo de publicação

## Pré-releases de desenvolvimento

Versões `X.Y.Z-dev` podem ser distribuídas em Releases como **pré-releases instáveis**, sem declarar aceite funcional ou capacidade. O workflow `Publicar pre-release de desenvolvimento` recebe o ID de um build bem-sucedido da main e reutiliza o JAR Linux desse build, no commit exato validado. Não recompila nem altera a versão.

Execute pelo GitHub Actions ou `gh workflow run prerelease.yml -f run_id=<ID>`. O workflow confere origem do build, checksum original, metadados e estrutura do JAR; cria a tag imutável `dev-X.Y.Z` no commit validado e só publica após verificar o JAR e `SHA256SUMS` anexados. Publicações de desenvolvimento são marcadas como pre-release e não como Latest. Uma versão já publicada não é sobrescrita nem tem sua tag movida; reexecuções só aceitam arquivos de hash idêntico.

O Actions continua executando os testes; os arquivos para download ficam na página Releases. Permissões de publicação ficam no job desse workflow, sem tokens no código. A política e o workflow de versões estáveis abaixo continuam separados.

## Releases estáveis

Nenhuma versão funcional foi aprovada ainda. Não crie v1.0.0 agora.

Após os testes automatizados e o teste real com três jogadores em servidor dedicado:

1. Atualize `mod_version` em gradle.properties para a nova versão SemVer.
2. Crie `release/vX.Y.Z.md` em português com seções: Novas funcionalidades, Bugs corrigidos, Configurações, Compatibilidade e Atualização.
3. Registre em `release/vX.Y.Z.json`: approved (boolean), three_player_dedicated_server_test (boolean), cobblemon ("1.8.3") e evidence (descrição e referência ao relatório real de teste). Também são obrigatórios combat_mode="TRADITIONAL_MULTIPLAYER" e os booleanos normal_battle_key, traditional_move_ui, late_join, global_hp e all_sessions_closed. Nunca marque um teste não executado como aprovado.
4. Revise e envie o commit para istwi/Shirokai-Raids.
5. Crie e envie uma tag nova `vX.Y.Z` no commit aprovado. Nunca mova uma tag publicada.

O workflow valida versão, aceite, build, testes e JAR antes de criar uma Release em rascunho. Publica somente após anexar JAR e SHA256SUMS. Reexecuções verificam hashes e recusam sobrescrever conteúdo divergente. A permissão contents:write fica restrita ao job de publicação; não há tokens no código.

Builds comuns de desenvolvimento não publicam automaticamente. A publicação de uma pré-release é acionada explicitamente pelo workflow acima. Os critérios de v1.0.0 continuam obrigatórios para uma Release estável.
