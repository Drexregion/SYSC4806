package ca.carletonu.cqrs.handlers;

import ca.carletonu.cqrs.commands.CheckoutCommand;
import ca.carletonu.events.CheckoutCompletedEvent;
import ca.carletonu.messaging.KafkaProducer;
import ca.carletonu.models.Cart;
import ca.carletonu.models.CartStatus;
import ca.carletonu.models.Customer;
import ca.carletonu.models.Item;
import ca.carletonu.models.Service;
import ca.carletonu.models.Shop;
import ca.carletonu.models.Transaction;
import ca.carletonu.models.repositories.CartRepository;
import ca.carletonu.models.repositories.CustomerRepository;
import ca.carletonu.models.repositories.ShopRepository;
import ca.carletonu.models.repositories.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The rules for checking out, with the repositories mocked so the handler is
 * tested with no HTTP and no database. CheckoutIntegrationTests covers the
 * same path over HTTP against a real database.
 */
class CheckoutCommandHandlerTests {

    private static final long CUSTOMER_ID = 1L;
    private static final long CART_ID = 7L;

    private CartRepository cartRepository;
    private ShopRepository shopRepository;
    private TransactionRepository transactionRepository;
    private ObjectProvider<KafkaProducer> kafkaProducerProvider;
    private CheckoutCommandHandler handler;

