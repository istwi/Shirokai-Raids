# 0.1.24-quizguard.1 — quiz bloqueado durante a raid

Enquanto existir a participação salva da raid, inclusive combate, transição e captura, o CRZquiz não aceita respostas desse jogador. A verificação ocorre antes de registrar resposta, vencedor, estatísticas ou recompensa. Uma resposta bloqueada não encerra a pergunta para os demais. Ao sair/recuperar o retorno e remover a participação, o jogador volta a ser elegível.

Mensagem editável em `messages.json`: `quiz.bloqueado_na_raid`. O bloqueio é específico ao CRZquiz 1.0.0 enviado; não bloqueia globalmente `/give` nem recompensas de todos os mods. O resgate das recompensas da própria raid não foi alterado. O quiz continua mostrando perguntas globais; esta alteração bloqueia a participação, não oculta mensagens.

Implementação original no mod de raids, com Mixin opcional em `QuizManager.processAnswer`. O JAR do quiz não foi alterado e não é redistribuído. Sem quiz instalado, o servidor continua iniciando. A integração depende do método dessa versão; outras versões de quiz precisam ser validadas.

Mantém as correções de captura 0.1.23 e a base binária completa 0.1.22, conforme [relatório anterior](../capturefix-0.1.23/README.md). A auditoria agora também lista classes novas adicionadas.

## Validação

Build Java 21, Minecraft 1.21.1, Fabric Loader 0.17.2, Fabric API 0.116.6+1.21.1, Cobblemon 1.8.3 e JAR original CRZquiz 1.0.0.

A fixture dedicada usa modo `-Dshirokai.integration=true -Dshirokai.integration.mode=quizguard`. Em dois momentos (antes da revelação da captura e durante a batalha privada), envia uma resposta sintética correta ao processamento real do quiz com participação registrada: nenhum vencedor, nenhum prêmio, pergunta continua ativa. Remove temporariamente a participação e envia a mesma resposta novamente: quiz aceita e executa `/give`. Restaura a participação para continuar a regressão nativa de captura, PC/healer, bolas, prazo e retorno. Isso verifica a fronteira de elegibilidade sem depender de entrada/saída visual pelo menu. Não há clientes gráficos reais nem teste de chat assinado.

Também foi iniciado um servidor dedicado com o quiz e a fixture removidos: chegou ao estado `Done`, confirmando que a integração é opcional. Pode aparecer um aviso de classe alvo ausente do Mixin quando o quiz não está instalado; não impede a inicialização.

Resultados: **129 testes JUnit**, zero falhas; **87 verificações dedicadas**, aprovadas; inicialização sem quiz aprovada. Os resultados JSON acompanham este relatório. Os limites do teste do desmaio simultâneo continuam sendo os descritos na versão anterior.
