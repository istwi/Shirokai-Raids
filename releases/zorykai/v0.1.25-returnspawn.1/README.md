# Zorykai Raids 0.1.25-returnspawn.1

Quando o local anterior estiver inválido, obstruído ou indisponível, tenta retornar ao spawn configurado no Shirokai Essentials. Sem esse provedor/posição, usa o spawn global do Overworld. Uma posição explícita opcional pode atender outros provedores. O retorno só é concluído e os comandos liberados depois da confirmação do teleporte e do salvamento.

Com o servidor parado, substitua o JAR 0.1.24 pelo novo e mantenha apenas uma versão. Faça backup do mundo e da configuração. Minecraft 1.21.1, Fabric, Java 21 e Cobblemon 1.8.3. Não precisa trocar o quiz nem instalar o mod no cliente.

[Funcionamento, configuração e testes](../../../docs/tests/returnspawn-0.1.25/README.md). Autenticação e proteções do Guard continuam sendo respeitadas. Se houver outra condição pendente, `/raid sair` passa a mostrar o motivo específico. A fixture de testes não acompanha este JAR.

Validação: 129 testes automatizados, 100 verificações de retorno e 87 de captura/quiz aprovados com jogadores simulados.
