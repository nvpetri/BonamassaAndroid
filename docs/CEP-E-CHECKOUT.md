# CEP e revisão da sacola — Cliente 0.6.0

Atualize primeiro a API da [PR #5](https://github.com/nvpetri/APIBonamassa/pull/5). O aplicativo envia os campos opcionais `complement` e `noComplement`, além da referência existente. Pedidos/cotações antigos e envios pendentes continuam preservados.

O CEP vem primeiro: com oito dígitos, o app consulta ViaCEP por HTTPS, sem token de sessão ou dados do cliente. Rua/bairro/cidade/UF são preenchidos e podem ser corrigidos. Número continua obrigatório; complemento é opcional. A opção “Não possui complemento” limpa e desabilita o campo. O complemento postal retornado pelo ViaCEP não é apartamento e não é copiado. CEP inexistente, geral ou serviço indisponível permitem completar o formulário manualmente. Mudar o CEP limpa o endereço anterior e descarta respostas atrasadas; edições manuais durante a consulta são preservadas.

Adicionar um produto só altera a sacola local. O diálogo oferece continuar comprando ou ir para checkout. A sacola e a revisão permitem adicionar mais produtos. A cotação usa todos os itens e um frete; o envio ocorre somente depois da revisão e da confirmação final. Reservas usam confirmação de agendamento. Uma compra posterior à confirmação continua sendo outro pedido, com o frete aplicável; pedidos antigos não são unificados automaticamente.

Testes: `:client:test` cobre transporte/erros ViaCEP e persistência de complemento; `CheckoutFlowTest` cobre autofill, número obrigatório, ausência de complemento, resposta atrasada e preenchimento manual com provedor controlado. `CustomerApiFlowTest` adiciona pizza + bebida, verifica zero pedidos antes de enviar e um pedido com dois itens e um frete após a confirmação, contra API/PostgreSQL isolados. Não depende de ViaCEP público nos testes.

Build debug/release e integração seguem o [guia de produção](PRODUCAO.md). `versionCode=6`; assine com a mesma chave da versão instalada. A API padrão permanece `https://bonamassa-api.onrender.com`; não há tela de configuração de API.

[Contrato e roteiro completo](https://github.com/nvpetri/APIBonamassa/blob/codex/cep-sacola-rotas/docs/CEP-SACOLA-ROTAS.md) · [ViaCEP](https://viacep.com.br/)
