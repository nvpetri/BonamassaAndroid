# Bonamassa Android — 0.2.0 demo

App cliente nativo em **Kotlin + Jetpack Compose**, com a identidade Bonamassa em preto, vermelho e dourado.

**Código-fonte de uma demonstração offline. Não é um APK e não recebe pedidos reais.** A interface não foi executada em emulador neste ambiente; consulte `docs/VERIFICACAO.md` antes de apresentar.

## O que esta versão implementa

- Início com a marca enviada, destaques e atalho para pedido em andamento.
- Cardápio de pizzas, bebidas e doce; busca por nome/ingrediente e filtro de favoritos.
- Pizza de um ou dois sabores, três tamanhos, borda e adicionais.
- Observação editável, quantidade e cálculo em centavos, sem usar ponto flutuante nas regras.
- Sacola com itens individuais; alterar quantidade, editar personalização e remover com confirmação.
- Cupom BONA10 com mínimo, limite e mensagens de erro.
- Entrega ou retirada, perfil local, endereço, validação dos campos e troco.
- Escolha de Pix, cartão ou dinheiro **sem cobrança**; confirmação explícita de demonstração.
- Histórico persistido, detalhes completos, pedir novamente e cancelamento inicial.
- Avanço **manual** das etapas para apresentação; retirada não passa pelo estado de motoboy.
- DataStore com alterações atômicas: sacola, favoritos, dados salvos e histórico sobrevivem ao fechamento do app.
- Erros de leitura não apagam os dados silenciosamente; limpeza exige confirmação.
- Ícone adaptativo, tratamento de insets/teclado, conteúdo rolável e previews Compose.
- 36 testes JVM de regras/serialização e 3 testes de interface incluídos.

O cardápio, preços, frete, cupom e regra de meio a meio são **exemplos a aprovar com a pizzaria**. As imagens de alimentos são ilustrações desenhadas pelo app; não são fotos dos produtos.

## Abrir no Android Studio (Windows)

1. Extraia o ZIP. Abra a pasta `BonamassaAndroid` em **File → Open**; não apenas a subpasta `app`.
2. Instale/configure um **JDK completo 17**. JRE não é suficiente. Em Gradle JDK, selecione o JDK 17.
3. No SDK Manager, instale **Android SDK Platform 35**, **Build Tools 35.0.0** e Platform Tools. Leia e aceite as licenças aplicáveis no seu ambiente.
4. Aguarde **Sync Project with Gradle Files**. A primeira sincronização precisa de Internet.
5. Crie um emulador Android 8.0/API 26 ou superior, ou conecte um celular com depuração USB.
6. Selecione `app` e clique em **Run**.

Se o Gradle reclamar de `SDK location not found`, configure o SDK no Android Studio. Ele poderá gerar o arquivo local `local.properties`. Não compartilhe esse arquivo nem caminhos específicos do seu computador.

### Gerar um APK de teste

No terminal do Android Studio, a partir da raiz do projeto:

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

No Linux/macOS:

```bash
chmod +x gradlew
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Se a compilação concluir, o APK de desenvolvimento ficará em:

`app/build/outputs/apk/debug/app-debug.apk`

Para testes de interface, com aparelho ou emulador conectado:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Para executar somente as regras sem instalar o SDK Android (ainda exige JDK 17 e Internet na primeira vez):

```powershell
.\gradlew.bat -PcoreOnly :core:test
```

Os scripts oficiais `gradlew`, `gradlew.bat` e o Wrapper JAR estão incluídos. O download do Gradle 8.10.2 possui checksum SHA-256 fixado. Não há chave de assinatura de produção no pacote.

## Roteiro de apresentação

1. Abra o cardápio e escolha **A Bonamassa**.
2. Selecione **Família**, **Meio a meio → Frango cremoso**, borda e bacon.
3. Escreva uma observação e adicione duas unidades.
4. Na sacola, edite um item, altere a quantidade e aplique **BONA10**.
5. Teste **Retirada** para ver o frete zerar; volte para **Entrega** se quiser demonstrar o formulário.
6. Preencha dados fictícios. Experimente um telefone curto para ver a validação.
7. Escolha **Dinheiro** e um valor de troco menor que o total; corrija depois.
8. Confirme **Criar demonstração**. Use os botões **Simular** para avançar as etapas.
9. Confira o histórico e **Pedir de novo**.
10. Feche e reabra o app para verificar a persistência. No perfil, teste a limpeza com confirmação.

## Onde mexer

| O que alterar | Arquivo |
|---|---|
| Produtos, preços e ingredientes | `core/src/main/kotlin/br/com/bonamassa/core/Catalog.kt` |
| Tamanhos, bordas, adicionais | `core/src/main/kotlin/br/com/bonamassa/core/Models.kt` |
| Regra de sabores, entrega, cupom e validações | `core/src/main/kotlin/br/com/bonamassa/core/OrderRules.kt` |
| Paleta e tipografia | `app/src/main/java/br/com/bonamassa/app/ui/Theme.kt` |
| Previews no Android Studio | `app/src/main/java/br/com/bonamassa/app/ui/Previews.kt` |
| Estado e ações do app | `app/src/main/java/br/com/bonamassa/app/BonamassaViewModel.kt` |
| Persistência | `app/src/main/java/br/com/bonamassa/app/data/` |

## Próxima etapa real

Conectar uma API autenticada, validar cardápio oficial/área de entrega e implementar cobrança verificada no servidor. Painel da pizzaria, cozinha e entregador são módulos futuros, não fazem parte deste pacote.

Veja `docs/INTEGRACAO.md` para as decisões pendentes e o contrato de integração sugerido. Veja `docs/VERIFICACAO.md` para o que foi efetivamente testado.

A logo fornecida pelo usuário foi mantida. Direitos sobre a marca e a logo permanecem com seus titulares.
# BonamassaAndroid
