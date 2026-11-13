# Build 0.1.13-dev

JAR compilado do código no commit `c17efa088641276b49b70db301defb1ad2bdb7b8`.
Minecraft 1.21.1, Fabric, Java 21 e Cobblemon 1.8.3+1.21.1.

Validação executada:

- `./gradlew --no-daemon build`: 79 testes Java aprovados, sem falhas ou casos ignorados.
- `python3 scripts/run_showdown_audit.py`: 8 verificações do simulador aprovadas.
- `python3 scripts/run_traditional_probe.py`: verificações do adaptador e simulação de três sessões com coordenador Java aprovadas.
- `python3 scripts/verify_jar.py --version 0.1.13-dev`: estrutura, metadados, Java 21 e checksum conferidos.

Artefato de desenvolvimento. Não houve teste com três clientes Minecraft nem
medição de capacidade; este build não representa aprovação de uma versão estável.
O SHA-256 do JAR está em `SHA256SUMS` neste diretório.
