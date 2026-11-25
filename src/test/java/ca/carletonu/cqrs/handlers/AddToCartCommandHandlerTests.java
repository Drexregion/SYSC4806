package ca.carletonu.cqrs.handlers;

import ca.carletonu.cqrs.commands.AddToCartCommand;
import ca.carletonu.events.CartItemAddedEvent;
import ca.carletonu.messaging.KafkaProducer;
import ca.carletonu.models.Cart;
import ca.carletonu.models.CartItem;
import ca.carletonu.models.Customer;
import ca.carletonu.models.Item;
import ca.carletonu.models.Service;
import ca.carletonu.models.repositories.CartRepository;
import ca.carletonu.models.repositories.CustomerRepository;
import ca.carletonu.models.repositories.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The rules for adding to a cart, with the repositories mocked so each rule is
 * checked on its own. AddToCartIntegrationTests covers the same path against a
 * real database over HTTP.
 */
class AddToCartCommandHandlerTests {

    private static final long CUSTOMER_ID = 1L;
    private static final long LAMP_ID = 10L;
    private static final long REWIRING_ID = 11L;
    private static final long SOLD_OUT_ID = 12L;

    private CartRepository cartRepository;
    private ObjectProvider<KafkaProducer> kafkaProducerProvider;
    private AddToCartCommandHandler handler;

    private Customer sam;
    private Cart cart;
    private Item lamp;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        cartRepository = mock(CartRepository.class);
        kafkaProducerProvider = mock(ObjectProvider.class);
        handler = new AddToCartCommandHandler(customerRepository, productRepository, cartRepository,
                kafkaProducerProvider);

        sam = new Customer("Sam", "sam@example.com", "pw", "555", "1 Main St");
        cart = new Cart(sam);
        lamp = new Item("Brass Lamp", "Polished brass", 89.0, 3, "Lamp");

        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(sam));
        when(productRepository.findById(LAMP_ID)).thenReturn(Optional.of(lamp));
        when(productRepository.findById(REWIRING_ID))
                .thenReturn(Optional.of(new Service("Rewiring", "Made safe", 45.0, 45)));
        when(productRepository.findById(SOLD_OUT_ID))
                .thenReturn(Optional.of(new Item("Sold Out Tea", "Gone", 12.0, 0, "Tea")));

        // findOpenCart and openCartFor are default methods on the interface,
        // and a mock replaces those too, so both are stubbed explicitly. One
        // open cart, empty to start: an empty cart counts the same as no cart
        // for the stock check, and the refused cases verify that no cart is
        // opened, which is the write that matters.
        when(cartRepository.findOpenCart(sam)).thenReturn(Optional.of(cart));
        when(cartRepository.openCartFor(sam)).thenReturn(cart);
    }

    @Test
    void anItemWithinStockIsAddedAndSaved() {
        CartItem line = handler.handle(add(LAMP_ID, 2));

        assertThat(line.getProduct()).isSameAs(lamp);
        assertThat(line.getQuantity()).isEqualTo(2);
        verify(cartRepository).save(cart);
    }

    @Test
    void addingTheSameProductTwiceRaisesTheQuantity() {
        handler.handle(add(LAMP_ID, 1));
        handler.handle(add(LAMP_ID, 1));

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void exactlyTheStockOnHandIsAllowed() {
        CartItem line = handler.handle(add(LAMP_ID, 3));

        assertThat(line.getQuantity()).isEqualTo(3);
    }

    @Test
    void moreThanTheStockOnHandIsRefusedAndNothingIsWritten() {
        assertThatThrownBy(() -> handler.handle(add(LAMP_ID, 4)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only 3 of Brass Lamp in stock.");

        verify(cartRepository, never()).openCartFor(any());
        verify(cartRepository, never()).save(any());
        verifyNoInteractions(kafkaProducerProvider);
    }

    @Test
    void whatIsAlreadyInTheCartCountsAgainstTheStock() {
        handler.handle(add(LAMP_ID, 2));

        assertThatThrownBy(() -> handler.handle(add(LAMP_ID, 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only 3 of Brass Lamp in stock, and your cart already has 2.");

        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void anItemWithNoStockIsRefused() {
        assertThatThrownBy(() -> handler.handle(add(SOLD_OUT_ID, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sold Out Tea is out of stock.");

        verify(cartRepository, never()).save(any());
    }

    @Test
    void aServiceIsAddedWithoutAStockCheck() {
        CartItem line = handler.handle(add(REWIRING_ID, 50));

        assertThat(line.getQuantity()).isEqualTo(50);
        verify(cartRepository).save(cart);
    }

    @Test
    void aQuantityOfZeroIsRefused() {
        assertThatThrownBy(() -> handler.handle(add(LAMP_ID, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Choose a quantity of at least one.");

        verify(cartRepository, never()).save(any());
    }

    @Test
    void aMissingQuantityIsRefused() {
        assertThatThrownBy(() -> handler.handle(new AddToCartCommand(CUSTOMER_ID, LAMP_ID, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Choose a quantity of at least one.");
    }

    @Test
    void anUnknownProductIsNotFound() {
        assertThatThrownBy(() -> handler.handle(add(999L, 1)))
                .isInstanceOf(ProductNotFoundException.class);

        verify(cartRepository, never()).save(any());
    }

    @Test
    void anUnknownCustomerIsRefused() {
        assertThatThrownBy(() -> handler.handle(new AddToCartCommand(999L, LAMP_ID, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("There is no customer account to add to.");

        verify(cartRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void anAddIsPublishedWhenABrokerIsConfigured() {
        KafkaProducer producer = mock(KafkaProducer.class);
        doAnswer(invocation -> {
            ((Consumer<KafkaProducer>) invocation.getArgument(0)).accept(producer);
            return null;
        }).when(kafkaProducerProvider).ifAvailable(any());

        handler.handle(add(LAMP_ID, 1));
        handler.handle(add(LAMP_ID, 2));

        ArgumentCaptor<CartItemAddedEvent> published = ArgumentCaptor.forClass(CartItemAddedEvent.class);
        verify(producer, org.mockito.Mockito.times(2)).sendCartItemAddedEvent(published.capture());
        CartItemAddedEvent second = published.getAllValues().get(1);
        assertThat(second.quantityAdded()).isEqualTo(2);
        assertThat(second.lineQuantity()).isEqualTo(3);
    }

    @Test
    void withoutABrokerTheProductIsStillAdded() {
        // The provider mock has no producer to hand over, as with no kafka profile.
        CartItem line = handler.handle(add(LAMP_ID, 1));

        assertThat(line.getQuantity()).isEqualTo(1);
        verify(cartRepository).save(cart);
    }

    private static AddToCartCommand add(long productId, int quantity) {
        return new AddToCartCommand(CUSTOMER_ID, productId, quantity);
    }
}
