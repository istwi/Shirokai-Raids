# Validação 0.1.10-dev

Pré-release de desenvolvimento. Atualiza a formatação de tempo da 0.1.9-dev para horas, minutos e segundos, como `1h 05min 30s`, no menu, BossBar, avisos, status e histórico. Os valores internos e argumentos administrativos continuam em segundos.

## Verificação

- Compilação e 67 testes JUnit aprovados localmente; testes do formatador incluem zero, negativos, segundos, transições de minuto/hora e durações acima de 24 horas.
- Verificação de metadados, bytecode Java 21, estrutura e SHA-256 do JAR pelo script `verify_jar.py`.
- O build do GitHub Actions associado à pré-release executa os testes em Linux e Windows, a auditoria Showdown e a simulação de três sessões.

As limitações de validação com clientes reais e capacidade da [0.1.9-dev](VALIDACAO_0.1.9-dev.md) continuam aplicáveis. Não é uma versão estável.
