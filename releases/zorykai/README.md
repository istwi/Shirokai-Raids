# Zorykai — correção de interações na arena

Versão 0.1.20-guard.1 com correção de PC/healer e blocos fantasmas.

- PC e máquina de cura do Cobblemon liberados para participantes fora de batalhas e da fase de captura. Recuperação pendente de inventário antigo continua bloqueada.
- Quebra de blocos continua impedida. Pacotes de quebra cancelados agora confirmam a sequência de previsão e reenviam o estado do bloco ao cliente.
- Visual e cabeçalhos Zorykai da atualização `dae70d1` preservados no JAR.

Instale este JAR no lugar do anterior, mantendo somente um JAR do mod, e reinicie.
IDs, comandos, permissões e caminhos de configuração permanecem originais.
Mensagens personalizadas existentes no servidor continuam prevalecendo.

Compilação e 129 testes passaram na correção. O comportamento visual e as
interações com PC/healer ainda precisam de validação com cliente Minecraft real.
Confira a integridade com `sha256sum -c SHA256SUMS.txt` nesta pasta.

As correções de lógica estão no código-fonte. O visual Zorykai é aplicado ao
JAR distribuído; recompilar apenas o código-fonte gera os textos originais.
