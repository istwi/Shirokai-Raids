# Zorykai Raids 0.1.29-receipts.1

[Baixar JAR corrigido](https://github.com/istwi/Shirokai-Raids/raw/refs/heads/main/releases/v0.1.29-receipts.1/Zorykai-Raids-0.1.29-receipts.1-fabric-1.21.1.jar) · [Código e testes](https://github.com/istwi/Shirokai-Raids/raw/refs/heads/main/releases/v0.1.29-receipts.1/zorykai-raids-0.1.29-receipts.1-codigo.zip)

Fabric Minecraft 1.21.1, Java 21, Cobblemon 1.8.3. Montado sobre a distribuição completa 0.1.28-manualrewards.1.

## Problema tratado

No caso relatado, o fragmento havia sido recebido e a tag `shirokai_modular:<transação>` existia no jogador, mas a fila permanecia em `UNCERTAIN`. O log enviado não continha a exceção original. Esta versão corrige a divergência quando existe um recibo verificável, sem supor que todo erro significa entrega ou ausência de entrega.

## Correção

- Itens e fragmentos nativos podem confirmar o recibo já salvo no arquivo NBT do destinatário. A conferência exige o UUID/transação exatos; não se baseia em encontrar um item parecido no inventário.
- Se o recibo existe somente na memória do jogador, é permitido salvar/verificar o inventário atual sem inserir qualquer outro item. Inventário temporariamente bloqueado pela raid aguarda liberação.
- Com prova confirmada, a fila grava `DONE` sem chamar novamente o provedor de entrega. O prêmio desaparece da tela de pendências; um aviso informa que foi apenas uma confirmação, sem reenvio.
- Entregas normais também atualizam o menu aberto após a conclusão. A tela não mantém o ícone de um prêmio já entregue até o jogador atualizar manualmente.
- `DONE` e os avisos de sucesso só são emitidos depois de salvar o estado. Se essa gravação falhar, a memória não mantém um falso sucesso; a reserva e o recibo permanecem para recuperação.
- O arquivo do jogador é sincronizado após a verificação do recibo. Isso melhora a ordem durável entre salvar o inventário e confirmar o journal.
- A manutenção verifica uma prova por ciclo, apenas para destinatários online, com intervalo por transação e rotação da fila. Falhas repetidas não geram uma linha por tick.

## Limites de segurança

Recibo ausente, arquivo ilegível/corrompido, UUID divergente ou salvamento recusado não são interpretados como entrega. Esses casos permanecem protegidos em conferência. Não é possível prometer ausência de erros de disco ou falhas de outros mods; o objetivo é recuperar com evidência e não duplicar.

Moedas e provedores sem uma API de recibo autoritativa não são confirmados por uma tag de inventário. Incerteza de pagamento continua exigindo conferência no provedor. A criação/assinatura de fragmentos pelo Crates não foi alterada; a recuperação apenas consulta o recibo do protocolo de entrega existente.

A política manual da 0.1.28 continua: raids sorteiam e guardam os prêmios; o jogador solicita a entrega em `/raid recompensas`. A confirmação de um recibo antigo não é uma nova entrega automática. Novos prêmios sem recibo continuam aguardando pedido explícito.

## Instalação

1. Pare o servidor e faça backup do JAR, da configuração e de `<mundo>/data/shirokai-raids/`.
2. Substitua o JAR antigo por `Zorykai-Raids-0.1.29-receipts.1-fabric-1.21.1.jar`; não deixe duas versões instaladas.
3. Preserve o `state.json`, os dados dos jogadores e os JSONs de recompensas. Não remova tags/recibos para tentar liberar uma pendência.
4. Inicie e entre com o jogador. Se a pendência ainda existir e o recibo puder ser comprovado, a manutenção fará a conciliação. Solicitar o resgate também solicita a verificação, respeitando o intervalo.

Se você já confirmou manualmente a transação antiga, ela continuará concluída. Não é necessário desfazer essa confirmação. Não há mudança nas chances, quantidades ou destinatários dos sorteios.

## Validação e escopo

Os relatórios anexos registram testes unitários e ensaios em servidor Fabric isolado com jogadores simulados, NBT e inventários reais. A fixture de fragmento testa o protocolo de recibos e a confirmação do provedor, sem emitir um token Crates assinado. A fabricação de tokens, o modpack completo e a interface em cliente humano não foram ensaiados nesta alteração.

A auditoria binária confirma a preservação da classe principal completa da 0.1.28, dos retornos, autenticação, batalha/captura, avisos de dano, catálogo e provedor Impactor. A entrega contém código e testes para reproduzir a montagem sobre o JAR completo anterior; não substitui o mod por uma compilação incompleta das fontes antigas.

Nenhuma mudança foi aplicada ao servidor de produção. O erro separado entre Core e Essentials (`cosmeticsProtocol`) não faz parte desta correção.

### Resultado final

151 testes JUnit e 319 verificações em Fabric passaram: 18 de recibos/menu, 25 de resgate manual, 252 de entrega/recibos de itens, 19 de catálogo e 5 em duas JVMs com SIGKILL. A falha injetada ocorreu depois do salvamento real do recibo e antes do DONE do journal; o arquivo ficou IN_FLIGHT e a nova JVM concluiu sem entregar mais quatro maçãs. Os ensaios usaram exatamente o JAR com SHA-256 `3b51d32e0409a9110e5285fccf206318d0aed303a23ef146355a0ce44d8b9002`.
