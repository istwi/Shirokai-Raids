# Validação 0.1.4-dev

Esta versão de desenvolvimento acrescenta configuração de dano por boss e sorteio de drops em duas etapas. Mantém o combate padrão pela tecla de desafio do Cobblemon, somente no servidor. Não é uma Release estável.

## Verificações executadas

- `bash gradlew --no-daemon build`: sucesso; 32 testes Java, zero falhas ou testes ignorados.
- `python3 scripts/run_traditional_probe.py`: 15 verificações do adaptador embarcado no motor oficial com ponte Java simulada; oito callbacks isolados do artefato auditado, seis verificações históricas do motor e três sessões Showdown ligadas ao coordenador Java também passaram.
- `bash scripts/run-challenge-integration.sh`: servidor Fabric/Cobblemon real, três FakePlayers, nenhum cliente Minecraft. Resultado positivo preservado em `/workspace/shared/raids-challenge-test.mW6eirUK` no ambiente de testes.
- `python3 scripts/verify_jar.py --version 0.1.4-dev`: metadados, mixins, recurso JavaScript e exclusão das classes de integração verificados.

A integração conferiu desafio padrão, pacote inicial da interface, movimentos manuais, consumo de PP, troca de Pokémon, entrada tardia, saída, retorno e nova saída por desconexão. O dano anterior permaneceu após retornar e aumentou ao executar outro movimento. O encerramento global somou 3.000 de dano efetivo, fechou as sessões e permitiu uma batalha normal posteriormente.

A fixture usou HP 3.000, escala 2,5, multiplicador 1,5 e uma tabela exclusiva de maçãs. Conferiu a escala na entidade, o multiplicador instalado em cada batalha e quatro maçãs pendentes por contribuinte. Um reload com item inexistente foi rejeitado preservando a tabela válida anterior. Os testes do motor conferiram multiplicador neutro, zero, duplicado, isolamento entre sessões, dano fixo, Focus Sash, troca e dano residual sem multiplicação.

Os testes Java conferiram probabilidades exatas por faixa, pesos de itens, orçamento de drops, compatibilidade com tabelas antigas, serialização e limites de configuração. Arquivos existentes não são migrados nem sobrescritos automaticamente; o exemplo novo é uma opção de migração explícita.

## Limites

Não houve jogadores ou clientes Minecraft reais, observação de animações, nem medição de capacidade para 100 pessoas. Três sessões controladas não comprovam esse limite. Os logs do servidor incluem avisos já conhecidos de datafixers, dados iniciais de jogadores simulados e consulta externa de chaves de autenticação indisponível no ambiente offline.

O multiplicador altera dano direto de movimentos do boss, incluindo dano fixo; status e recoil seguem o motor nativo. As restrições anteriores de cura do boss, Perish Song e auto-sacrifício continuam vigentes. O adaptador requer o serviço Graal padrão.

A contribuição é mantida ao sair e retornar ao mesmo evento; `/raid sair` salva um snapshot. Golpes posteriores continuam sujeitos à persistência periódica em caso de queda abrupta. Os drops ainda usam pendências e resgate pelo comando existente; entrega automática, menu de pendências, recuperação completa após falhas e os demais itens do escopo precisam de etapas posteriores.

## Artefato

`build/libs/Shirokai-Raids-0.1.4-dev-fabric-1.21.1.jar`

SHA-256: `01c55a413a2e768f013d63e10b430fc9c5d29d88e5cf54858224f3da0b353e36`
