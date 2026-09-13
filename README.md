# Bonamassa Android — cliente conectado

App nativo em Kotlin e Jetpack Compose, integrado à [APIBonamassa](https://github.com/nvpetri/APIBonamassa). Mantém a identidade preta, vermelha e dourada da Bonamassa. A versão padrão envia pedidos à API configurada.

## Testar no Android

1. Deixe a API funcionando no PC na porta **3001** e confirme que a loja está aberta no painel.
2. Atualize este repositório e abra a pasta raiz no Android Studio. Use JDK 17 ou 21 no Gradle, SDK Android 35 e Build Tools 35.0.0.
3. Execute a variante **debug** em um Android 8 ou superior. Também há um APK debug nos artefatos da execução do GitHub Actions.
4. No app, abra **Conta → Configurar API**. Informe `http://IPV4-DO-PC:3001` e o identificador `bonamassa` (ou o slug configurado na sua API). Não acrescente `/v1`.
5. No emulador Android Studio, use `http://10.0.2.2:3001`. No celular físico, use o IPv4 do PC na mesma rede Wi-Fi; `localhost` apontaria para o próprio celular.
6. Toque em **Entrar ou criar conta → Criar conta**. Cadastre um cliente com senha de pelo menos 12 caracteres. A conta de gestor usada no painel não é uma conta de cliente.
7. Monte a pizza, confira a sacola, escolha entrega ou retirada e pagamento. Toque em **Conferir valores** e depois **Confirmar e enviar pedido**.
8. No painel conectado à mesma API/loja, aceite e prepare o pedido. A tela do cliente atualiza o status automaticamente enquanto estiver aberta.

Se o celular não conectar, abra `http://IPV4-DO-PC:3001/v1/health` no navegador dele. Confirme o IP atual, a mesma rede, o processo da API e a regra do Firewall do Windows para a porta 3001 na rede privada. Não é necessário publicar a API na internet nem abrir portas no roteador para esse teste.

Para obter esta integração antes do merge:

```powershell
git fetch origin
git switch --track origin/codex/integracao-api
```

Se a branch já existe localmente, use `git switch codex/integracao-api` e `git pull`.

## O que está conectado

- Cadastro, login de cliente e encerramento de sessão.
- Cardápio, disponibilidade, fotos, grupos tradicionais/especiais, bordas, bebidas e combos cadastrados no painel.
- Pizzas pequenas, médias e grandes; inteira ou dois sabores; preço do maior sabor e borda cadastrada.
- Combos com composição inteira/meio a meio definida pela pizzaria e preço próprio, incluindo o desconto em relação aos avulsos.
- Promoções por prazo ou quantidade: seleção na finalização e confirmação de disponibilidade pela API. O resumo informa o desconto e quantas pizzas receberam o benefício.
- Sacola e favoritos persistidos; endereço de entrega salvo após a cotação; retirada sem endereço/taxa.
- Cartão na maquininha ou dinheiro, incluindo cálculo de troco. Não há cobrança online.
- Cotação emitida pela API, revisão explícita do valor e confirmação de envio.
- Histórico paginado, acompanhamento de status e cancelamento antes do aceite da pizzaria.
- Recuperação de envio interrompido sem gerar outra chave ou outro pedido.

O app não injeta produtos demonstrativos quando a API está vazia ou indisponível. As ilustrações de reserva indicam a categoria quando um produto ainda não tem foto.

## Compilar e verificar

```powershell
.\gradlew.bat :core:test :client:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Sem SDK Android, os testes JVM podem ser executados com:

```powershell
.\gradlew.bat -PcoreOnly :core:test :client:test
```

O workflow `Android integrado` compila o APK, executa os testes e o lint, sobe PostgreSQL 17 e a API e roda o fluxo em um emulador Android 35. Os relatórios e o APK ficam como artefatos da execução. O cenário de integração real é opt-in e usa apenas o banco isolado da CI; veja [docs/VERIFICACAO.md](docs/VERIFICACAO.md).

## Configuração de build

O padrão debug usa o endereço do emulador e permite alterá-lo em Conta. Para definir os valores iniciais na compilação:

```powershell
.\gradlew.bat :app:assembleDebug -PbonamassaApiUrl=http://192.168.1.10:3001 -PbonamassaStoreSlug=bonamassa
```

Uma configuração já salva no aparelho tem prioridade. Para trocar, saia da conta e use **Configurar API**. Um envio pendente precisa ser resolvido na conta/servidor originais antes de sair ou trocar de servidor.

Release exige HTTPS, não exibe configuração de servidor nem controles de demonstração. Configure a URL real no build, por exemplo `-PbonamassaApiUrl=https://api.seu-dominio.com.br`. Assinatura de produção e publicação na loja ainda precisam ser configuradas para o ambiente definitivo.

A demonstração offline anterior continua isolada e pode ser aberta explicitamente em debug:

```powershell
.\gradlew.bat :app:assembleDebug -PbonamassaDemo=true
```

Ela usa seus próprios dados locais, não envia pedidos e continua identificada como demonstração. Nenhum carrinho, cupom ou histórico antigo da demo é migrado para a API.

## Limites atuais

Pix online, notificações push, GPS em tempo real, múltiplos endereços e edição/exclusão da conta não fazem parte desta integração. O status é consultado a cada 5 segundos com o app visível, ou 15 segundos após uma falha de conexão. Pedidos continuam no servidor quando o app está fechado.

A API atual define a receita de cada combo no painel; o cliente escolhe a oferta pronta. Sabores e bordas de uma pizza avulsa são personalizáveis. Repetição automática de pedidos antigos não é exposta, pois o recibo histórico não inclui todos os identificadores necessários para reconstruir a receita com segurança.

Contratos, persistência e regras de recuperação: [docs/INTEGRACAO.md](docs/INTEGRACAO.md).
