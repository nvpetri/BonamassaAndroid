# Integração e limites — Bonamassa Android 0.2.0

## Estado atual

Aplicativo cliente Android nativo, inteiramente offline. Não existe endpoint configurado, autenticação de usuário, pagamento real, cadastro remoto, painel da pizzaria ou aplicativo do motoboy neste pacote.

Todas as telas exibem o aviso de demonstração. A finalização pede confirmação explícita e cria somente um pedido local. O botão de avanço de status existe exclusivamente para apresentações; não deve existir em um cliente de produção.

## Organização

| Área | Responsabilidade |
|---|---|
| `core/Models.kt` | Modelos imutáveis e valores em centavos |
| `core/Catalog.kt` | Produtos e preços demonstrativos |
| `core/OrderRules.kt` | Preço, cupom, validação, status e criação de pedido |
| `app/data/StateCodec.kt` | JSON versionado e snapshots de pedidos |
| `app/data/LocalRepository.kt` | Persistência atômica com DataStore Preferences |
| `app/BonamassaViewModel.kt` | Estado, ações e mensagens para a interface |
| `app/ui/` | Compose, navegação, componentes e ilustrações nativas |

O carrinho, favoritos, dados salvos e histórico sobrevivem ao fechamento e à recriação do processo. Rascunhos de formulários usam `rememberSaveable`, útil para rotação/recriação, mas só o botão **Salvar** ou a criação do pedido grava esses dados de forma durável. Até 50 linhas por sacola, 20 unidades por linha e 100 pedidos históricos. Uma exclusão de dados exige confirmação.

## Decisões de demonstração (confirmar com a pizzaria)

- Pizza média: seis fatias, R$ 10,00 abaixo da grande.
- Grande: oito fatias, valor base.
- Família: doze fatias, R$ 14,00 acima da grande.
- Dois sabores: utiliza o preço do mais caro; não soma as metades.
- Até dois sabores salgados distintos; bebidas e sobremesas não aceitam personalizações de pizza.
- Borda e adicionais cobrados por pizza inteira; a quantidade multiplica o conjunto completo.
- Entrega: R$ 7,00 fixos. Retirada sem taxa. Sem validação geográfica da área atendida.
- BONA10: 10% sobre produtos a partir de R$ 60,00, limitado a R$ 20,00; arredondado para baixo em centavos. Frete não participa do desconto.
- Pix, cartão no recebimento e dinheiro são somente preferências locais, sem cobranças.
- Pedido pode ser cancelado pelo cliente apenas no estado recebido nesta demonstração.
- Valores, nomes dos produtos, ingredientes e ilustrações são exemplos, não um cardápio oficial confirmado.

## O que falta para receber pedidos reais

1. Validar cardápio, preços por tamanho, regra dos sabores, bordas, adicionais, horários, área de entrega e endereço oficial.
2. Implementar uma API autenticada. O servidor deve validar disponibilidade, preços, cupom, frete e autorização em todas as operações; nunca aceitar o total do cliente como verdadeiro.
3. A criação de pedidos precisa de chave de idempotência persistida antes do envio e usada novamente nos retries. A proteção local contra toques repetidos desta demo **não** substitui idempotência de rede.
4. Usar um orçamento emitido pelo servidor com validade curta; revalidar preços e disponibilidade antes da confirmação.
5. Integração de pagamentos exclusivamente no servidor, com segredo fora do APK, confirmação assinada por webhook, tratamento de duplicidade e reconciliação. Nunca marcar como pago por evento recebido apenas do aplicativo.
6. Implementar autenticação, sessões e autorização por estabelecimento. Não usar um seletor local de perfil cliente/gerente/motoboy como segurança.
7. Trocar o controle manual de status por atualizações autorizadas vindas da cozinha/expedição. WebSocket ou polling devem atualizar o repositório; reconsultar ao retornar ao primeiro plano.
8. Definir retenção, consentimentos necessários, suporte, política de privacidade e exclusão de conta quando houver cadastro real.
9. Antes de publicar, atualizar e verificar requisitos vigentes da Play Store, target SDK, assinatura e testes de dispositivos. Este target SDK 35 é uma base de desenvolvimento, não uma declaração de conformidade para publicação.

## Contrato REST sugerido (ainda não implementado)

| Método | Recurso | Responsabilidade |
|---|---|---|
| GET | `/v1/stores/{id}/catalog` | Cardápio e disponibilidade |
| POST | `/v1/stores/{id}/quotes` | Orçamento validado, taxas e validade |
| POST | `/v1/orders` | Criar a partir de quoteId + idempotency key |
| GET | `/v1/orders/{id}` | Consultar um pedido autorizado |
| POST | `/v1/orders/{id}/cancel` | Solicitar cancelamento sujeito à regra do servidor |
| GET | `/v1/me/orders` | Histórico do usuário autenticado |

Não foi criado código de rede com URL fictícia. Adicionar a permissão INTERNET apenas ao implementar a API. Exigir HTTPS e nunca incluir chaves de gateway no código-fonte, recursos, BuildConfig ou APK.

## Persistência e privacidade

DataStore em armazenamento privado do app; não é um cofre criptográfico. Backup em nuvem e transferência estão excluídos no manifesto/regras. Não há analytics, GPS, contatos, SMS nem Internet. Prefira dados fictícios ao demonstrar. Dados ilegíveis causam uma tela de erro com tentativa de recarga; não são silenciosamente substituídos.

O codec valida o schema e preços históricos sem recalculá-los. Antes de alterar o catálogo em uma versão futura, implemente migração de carrinhos e dos `sourceItems` dos pedidos; os IDs persistidos não podem simplesmente desaparecer. Para escala maior e múltiplos endereços, migrar para Room com migrations e testes.

## Referências da configuração

- [Compatibilidade do AGP 8.8: Gradle 8.10.2, JDK 17 e API 35](https://developer.android.com/build/releases/agp-8-8-0-release-notes)
- [Plugin do compilador Compose](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
- [Checksums oficiais do Gradle](https://gradle.org/release-checksums/)

Dependências fixadas para uma base conhecida; não são uma promessa de versões mais recentes. Atualizações devem passar novamente pelos testes.
