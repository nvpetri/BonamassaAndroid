# Verificação — cliente Android integrado

## Comandos

```powershell
.\gradlew.bat :core:test :client:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Os testes `client` cobrem payloads reais, cálculo de prévia, dinheiro em centavos, validação de endereço, itens/quantidades, isolamento de contas/servidores, persistência de pendentes, classificação de falhas, cabeçalhos HTTP, redirects e rejeição de login de funcionários. `core` e os testes de codec/UI existentes continuam verificando a demonstração isolada.

## Fluxo com API real

O workflow `.github/workflows/android.yml`:

1. Compila com JDK 17, SDK 35 e Gradle Wrapper com checksum.
2. Executa testes JVM, lint e gera APK debug + APK de instrumentação.
3. Sobe PostgreSQL 17, baixa a versão fixa da API, aplica migrações e seed em um banco de teste exclusivo.
4. Executa um emulador Android 35 com acesso à API por `10.0.2.2:3001`.
5. Testa cadastro na UI, pizza meio a meio com borda, endereço, dinheiro/troco e promoção; compara valores persistidos pela API.
6. Confirma na UI as mudanças de aceite, cozinha, saída e conclusão da entrega feitas pelos endpoints autorizados usados pelos outros aplicativos.
7. Verifica combo com preço próprio, reabertura do app com envio pendente e recuperação sem duplicidade, cancelamento, isolamento entre clientes e revogação de sessão.
8. Publica relatórios de teste e APK como artefatos da execução.

As chamadas de gestor e entregador existem somente no código de instrumentação e usam contas descartáveis da CI. Não estão no APK do cliente. As senhas de CI não são configurações de produção.

O teste real é **opt-in**: `CustomerApiFlowTest` é ignorado se o argumento `bonamassaIntegration=true` não estiver presente. Para executá-lo manualmente, prepare uma API e banco isolados com exatamente as credenciais/seed do workflow e um emulador:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.bonamassaIntegration=true
```

Não aponte esse cenário para uma loja em operação.

## Conferência na pizzaria

- Conectar celular físico à API do PC e cadastrar uma conta de cliente.
- Criar/editar sabor e foto no painel; conferir atualização no app.
- Pausar produto ou fechar loja; confirmar que pedidos indisponíveis são recusados.
- Pedir uma pizza inteira e outra meio a meio com borda; revisar o valor calculado pela API.
- Pedir o combo cadastrado no painel e conferir composição e preço promocional.
- Selecionar promoção com prazo/cota; conferir quantidade de pizzas beneficiadas no resumo.
- Testar entrega com CEP/UF, retirada, cartão na entrega e dinheiro/troco.
- Enviar pedido, acompanhar no painel/cozinha e observar status no app.
- Cancelar antes do aceite; depois do aceite, o app orienta a falar com a loja.
- Desconectar/reconectar a rede durante consulta e envio; usar **Verificar envio** quando necessário e conferir que existe um único pedido.
- Fechar e reabrir o app; conferir sessão, sacola e histórico da conta.

## Escopo da evidência

A CI exercita Android 35 e o backend real com PostgreSQL; ela não substitui a homologação no celular do usuário e na rede da pizzaria. GPS, push e pagamento online não fazem parte deste teste nem desta integração. A evidência de cada execução está nos relatórios da aba Actions deste repositório.
