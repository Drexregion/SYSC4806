package ca.carletonu.models.repositories;

import ca.carletonu.models.Cart;
import ca.carletonu.models.CartItem;
import ca.carletonu.models.CartStatus;
import ca.carletonu.models.Customer;
import ca.carletonu.models.Item;
import ca.carletonu.models.Product;
import ca.carletonu.models.Service;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cart and CartItem persistence, including the rule that a customer has at
 * most one open cart, which the database holds as well as the entity.
 */
@RepositoryTest
class CartRepositoryTests {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    private Customer bo;
    private Product lamp;
    private Product rewiring;

    @BeforeEach
    void setUp() {
        bo = customerRepository.save(new Customer("Bo", "bo@example.com", "pw", "555", "1 Main St"));
        lamp = productRepository.save(new Item("Lamp", "Brass", 89.0, 4, "Lighting"));
        rewiring = productRepository.save(new Service("Rewiring", "Make it safe", 45.0, 45));
    }

    @Test
    void aCartAndItsLinesArePersisted() {
        Cart cart = new Cart(bo);
        cart.addProduct(lamp, 2);
        cart.addProduct(rewiring, 1);
        Long id = cartRepository.save(cart).getId();
        flushAndClear();

        Cart reloaded = cartRepository.findById(id).orElseThrow();

        assertThat(reloaded.getCustomer().getName()).isEqualTo("Bo");
        assertThat(reloaded.getStatus()).isEqualTo(CartStatus.OPEN);
        assertThat(reloaded.getItems())
                .extracting(line -> line.getProduct().getName(), CartItem::getQuantity)
                .containsExactlyInAnyOrder(
                        Tuple.tuple("Lamp", 2),
                        Tuple.tuple("Rewiring", 1));
        assertThat(cartItemRepository.findByCartId(id)).hasSize(2);
    }

    @Test
    void removingALineDeletesItsRow() {
        Cart cart = new Cart(bo);
        cart.addProduct(lamp, 2);
        cart.addProduct(rewiring, 1);
        cart = cartRepository.save(cart);
        flushAndClear();

        Cart reloaded = cartRepository.findById(cart.getId()).orElseThrow();
        reloaded.removeProduct(reloaded.getItems().stream()
                .filter(line -> line.getProduct().getName().equals("Lamp"))
                .findFirst().orElseThrow().getProduct());
        cartRepository.save(reloaded);
        flushAndClear();

        assertThat(cartItemRepository.findByCartId(cart.getId()))
                .extracting(line -> line.getProduct().getName())
                .containsExactly("Rewiring");
    }

    @Test
    void openCartForReturnsTheSameCartEachTime() {
        Cart first = cartRepository.openCartFor(bo);
        flushAndClear();
        Cart second = cartRepository.openCartFor(bo);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(cartRepository.findByCustomerId(bo.getId())).hasSize(1);
    }

    @Test
    void afterCheckoutTheCustomerGetsANewOpenCart() {
        Cart first = cartRepository.openCartFor(bo);
        first.addProduct(lamp, 1);
        first.checkOut();
        cartRepository.save(first);
        flushAndClear();

        Cart next = cartRepository.openCartFor(bo);
        flushAndClear();

        assertThat(next.getId()).isNotEqualTo(first.getId());
        assertThat(cartRepository.findOpenCart(bo)).get().extracting(Cart::getId).isEqualTo(next.getId());
        assertThat(cartRepository.findByCustomerId(bo.getId())).hasSize(2);
    }

    @Test
    void manyCheckedOutCartsCanBelongToOneCustomer() {
        for (int i = 0; i < 3; i++) {
            Cart cart = cartRepository.openCartFor(bo);
            cart.addProduct(lamp, 1);
            cart.checkOut();
            cartRepository.save(cart);
            flushAndClear();
        }

        assertThat(cartRepository.findByCustomerId(bo.getId()))
                .hasSize(3)
                .allSatisfy(cart -> assertThat(cart.isOpen()).isFalse());
    }

    @Test
    void theDatabaseRefusesASecondOpenCartForTheSameCustomer() {
        cartRepository.save(new Cart(bo));
        flushAndClear();

        // Bypasses openCartFor, as a careless caller or a REST client would
        cartRepository.save(new Cart(bo));

        assertThatThrownBy(entityManager::flush)
                .isInstanceOfAny(PersistenceException.class, DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("OPEN_FOR_CUSTOMER_ID");
    }

    @Test
    void differentCustomersEachHaveTheirOwnOpenCart() {
        Customer al = customerRepository.save(new Customer("Al", "al@example.com", "pw", "556", "2 Main St"));

        Cart bosCart = cartRepository.openCartFor(bo);
        Cart alsCart = cartRepository.openCartFor(al);
        flushAndClear();

        assertThat(bosCart.getId()).isNotEqualTo(alsCart.getId());
    }

    @Test
    void aZeroQuantityLineCannotBeWritten() {
        Cart cart = new Cart(bo);
        CartItem line = cart.addProduct(lamp, 1);
        // Force the field past the setter, as a JSON body could
        ReflectionTestUtils.setField(line, "quantity", 0);

        assertThatThrownBy(() -> {
            cartRepository.save(cart);
            entityManager.flush();
        }).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
