package ca.carletonu.cqrs.handlers;

import ca.carletonu.cqrs.commands.CheckoutCommand;
import ca.carletonu.models.Cart;
import ca.carletonu.models.CartStatus;
import ca.carletonu.models.Customer;
import ca.carletonu.models.Item;
import ca.carletonu.models.Service;
import ca.carletonu.models.repositories.CartRepository;
import ca.carletonu.models.repositories.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The rules for checking out, with the repositories mocked so the handler is
 * tested with no HTTP and no database. CheckoutIntegrationTests covers the
 * same path over HTTP against a real database.
 */
class CheckoutCommandHandlerTests {

    private static final long CUSTOMER_ID = 1L;

    private CartRepository cartRepository;
    private CheckoutCommandHandler handler;

    private Customer sam;
    private Cart cart;
    private Item lamp;
    private Item tea;
    private Service rewiring;

    @BeforeEach
    void setUp() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        cartRepository = mock(CartRepository.class);
        handler = new CheckoutCommandHandler(customerRepository, cartRepository);

        sam = new Customer("Sam", "sam@example.com", "pw", "555", "1 Main St");
        cart = new Cart(sam);
        lamp = new Item("Brass Lamp", "Polished brass", 89.0, 3, "Lamp");
        tea = new Item("Sencha", "Green tea", 14.5, 5, "Tea");
        rewiring = new Service("Rewiring", "Made safe", 45.0, 45);

        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(sam));
        // findOpenCart is a default method on the interface; the mock replaces it too
        when(cartRepository.findOpenCart(sam)).thenReturn(Optional.of(cart));
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

        assertThat(result.itemCount()).isEqualTo(3);
        assertThat(result.total()).isCloseTo(2 * 89.0 + 45.0, within(0.001));
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

        assertRefused(checkout(), "Your cart has more than is in stock: only 1 of Brass Lamp left.");

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

        assertRefused(checkout(), "Your cart has more than is in stock: "
                + "Brass Lamp is out of stock, only 2 of Sencha left.");
    }

    @Test
    void noCustomerIsRefused() {
        assertRefused(new CheckoutCommand(null), "There is no customer account to check out.");
    }

    @Test
    void anUnknownCustomerIsRefused() {
        assertRefused(new CheckoutCommand(999L), "There is no customer account to check out.");
    }

    private void assertRefused(CheckoutCommand command, String message) {
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);

        verify(cartRepository, never()).save(any());
    }

    private static CheckoutCommand checkout() {
        return new CheckoutCommand(CUSTOMER_ID);
    }
}
