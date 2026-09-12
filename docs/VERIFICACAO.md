# Verificação — 12/09/2026

## Resultado efetivamente obtido

| Verificação | Resultado |
|---|---|
| Compilação isolada do módulo de regras e codec JSON | Concluída com Kotlin 2.0.21, alvo JVM 17 |
| Regras de negócio (JUnit 4.13.2) | 30 testes aprovados |
| Serialização e validação de dados locais (JUnit) | 6 testes aprovados |
| Sintaxe dos arquivos Kotlin | 18 arquivos analisados via parser PSI, sem erros de sintaxe |
| Gradle Wrapper | Gerado pelo Gradle 8.10.2; JAR e distribuição conferidos por SHA-256 oficial |
| Sintaxe do script gradlew | Validada com bash -n |
| XML de recursos e manifesto | Parsing e referências locais conferidos |
| Compilação do aplicativo Android | Não concluída neste ambiente |
| Android Lint | Não executado |
| Testes de interface | 3 implementados, não executados |
| Validação visual em emulador/aparelho | Pendente |
| APK instalável | Não gerado |

Saída da execução dos testes JVM:

```text
JUnit version 4.13.2
....................................
OK (36 tests)
```

A execução inicial foi feita diretamente com o compilador Kotlin e o runner JUnit, usando as classes reais de produção de `core` e `StateCodec`, não uma reimplementação das regras em outra linguagem. Naquela configuração, o módulo `core` exigia um JDK completo 17; o ambiente disponível possuía somente runtime Java 17 e não possuía SDK Android. A tentativa inicial com Gradle encontrou ausência de toolchain Java de compilação. Por isso não foi concluído o ciclo Gradle/Android.

A análise de sintaxe não é uma compilação Android: não confirma resolução de APIs Compose, recursos via AAPT, lint ou comportamento visual. Os testes do codec verificam serialização e snapshots; não equivalem a testar DataStore em um dispositivo. Não afirmar que o app está homologado para uso real com base nestes resultados.

## Cobertura dos 36 testes

Preço pelo maior sabor; tamanho; borda; extras por pizza inteira; preço de bebidas; rejeição de misturas/tamanhos inválidos; quantidade e observação; sacola vazia; frete e retirada; mínimo e teto do cupom; entrada monetária decimal; nome/telefone/endereço; valor de troco; progressão de status de entrega e retirada; bloqueio de estados terminais; snapshot de pedido; limpeza da sacola; proteção contra nova criação com sacola já esvaziada; limite de linhas; ida e volta de JSON; schema desconhecido; dados inválidos.

## Correção da seleção do JDK — 12/09/2026

O módulo `core` passou a usar o JDK escolhido para executar o Gradle, com os alvos Java e Kotlin definidos explicitamente como 17. A exigência de uma instalação separada via `jvmToolchain(17)` foi removida. As instruções de abertura agora orientam a seleção de um JDK completo 17 ou 21.

A configuração corrigida foi verificada com **Gradle 8.10.2 e Temurin JDK 21.0.12.1**, executando `-PcoreOnly :core:test`:

- `BUILD SUCCESSFUL`; 30 testes de regras aprovados, sem falhas ou erros.
- As 20 classes de produção geradas foram inspecionadas: todas têm major version 61, correspondente ao bytecode Java 17.
- A execução usou o JDK 21 sem provisionar outro JDK 17 para o módulo `core`.

Essa verificação adicional cobre a compilação e os testes do módulo `core`. O SDK Android continua ausente neste ambiente: a compilação do app, os testes do módulo `app`, lint e emulador permanecem pendentes pelo Gradle. Os resultados anteriores do codec são da execução isolada descrita acima.

Referências: [JDK e alvo de compilação no Gradle](https://docs.gradle.org/8.10.2/userguide/toolchains.html), [opções de compilação do Kotlin](https://kotlinlang.org/docs/gradle-compiler-options.html).

## Checklist obrigatório no Android Studio antes de apresentar

- Rodar `:core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- Executar `:app:connectedDebugAndroidTest` em emulador ou celular.
- Verificar telas em 360 dp, 390 dp e tablet; fonte padrão e ampliada.
- Validar barras do sistema, teclado aberto, rotação e botão Voltar do Android.
- Validar que Adicionar mais itens retorna ao cardápio e não restaura uma sacola antiga.
- Personalizar pizza com dois sabores, editar depois e conferir os valores exatos.
- Testar Pix/dinheiro/cartão de demonstração; garantir que não aparece confirmação de pagamento real.
- Confirmar persistência após fechar o app/encerrar o processo e abrir novamente.
- Verificar perfil e endereço sem usar dados pessoais reais.
- Verificar tela de erro e confirmação de apagar os dados.
- Verificar que a retirada não tem taxa nem etapa de entregador.
- Conferir os avisos de demonstração em todo o percurso.

As funções externas descritas em `INTEGRACAO.md` continuam pendentes. Este pacote não configura infraestrutura, não publica app e não envia dados à pizzaria.
