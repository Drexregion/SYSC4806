package ca.carletonu.testsupport;

import ca.carletonu.models.Cart;
import ca.carletonu.models.Customer;
import ca.carletonu.models.Item;
import ca.carletonu.models.Seller;
import ca.carletonu.models.Shop;
import ca.carletonu.models.Transaction;
import ca.carletonu.models.repositories.CartRepository;
import ca.carletonu.models.repositories.CustomerRepository;
import ca.carletonu.models.repositories.SellerRepository;
import ca.carletonu.models.repositories.ShopRepository;
import ca.carletonu.models.repositories.TransactionRepository;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that nothing one integration test writes is visible to the next.
 * <p>
 * The rows left behind are the awkward ones: a cart and a purchase both hold
 * foreign keys, which made clearing shops with deleteAll() fail.
 */
@IntegrationTest
class IntegrationTestIsolationTests {

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    /** The second repetition fails if the first one's rows survive. */
    @RepeatedTest(2)
    void everyTestStartsWithAnEmptyDatabase() {
        assertThat(sellerRepository.count()).isZero();
        assertThat(shopRepository.count()).isZero();
        assertThat(customerRepository.count()).isZero();
        assertThat(cartRepository.count()).isZero();
        assertThat(transactionRepository.count()).isZero();

        Seller seller = sellerRepository.save(new Seller("Ada", "ada@example.com", "pw", "555"));
        Shop shop = new Shop("Lantern Works", "Lamps", "Lighting", seller);
        shop.addProduct(new Item("Lamp", "Brass", 89.0, 4, "Lighting"));
        shop = shopRepository.save(shop);
        Customer bo = customerRepository.save(new Customer("Bo", "bo@example.com", "pw", "555", "1 Main St"));

        Cart cart = new Cart(bo);
        cart.addProduct(shop.getProducts().getFirst(), 1);
        cartRepository.save(cart);
        transactionRepository.save(new Transaction(shop, shop.getProducts().getFirst(), bo));
    }
}
