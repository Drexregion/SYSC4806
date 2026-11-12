package ca.carletonu.models.repositories;

import ca.carletonu.models.Item;
import ca.carletonu.models.Product;
import ca.carletonu.models.Service;
import ca.carletonu.models.Shop;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests ShopRepository's derived queries and the Shop to Product cascade
 * against a real (in-memory H2) database.
 */
@RepositoryTest
class ShopRepositoryTests {

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        shopRepository.save(new Shop("Bean There Coffee", "Espresso and pastries", "Cafe", null));
        shopRepository.save(new Shop("Chapter One", "New and used books", "Books", null));
        shopRepository.save(new Shop("Bookworm Books", "Children's books", "Books", null));
    }

    @Test
    void matchesPartOfNameIgnoringCase() {
        assertThat(search("coffee")).containsExactly("Bean There Coffee");
    }

    @Test
    void matchesCategoryIgnoringCase() {
        assertThat(search("books")).containsExactlyInAnyOrder("Chapter One", "Bookworm Books");
    }

    @Test
    void shopMatchingOnNameAndCategoryAppearsOnce() {
        assertThat(search("book")).containsExactlyInAnyOrder("Chapter One", "Bookworm Books");
    }

    @Test
    void noMatchReturnsEmptyList() {
        assertThat(search("hardware")).isEmpty();
    }

    @Test
    void findsTheShopThatSellsAProduct() {
        Shop shop = new Shop("Lantern Works", "Lamps", "Lighting", null);
        shop.addProduct(new Item("Paper lantern", "Rice paper", 12.5, 4, "Lamp"));
        shop = shopRepository.save(shop);
        Long productId = shop.getProducts().get(0).getId();

        assertThat(shopRepository.findFirstByProducts_Id(productId))
                .map(Shop::getName)
                .contains("Lantern Works");
    }

    @Test
    void productWithNoShopFindsNothing() {
        Item loose = itemRepository.save(new Item("Loose", "No shop", 1.0, 1, "x"));

        assertThat(shopRepository.findFirstByProducts_Id(loose.getId())).isEmpty();
    }

    @Test
    void unknownProductIdFindsNothing() {
        assertThat(shopRepository.findFirstByProducts_Id(999_999L)).isEmpty();
    }

    @Test
    void savingAShopCascadesToItsProducts() {
        Shop shop = new Shop("Lantern Works", "Lamps", "Lighting", null);
        shop.addProduct(new Item("Paper lantern", "Rice paper", 12.5, 4, "Lamp"));
        shop.addProduct(new Service("Repair", "Rewiring", 30.0, 45));
        Long id = shopRepository.save(shop).getId();
        entityManager.flush();
        entityManager.clear();

        Shop reloaded = shopRepository.findById(id).orElseThrow();

        assertThat(reloaded.getProducts()).hasSize(2);
        assertThat(reloaded.getProducts()).allSatisfy(p -> assertThat(p.getId()).isNotNull());
        // Inheritance survives the round trip: each comes back as its real subtype
        assertThat(reloaded.getProducts()).hasAtLeastOneElementOfType(Item.class);
        assertThat(reloaded.getProducts()).hasAtLeastOneElementOfType(Service.class);
    }

    @Test
    void removingAProductFromAShopIsPersisted() {
        Shop shop = new Shop("Lantern Works", "Lamps", "Lighting", null);
        Item lantern = new Item("Paper lantern", "Rice paper", 12.5, 4, "Lamp");
        shop.addProduct(lantern);
        shop.addProduct(new Item("Brass lamp", "Brass", 89.0, 1, "Lamp"));
        shop = shopRepository.save(shop);

        shop.removeProduct(lantern);
        shopRepository.save(shop);
        entityManager.flush();
        entityManager.clear();

        assertThat(shopRepository.findById(shop.getId()).orElseThrow().getProducts())
                .extracting(Product::getName)
                .containsExactly("Brass lamp");
    }

    @Test
    void deletingAShopCascadesToItsProducts() {
        Shop shop = new Shop("Lantern Works", "Lamps", "Lighting", null);
        shop.addProduct(new Item("Paper lantern", "Rice paper", 12.5, 4, "Lamp"));
        shop = shopRepository.save(shop);
        Long productId = shop.getProducts().get(0).getId();

        shopRepository.delete(shop);
        entityManager.flush();

        assertThat(productRepository.findById(productId)).isEmpty();
    }

    private List<String> search(String term) {
        return shopRepository.findByNameContainingIgnoreCaseOrTypeContainingIgnoreCase(term, term)
                .stream()
                .map(Shop::getName)
                .toList();
    }
}
