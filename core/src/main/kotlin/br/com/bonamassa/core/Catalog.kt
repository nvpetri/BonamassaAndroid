package br.com.bonamassa.core

/** All prices, menu content and fees are examples, pending merchant approval. */
object Catalog {
    val products = listOf(
        Product("bonamassa", "A Bonamassa", "Pepperoni, mussarela, bacon crocante e um toque de manjericão.", 6290, Category.PIZZA, "DA CASA", 0),
        Product("calabresa", "Calabresa da casa", "Calabresa, cebola roxa, mussarela, azeitonas e orégano.", 4990, Category.PIZZA, "CLÁSSICA", 1),
        Product("frango", "Frango cremoso", "Frango desfiado, requeijão cremoso, milho e mussarela.", 5490, Category.PIZZA, "CREMOSA", 2),
        Product("margherita", "Margherita", "Molho de tomate, mussarela, tomates frescos e manjericão.", 5290, Category.PIZZA, "VEGETARIANA", 3),
        Product("portuguesa", "Portuguesa", "Presunto, ovos, cebola, ervilha, azeitona e mussarela.", 5790, Category.PIZZA, "TRADICIONAL", 4),
        Product("quatro", "Quatro queijos", "Mussarela, parmesão, provolone e requeijão cremoso.", 5890, Category.PIZZA, "4 QUEIJOS", 5),
        Product("cola", "Refrigerante cola", "Garrafa 2 litros. Para compartilhar.", 1400, Category.DRINK, "2 LITROS", 6),
        Product("guarana", "Guaraná", "Garrafa 2 litros. Bem gelado.", 1200, Category.DRINK, "2 LITROS", 7),
        Product("agua", "Água mineral", "Sem gás. Garrafa 500 ml.", 500, Category.DRINK, "500 ML", 8),
        Product("brownie", "Brownie da casa", "Chocolate, casquinha crocante e centro macio. Porção individual.", 1590, Category.DESSERT, "CHOCOLATE", 9)
    )
    fun find(id: String?) = products.find { it.id == id }
    fun require(id: String) = requireNotNull(find(id)) { "Produto não encontrado." }
    val pizzas get() = products.filter { it.category == Category.PIZZA && it.available }
}
