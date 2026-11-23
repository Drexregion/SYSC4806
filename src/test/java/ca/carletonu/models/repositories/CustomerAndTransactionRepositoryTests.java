package ca.carletonu.models.repositories;

import ca.carletonu.models.Customer;
import ca.carletonu.models.Item;
import ca.carletonu.models.Shop;
import ca.carletonu.models.Transaction;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Customer favourites, Transaction persistence, and purchase history
 * (which comes from Transaction, not from Customer).
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
    void purchaseDateIsPersistedAfterBuying() {
        Shop shop = new Shop("Lantern Works", "Lamps", "Lighting", null);
        shop.addProduct(new Item("Lamp", "Brass", 89.0, 4, "Lighting"));
        shop = shopRepository.save(shop);
        Customer bo = customerRepository.save(new Customer("Bo", "bo@example.com", "pw", "555", "1 Main St"));

        Item lamp = (Item) shop.getProducts().get(0);
        Transaction transaction = new Transaction(shop, lamp, bo);
        assertThat(transaction.buy(lamp)).isTrue();
        Long id = transactionRepository.save(transaction).getId();
        entityManager.flush();
        entityManager.clear();

        assertThat(transactionRepository.findById(id).orElseThrow().getDate()).isNotNull();
    }

    @Test
    void purchaseHistoryIsTheCustomersTransactionsNewestFirst() {
        Shop shop = new Shop("Lantern Works", "Lamps", "Lighting", null);
        shop.addProduct(new Item("Lamp", "Brass", 89.0, 4, "Lighting"));
        shop.addProduct(new Item("Lantern", "Paper", 12.5, 4, "Lighting"));
        shop = shopRepository.save(shop);
        Customer bo = customerRepository.save(new Customer("Bo", "bo@example.com", "pw", "555", "1 Main St"));
        Customer cy = customerRepository.save(new Customer("Cy", "cy@example.com", "pw", "556", "2 Main St"));

        Item lamp = (Item) shop.getProducts().get(0);
        Item lantern = (Item) shop.getProducts().get(1);
        Transaction first = new Transaction(shop, lamp, bo);
        Transaction second = new Transaction(shop, lantern, bo);
        Transaction someoneElses = new Transaction(shop, lamp, cy);

        ReflectionTestUtils.setField(first, "date", new Date(1_000));
        ReflectionTestUtils.setField(second, "date", new Date(2_000));
        ReflectionTestUtils.setField(someoneElses, "date", new Date(3_000));
        transactionRepository.saveAll(List.of(first, second, someoneElses));
        entityManager.flush();
        entityManager.clear();

        assertThat(transactionRepository.findByCustomerOrderByDateDesc(bo))
                .extracting(t -> t.getProduct().getName())
                .containsExactly("Lantern", "Lamp");
    }

    @Test
    void customerWithNoPurchasesHasEmptyHistory() {
        Customer bo = customerRepository.save(new Customer("Bo", "bo@example.com", "pw", "555", "1 Main St"));

        assertThat(transactionRepository.findByCustomerOrderByDateDesc(bo)).isEmpty();
    }

    @Test
    void customerTableHasNoTransactionHistoryStorage() {
        // Regression check for #48: purchase history lives on Transaction only,
        // so the old element-collection table must not come back.
        Number tables = (Number) entityManager.createNativeQuery(
                        "select count(*) from information_schema.tables "
                                + "where lower(table_name) = 'customer_transaction_history'")
                .getSingleResult();

        assertThat(tables.intValue()).isZero();
    }
}
