package ca.carletonu.models.repositories;

import ca.carletonu.models.Seller;
import ca.carletonu.models.Shop;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seller owns its shops (one-to-many, mapped by Shop.seller, cascading).
 */
@RepositoryTest
class SellerRepositoryTests {

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savingASellerCascadesToItsShops() {
        Seller seller = new Seller("Ada", "ada@example.com", "pw", "555");
        seller.addShop(new Shop("Kettle Corner", "Teas", "Tea", null));
        seller.addShop(new Shop("Bean There", "Coffee", "Cafe", null));
        Long id = sellerRepository.save(seller).getId();
        entityManager.flush();
        entityManager.clear();

        Seller reloaded = sellerRepository.findById(id).orElseThrow();

        assertThat(reloaded.getShops())
                .extracting(Shop::getName)
                .containsExactlyInAnyOrder("Kettle Corner", "Bean There");
        assertThat(reloaded.getShops())
                .allSatisfy(shop -> assertThat(shop.getSeller().getId()).isEqualTo(id));
    }

    @Test
    void shopKnowsItsSellerAfterReload() {
        Seller seller = sellerRepository.save(new Seller("Ada", "ada@example.com", "pw", "555"));
        Long shopId = shopRepository.save(new Shop("Kettle Corner", "Teas", "Tea", seller)).getId();
        entityManager.flush();
        entityManager.clear();

        assertThat(shopRepository.findById(shopId).orElseThrow().getSeller().getName())
                .isEqualTo("Ada");
    }

    @Test
    void sellerFieldsArePersisted() {
        Long id = sellerRepository.save(new Seller("Ada", "ada@example.com", "pw", "555-0100")).getId();
        entityManager.flush();
        entityManager.clear();

        Seller reloaded = sellerRepository.findById(id).orElseThrow();

        assertThat(reloaded.getName()).isEqualTo("Ada");
        assertThat(reloaded.getEmail()).isEqualTo("ada@example.com");
        assertThat(reloaded.getPhone()).isEqualTo("555-0100");
    }

    @Test
    void sellerIsAlsoFoundAsAPerson() {
        Long id = sellerRepository.save(new Seller("Ada", "ada@example.com", "pw", "555")).getId();
        entityManager.flush();
        entityManager.clear();

        assertThat(personRepository.findById(id)).get().isInstanceOf(Seller.class);
    }
}
