package ca.carletonu.models.repositories;

import ca.carletonu.models.Customer;
import ca.carletonu.models.Item;
import ca.carletonu.models.Shop;
import ca.carletonu.models.Transaction;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Customer favourites, purchase history, and Transaction persistence.
 */
@RepositoryTest
class CustomerAndTransactionRepositoryTests {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void customerFavoriteShopsArePersisted() {
        Shop shop = shopRepository.save(new Shop("Kettle Corner", "Teas", "Tea", null));
        Customer bo = new Customer("Bo", "bo@example.com", "pw", "555", "1 Main St");
        bo.addShop(shop);
        Long id = customerRepository.save(bo).getId();
        entityManager.flush();
        entityManager.clear();

        Customer reloaded = customerRepository.findById(id).orElseThrow();

        assertThat(reloaded.getAddress()).isEqualTo("1 Main St");
        assertThat(reloaded.getFavoriteShops()).extracting(Shop::getName).containsExactly("Kettle Corner");
    }

    @Test
    void shopCustomersArePersisted() {
        Customer bo = customerRepository.save(new Customer("Bo", "bo@example.com", "pw", "555", "1 Main St"));
        Shop shop = new Shop("Kettle Corner", "Teas", "Tea", null);
        shop.addCustomer(bo);
        Long shopId = shopRepository.save(shop).getId();
        entityManager.flush();
        entityManager.clear();

        assertThat(shopRepository.findById(shopId).orElseThrow().getCustomers())
                .extracting(Customer::getName)
                .containsExactly("Bo");
    }

    @Test
    void transactionLinksShopProductAndCustomer() {
        Shop shop = new Shop("Lantern Works", "Lamps", "Lighting", null);
        Item lamp = new Item("Lamp", "Brass", 89.0, 4, "Lighting");
        shop.addProduct(lamp);
        shop = shopRepository.save(shop);
        Customer bo = customerRepository.save(new Customer("Bo", "bo@example.com", "pw", "555", "1 Main St"));

        Long id = transactionRepository.save(new Transaction(shop, shop.getProducts().get(0), bo)).getId();
        entityManager.flush();
        entityManager.clear();

        Transaction reloaded = transactionRepository.findById(id).orElseThrow();

        assertThat(reloaded.getShop().getName()).isEqualTo("Lantern Works");
        assertThat(reloaded.getProduct().getName()).isEqualTo("Lamp");
        assertThat(reloaded.getCustomer().getName()).isEqualTo("Bo");
    }

    @Test
    void purchaseHistoryIsPersistedAfterBuying() {
        Shop shop = new Shop("Lantern Works", "Lamps", "Lighting", null);
        shop.addProduct(new Item("Lamp", "Brass", 89.0, 4, "Lighting"));
        shop = shopRepository.save(shop);
        Customer bo = customerRepository.save(new Customer("Bo", "bo@example.com", "pw", "555", "1 Main St"));

        Item lamp = (Item) shop.getProducts().get(0);
        Transaction transaction = new Transaction(shop, lamp, bo);
        assertThat(transaction.buy(lamp)).isTrue();
        transactionRepository.save(transaction);
        customerRepository.save(bo);
        entityManager.flush();
        entityManager.clear();

        assertThat(customerRepository.findById(bo.getId()).orElseThrow().getTransactionHistory())
                .singleElement()
                .asString()
                .startsWith("Bought Lamp from Lantern Works");
    }
}
