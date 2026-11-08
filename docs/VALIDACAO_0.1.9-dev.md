# Validação 0.1.9-dev

Pré-release de desenvolvimento; não é uma versão estável.

## Funcionalidades

`/raid` e `/raid menu` mostram informações do evento. Entrar salva a dimensão, posição e orientação anterior em `state.json` antes do teleporte seguro à arena de `/raidadmin setspawn`. `/raid sair` ou o botão de saída encerra a batalha e retorna à origem; a posição permanece disponível após desconexão, reinício e término do evento.

Combate exige entrada e presença dentro de `arenaRadius`. Ao sair da área, a sessão é encerrada; danos fora dela são recusados. Outros comandos ficam bloqueados durante a participação, exceto `/raid`, `/raid menu` e `/raid sair`. O bloqueio termina ao retornar. Fechar o menu não encerra a participação.

Contadores de evento, BossBar, avisos e histórico usam minutos decimais. Campos JSON e argumentos administrativos de tempo continuam em segundos.

## Evidência automatizada

- `bash gradlew --no-daemon build`: compilação e testes aprovados em Linux antes da publicação.
- 67 testes JUnit, sem falhas ou testes ignorados: incluem persistência de dimensão/posição/orientação, rejeição de coordenadas inválidas, compatibilidade de estados antigos, formatação em minutos e lista de comandos permitidos/bloqueados.
- `python3 scripts/verify_jar.py --version 0.1.9-dev`: metadados, bytecode Java 21, estrutura e SHA-256 do JAR.
- Fixtures de teleporte e desafio atualizadas e compiladas para o novo fluxo; não executadas nesta alteração, pois o servidor de testes com EULA aceita não estava preparado.

O build do GitHub Actions associado à pré-release executa também a auditoria Showdown e a simulação de três sessões.

## Limites

Menu, retorno por teleporte, interceptação de comandos em execução e desafio ainda precisam de teste em servidor dedicado com clientes reais. Não há aceite funcional com três jogadores nem capacidade comprovada para 100 pessoas. Não publicar como versão estável.
