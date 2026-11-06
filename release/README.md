# Processo de publicação

Nenhuma versão funcional foi aprovada ainda. Não crie v1.0.0 agora.

Após os testes automatizados e o teste real com três jogadores em servidor dedicado:

1. Atualize `mod_version` em gradle.properties para a nova versão SemVer.
2. Crie `release/vX.Y.Z.md` em português com seções: Novas funcionalidades, Bugs corrigidos, Configurações, Compatibilidade e Atualização.
3. Registre em `release/vX.Y.Z.json`: approved (boolean), three_player_dedicated_server_test (boolean), cobblemon ("1.8.3") e evidence (descrição e referência ao relatório real de teste). Também são obrigatórios combat_mode="TRADITIONAL_MULTIPLAYER" e os booleanos normal_battle_key, traditional_move_ui, late_join, global_hp e all_sessions_closed. Nunca marque um teste não executado como aprovado.
4. Revise e envie o commit para istwi/Shirokai-Raids.
5. Crie e envie uma tag nova `vX.Y.Z` no commit aprovado. Nunca mova uma tag publicada.

O workflow valida versão, aceite, build, testes e JAR antes de criar uma Release em rascunho. Publica somente após anexar JAR e SHA256SUMS. Reexecuções verificam hashes e recusam sobrescrever conteúdo divergente. A permissão contents:write fica restrita ao job de publicação; não há tokens no código.

Builds de desenvolvimento não criam Releases. A versão de desenvolvimento atual é 0.1.1-dev enquanto os critérios de v1.0.0 não forem cumpridos.
