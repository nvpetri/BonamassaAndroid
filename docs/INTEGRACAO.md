# Integração do cliente Android 0.3.0

Compatível com APIBonamassa `c9fcefad3827343a27abd5b0d14970a935b182b5`. O Android acessa a API REST diretamente. Painel e cozinha usam a mesma loja e os mesmos pedidos.

## Código

| Diretório | Responsabilidade |
|---|---|
| `client/` | Contratos REST, modelos, validação de rascunho, transporte HTTP, idempotência e codec persistente; testes JVM |
| `app/connected/CustomerViewModel.kt` | Sessão, catálogo, sacola, cotação, envio, histórico e sincronização |
| `app/connected/SecureStore.kt` | AES-GCM com chave no Android Keystore e escrita atômica em `noBackupFilesDir` |
| `app/connected/*Screens.kt` | Telas Compose conectadas usando o tema/componentes Bonamassa |
| `core/`, `app/data/`, `app/ui/` | Regras e telas da demo anterior; tema e componentes visuais compartilhados |

A variante padrão abre `CustomerApp`. Só o debug com `-PbonamassaDemo=true` abre `BonamassaApp` (demo anterior). O release força a integração.

## Contratos utilizados

| Método | Endpoint | Uso |
|---|---|---|
| POST | `/v1/customers` | Cadastro e emissão de sessão |
| POST | `/v1/sessions` | Login com `storeSlug`, e-mail e senha |
| DELETE | `/v1/sessions/current` | Revogação da sessão |
| GET | `/v1/stores/{slug}/catalog` | Produtos, regras, preços, promoções e loja aberta/fechada |
| GET | `/v1/stores/{slug}/images/{id}` | Fotos públicas da loja |
| POST | `/v1/orders/quote` | Cotação com itens, endereço, modalidade, pagamento, troco e promoção |
| POST | `/v1/orders` | Confirmar `{quoteId}` com a chave de idempotência persistida |
| GET | `/v1/me/orders` | Histórico com cursor e filtro de status |
| GET | `/v1/orders/{id}` | Reconciliar o pedido exibido e pedidos ativos antigos |
| POST | `/v1/orders/{id}/cancel` | Motivo + `expectedVersion`, somente antes do aceite |

Tokens são enviados como Bearer apenas ao servidor configurado. Contas de funcionários são rejeitadas; a sessão emitida por esse login é revogada. Uma resposta 401 remove a sessão local e pede login novamente. A sacola e qualquer envio pendente permanecem vinculados ao cliente original.

## Pedido e valores

Todos os valores são inteiros em centavos. A prévia local usa o preço mais alto dos sabores no tamanho escolhido, somando uma borda por pizza. Ela não autoriza nem finaliza a compra.

O app envia apenas identificadores, escolhas e quantidade. Não envia preço, frete, desconto, nome/telefone de cliente ou canal como autoridade. A API determina esses valores e obtém a identidade da sessão autenticada.

A cotação contém um recibo com produtos, receita do combo, valores e promoção. O usuário revisa esse resultado antes de confirmar. Se preço, disponibilidade, cota promocional ou validade mudarem, a API pode recusar o envio. A sacola é mantida e o app exige outra cotação/revisão, sem aceitar um novo total silenciosamente.

Pizzas: `SMALL`, `MEDIUM`, `LARGE`; um ou dois sabores distintos; borda `NONE` ou ID do produto de borda. Não há tamanho família ou adicionais fictícios no modo conectado. Bebidas não recebem campos de pizza. Combos enviam o ID da oferta, quantidade e observação: a receita e o preço são definidos pela pizzaria.

Promoções por tempo/cota são selecionadas por ID real; não se usa o cupom BONA10 da demo. O benefício vale para a base das pizzas elegíveis, limitado à cota disponível. Bordas, bebidas, combos e frete ficam fora. A confirmação final reserva a cota no servidor.

Entrega exige rua, número, bairro, cidade, UF e CEP. Retirada envia `address: null`. Cartão envia `cashTendered: null`; dinheiro sem valor significa sem troco. O app não gera Pix, processa cartão nem marca um pagamento como recebido.

## Persistência e recuperação

Um arquivo versionado contém sessão, conta, carrinho, endereço, favoritos e comando pendente. Ele é criptografado com AES-256-GCM e uma chave não exportável do Android Keystore. Cada gravação usa um IV novo gerado pelo provedor e `AtomicFile`. Senhas não são persistidas. Backup e transferência de dados do app ficam desabilitados.

Antes de enviar um pedido/cancelamento, o app grava atomicamente:

- caminho e corpo originais;
- chave UUID de idempotência;
- ID do cliente, ID da loja e origem da API.

Enquanto esse comando não tiver resultado confirmado, a sacola, o checkout, a troca de servidor e a saída da conta ficam bloqueados. **Verificar envio** repete exatamente corpo e chave originais. Fechar ou recriar o app não cria uma nova tentativa comercial.

Uma resposta de sucesso limpa o pendente e, para criação de pedido, a sacola, na mesma gravação. Se essa gravação falhar depois do sucesso remoto, o comando original continua salvo e pode ser repetido com segurança. Respostas ambíguas (rede, timeout, 429, 5xx, resposta inválida) preservam o pendente. Em 401, é necessário autenticar novamente na mesma conta. Uma rejeição definitiva de negócio limpa o comando, preserva a sacola e descarta a cotação.

Dados locais ilegíveis nunca são substituídos automaticamente. A tela permite tentar carregá-los novamente. Não remova os dados do app para resolver um envio ambíguo: consulte primeiro os pedidos da conta/pizzaria.

## Atualização de status

Enquanto a Activity está visível, o app atualiza o catálogo e os pedidos a cada 5 segundos; após falha, o intervalo é de 15 segundos. Ao sair do primeiro plano, o polling é cancelado; ao retornar, ocorre uma consulta. Não há serviço em segundo plano nem simulação de status.

O histórico mantém páginas anteriores e usa versões para impedir regressão quando uma consulta antiga chega depois de outra. Pedidos ativos já conhecidos e o pedido selecionado são consultados individualmente quando saem da primeira página. Na primeira carga com histórico paginado, também são buscados os pedidos ativos mais antigos.

São tratados `NEW`, `CONFIRMED`, `PREPARING`, `READY`, `OUT_FOR_DELIVERY`, `RETURNING`, `DELIVERED`, `RETURNED` e `CANCELLED`. Uma devolução nunca aparece como entrega concluída. O pagamento exibido também vem do servidor.

## Transporte e ambiente

A URL aceita somente uma origem HTTP(S), sem usuário/senha, caminho, query ou fragmento. Release exige HTTPS; apenas o manifesto debug libera HTTP para desenvolvimento na rede local. Não há trust manager permissivo, credencial fixa nem bypass de TLS.

O transporte recusa redirects e desativa repetição automática de envios. Consultas GET podem recuperar conexões interrompidas; conexões ociosas são encerradas em 2 segundos para evitar reutilizar sockets fechados pela API. As fotos são públicas e aceitas somente em caminhos de imagens da loja configurada; elas não recebem token. O HTTP client não instala logger de credenciais, corpo ou dados pessoais.

A preparação do release exige URL HTTPS e assinatura do ambiente de produção. A versão de SDK e as políticas de publicação deverão ser revistas ao preparar a entrega pela Play Store.

Referências de implementação: [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [criptografia Android](https://developer.android.com/privacy-and-security/cryptography) e [emulador na CI](https://github.com/ReactiveCircus/android-emulator-runner).
