# Validação 0.1.12-dev

Pré-release de desenvolvimento; não é uma versão estável.

## Alterações

- Formatação de chat e títulos de BossBar com códigos de cor, estilo, reset e hexadecimal. Placeholders existentes preservados.
- Contagem regressiva pública com barra proporcional ao intervalo até a próxima raid automática; substitui os avisos periódicos no chat.
- Barra de HP e alerta de pouca vida preservados para participantes na arena.

## Evidência

- Build local e 75 testes JUnit passaram, sem falhas ou testes ignorados.
- Novos testes de cores, negrito, reset, hexadecimal, códigos desconhecidos e progresso da contagem com prazo vencido ou intervalo alterado.
- Auditoria do simulador oficial: 8 verificações passaram.
- Probe tradicional: três sessões Showdown com coordenador Java; dano efetivo global de 2000, entrada tardia, PP e encerramento único validados.
- Verificação de estrutura, metadados, bytecode Java 21 e SHA-256 do JAR por `scripts/verify_jar.py`.
- A publicação reutiliza o JAR Linux do build CI do commit exato após o sucesso da matriz Linux/Windows.

## Limites

A aparência das cores e BossBars precisa de conferência com cliente Minecraft real. Não foi executado novo servidor de integração nesta alteração. Não há aceite com três clientes reais nem capacidade comprovada para 100 pessoas. As limitações anteriores continuam aplicáveis.