    private Customer sam;
    private Cart cart;
    private Shop shop;
    private Item lamp;
    private Item tea;
    private Service rewiring;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        cartRepository = mock(CartRepository.class);
        shopRepository = mock(ShopRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        kafkaProducerProvider = mock(ObjectProvider.class);
        handler = new CheckoutCommandHandler(customerRepository, cartRepository, shopRepository,
                transactionRepository, kafkaProducerProvider);

        sam = new Customer("Sam", "sam@example.com", "pw", "555", "1 Main St");
        cart = new Cart(sam);
        shop = new Shop("Lantern Works", "Lamps", "Lighting", null);
        lamp = new Item("Brass Lamp", "Polished brass", 89.0, 3, "Lamp");
        tea = new Item("Sencha", "Green tea", 14.5, 5, "Tea");
        rewiring = new Service("Rewiring", "Made safe", 45.0, 45);

        // Ids as the database would give them, so each product can be told apart
        ReflectionTestUtils.setField(cart, "id", CART_ID);
        ReflectionTestUtils.setField(shop, "id", 100L);
        ReflectionTestUtils.setField(lamp, "id", 10L);
        ReflectionTestUtils.setField(tea, "id", 11L);
        ReflectionTestUtils.setField(rewiring, "id", 12L);

        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(sam));
        // findOpenCart is a default method on the interface; the mock replaces it too
        when(cartRepository.findOpenCart(sam)).thenReturn(Optional.of(cart));
        when(shopRepository.findFirstByProducts_Id(any())).thenReturn(Optional.of(shop));
    }

    @Test
    void checkingOutClosesTheCartAndSavesIt() {
        cart.addProduct(lamp, 2);

        handler.handle(checkout());

        assertThat(cart.getStatus()).isEqualTo(CartStatus.CHECKED_OUT);
        verify(cartRepository).save(cart);
    }

    @Test
    void checkingOutTakesEachItemOutOfStock() {
        cart.addProduct(lamp, 2);
        cart.addProduct(tea, 5);

        handler.handle(checkout());

        assertThat(lamp.getQuantity()).isEqualTo(1);
        assertThat(tea.getQuantity()).isZero();
    }

    @Test
    void aServiceHasNoStockToTake() {
        cart.addProduct(rewiring, 4);

        handler.handle(checkout());

        assertThat(cart.isOpen()).isFalse();
    }

    @Test
    void theResultSaysWhatWasBought() {
        cart.addProduct(lamp, 2);
        cart.addProduct(rewiring, 1);

        CheckoutResult result = handler.handle(checkout());

        assertThat(result.cartId()).isEqualTo(CART_ID);
        assertThat(result.itemCount()).isEqualTo(3);
        assertThat(result.total()).isCloseTo(2 * 89.0 + 45.0, within(0.001));
    }

    @Test
    void eachLineIsRecordedAsATransaction() {
        cart.addProduct(lamp, 2);
        cart.addProduct(tea, 1);

        handler.handle(checkout());

        List<Transaction> saved = savedTransactions(2);
        assertThat(saved).extracting(Transaction::getProduct).containsExactly(lamp, tea);
        assertThat(saved).allSatisfy(transaction -> {
            assertThat(transaction.getCustomer()).isSameAs(sam);
            assertThat(transaction.getShop()).isSameAs(shop);
            assertThat(transaction.getCartId()).isEqualTo(CART_ID);
            assertThat(transaction.getDate()).isNotNull();
        });
        assertThat(saved.get(0).getQuantity()).isEqualTo(2);
        assertThat(saved.get(0).getUnitPrice()).isEqualTo(89.0);
        assertThat(saved.get(1).getQuantity()).isEqualTo(1);
    }

    @Test
    void aServiceIsRecordedAsATransactionToo() {
        cart.addProduct(rewiring, 3);

        handler.handle(checkout());

        assertThat(savedTransactions(1)).singleElement().satisfies(transaction -> {
            assertThat(transaction.getProduct()).isSameAs(rewiring);
            assertThat(transaction.getQuantity()).isEqualTo(3);
        });
    }

    @Test
    void theRecordedPriceIsThePriceAtCheckout() {
        cart.addProduct(lamp, 1);

        handler.handle(checkout());
        lamp.setPrice(120.0);

        assertThat(savedTransactions(1).get(0).getUnitPrice()).isEqualTo(89.0);
    }

    @Test
    void anEmptyCartIsRefused() {
        assertRefused(checkout(), "Your cart is empty.");
        assertThat(cart.isOpen()).isTrue();
    }

    @Test
    void noOpenCartIsRefusedAndNoCartIsOpened() {
        when(cartRepository.findOpenCart(sam)).thenReturn(Optional.empty());

        assertRefused(checkout(), "Your cart is empty.");
        verify(cartRepository, never()).openCartFor(any());
    }

    @Test
    void moreThanIsInStockIsRefusedAndNothingChanges() {
        cart.addProduct(lamp, 2);
        cart.addProduct(tea, 1);
        // Stock fell after the lamp was added: adding does not reserve it
        lamp.setQuantity(1);

        assertRefused(checkout(), "Checkout was refused: only 1 of Brass Lamp left.");

        assertThat(cart.isOpen()).isTrue();
        assertThat(lamp.getQuantity()).isEqualTo(1);
        assertThat(tea.getQuantity()).isEqualTo(5);
    }

    @Test
    void everyShortItemIsNamedAtOnce() {
        cart.addProduct(lamp, 2);
        cart.addProduct(tea, 3);
        lamp.setQuantity(0);
        tea.setQuantity(2);

        assertRefused(checkout(), "Checkout was refused: "
                + "Brass Lamp is out of stock, only 2 of Sencha left.");
    }

    @Test
    void aProductNoShopSellsIsRefusedAndNothingChanges() {
        cart.addProduct(lamp, 1);
        when(shopRepository.findFirstByProducts_Id(10L)).thenReturn(Optional.empty());

        assertRefused(checkout(), "Checkout was refused: Brass Lamp is no longer sold by any shop.");

        assertThat(cart.isOpen()).isTrue();
        assertThat(lamp.getQuantity()).isEqualTo(3);
    }

    @Test
    void stockAndShopProblemsAreNamedTogether() {
        cart.addProduct(lamp, 2);
        cart.addProduct(tea, 1);
        lamp.setQuantity(1);
        when(shopRepository.findFirstByProducts_Id(11L)).thenReturn(Optional.empty());

        assertRefused(checkout(), "Checkout was refused: "
                + "only 1 of Brass Lamp left, Sencha is no longer sold by any shop.");
    }

    @Test
    void noCustomerIsRefused() {
        assertRefused(new CheckoutCommand(null), "There is no customer account to check out.");
    }

    @Test
    void anUnknownCustomerIsRefused() {
        assertRefused(new CheckoutCommand(999L), "There is no customer account to check out.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aCheckoutIsPublishedWhenABrokerIsConfigured() {
        KafkaProducer producer = mock(KafkaProducer.class);
        doAnswer(invocation -> {
            ((Consumer<KafkaProducer>) invocation.getArgument(0)).accept(producer);
            return null;
        }).when(kafkaProducerProvider).ifAvailable(any());
        cart.addProduct(lamp, 2);
        cart.addProduct(rewiring, 1);

        handler.handle(checkout());

        ArgumentCaptor<CheckoutCompletedEvent> published = ArgumentCaptor.forClass(CheckoutCompletedEvent.class);
        verify(producer).sendCheckoutCompletedEvent(published.capture());
        CheckoutCompletedEvent event = published.getValue();
        assertThat(event.cartId()).isEqualTo(CART_ID);
        assertThat(event.total()).isCloseTo(2 * 89.0 + 45.0, within(0.001));
        assertThat(event.lines()).containsExactly(
                new CheckoutCompletedEvent.Line(10L, "Brass Lamp", 100L, 2, 89.0),
                new CheckoutCompletedEvent.Line(12L, "Rewiring", 100L, 1, 45.0));
    }

    @Test
    void withoutABrokerTheCheckoutStillHappens() {
        // The provider mock has no producer to hand over, as with no kafka profile
        cart.addProduct(lamp, 1);

        handler.handle(checkout());

        assertThat(cart.isOpen()).isFalse();
        savedTransactions(1);
    }

    private void assertRefused(CheckoutCommand command, String message) {
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);

        verify(cartRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(kafkaProducerProvider);
    }

    private List<Transaction> savedTransactions(int expected) {
        ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(expected)).save(saved.capture());
        return saved.getAllValues();
    }

    private static CheckoutCommand checkout() {
        return new CheckoutCommand(CUSTOMER_ID);
    }
}
