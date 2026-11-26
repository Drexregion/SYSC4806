package ca.carletonu.cqrs.handlers;

import ca.carletonu.cqrs.commands.ChangeCartQuantityCommand;
import ca.carletonu.cqrs.commands.RemoveFromCartCommand;
import ca.carletonu.models.Cart;
import ca.carletonu.models.CartStatus;
import ca.carletonu.models.Customer;
import ca.carletonu.models.Item;
import ca.carletonu.models.Service;
import ca.carletonu.models.repositories.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChangeCartCommandHandlerTests {

    private static final long CUSTOMER_ID = 1L;
    private static final long LAMP_ID = 10L;
    private static final long REWIRING_ID = 11L;

    private CartRepository cartRepository;
    private ChangeCartQuantityCommandHandler changeHandler;
    private RemoveFromCartCommandHandler removeHandler;
    private Cart cart;
    private Item lamp;

    @BeforeEach
    void setUp() {
        cartRepository = mock(CartRepository.class);
        changeHandler = new ChangeCartQuantityCommandHandler(cartRepository);
        removeHandler = new RemoveFromCartCommandHandler(cartRepository);

        lamp = new Item("Brass Lamp", "Polished brass", 89.0, 3, "Lamp");
        Service rewiring = new Service("Rewiring", "Made safe", 45.0, 45);
        ReflectionTestUtils.setField(lamp, "id", LAMP_ID);
        ReflectionTestUtils.setField(rewiring, "id", REWIRING_ID);

        cart = new Cart(new Customer("Sam", "sam@example.com", "pw", "555", "1 Main St"));
        cart.addProduct(lamp, 2);
        cart.addProduct(rewiring, 1);
        when(cartRepository.findByCustomerIdAndStatus(CUSTOMER_ID, CartStatus.OPEN)).thenReturn(Optional.of(cart));
    }

    @Test
    void changesTheQuantityUpToTheStock() {
        change(LAMP_ID, 3);

        assertThat(quantityOfLamp()).isEqualTo(3);
        verify(cartRepository).save(cart);
    }

    @Test
    void lowersTheQuantity() {
        change(LAMP_ID, 1);

        assertThat(quantityOfLamp()).isEqualTo(1);
    }

    @Test
    void refusesMoreThanTheStock() {
        assertRefused(LAMP_ID, 4, "Only 3 of Brass Lamp in stock.");
    }

    @Test
    void refusesAnItemThatHasSoldOut() {
        lamp.setQuantity(0);

        assertRefused(LAMP_ID, 1, "Brass Lamp is out of stock.");
    }

    @Test
    void refusesAQuantityBelowOneOrMissing() {
        assertRefused(LAMP_ID, 0, "Choose a quantity of at least one.");
        assertRefused(LAMP_ID, -1, "Choose a quantity of at least one.");
        assertRefused(LAMP_ID, null, "Choose a quantity of at least one.");
    }

    @Test
    void aServiceHasNoStockLimit() {
        change(REWIRING_ID, 50);

        assertThat(cart.getItems().get(1).getQuantity()).isEqualTo(50);
    }

    @Test
    void refusesToChangeAProductThatIsNotInTheCart() {
        assertRefused(99L, 1, "That product is not in your cart.");
    }

    @Test
    void refusesWhenThereIsNoOpenCart() {
        when(cartRepository.findByCustomerIdAndStatus(CUSTOMER_ID, CartStatus.OPEN)).thenReturn(Optional.empty());

        assertRefused(LAMP_ID, 1, "That product is not in your cart.");
        assertThatThrownBy(() -> removeHandler.handle(new RemoveFromCartCommand(CUSTOMER_ID, LAMP_ID)))
                .hasMessage("That product is not in your cart.");
    }

    @Test
    void removesOnlyTheChosenLine() {
        removeHandler.handle(new RemoveFromCartCommand(CUSTOMER_ID, LAMP_ID));

        assertThat(cart.getItems()).singleElement()
                .satisfies(line -> assertThat(line.getProduct().getId()).isEqualTo(REWIRING_ID));
        verify(cartRepository).save(cart);
    }

    @Test
    void refusesToRemoveAProductThatIsNotInTheCart() {
        assertThatThrownBy(() -> removeHandler.handle(new RemoveFromCartCommand(CUSTOMER_ID, 99L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("That product is not in your cart.");
        verify(cartRepository, never()).save(any());
    }

    @Test
    void noCustomerIsRefusedBeforeTheQuantityIsChecked() {
        // The controller passes a null customer when there is no shopper to stand in
        assertThatThrownBy(() -> changeHandler.handle(new ChangeCartQuantityCommand(null, LAMP_ID, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("There is no customer account with a cart yet.");
        verify(cartRepository, never()).save(any());
    }

    @Test
    void noCustomerCannotRemove() {
        assertThatThrownBy(() -> removeHandler.handle(new RemoveFromCartCommand(null, LAMP_ID)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("There is no customer account with a cart yet.");
        verify(cartRepository, never()).save(any());
    }

    private void change(long productId, Integer quantity) {
        changeHandler.handle(new ChangeCartQuantityCommand(CUSTOMER_ID, productId, quantity));
    }

    private void assertRefused(long productId, Integer quantity, String message) {
        assertThatThrownBy(() -> change(productId, quantity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
        assertThat(quantityOfLamp()).isEqualTo(2);
        verify(cartRepository, never()).save(any());
    }

    private int quantityOfLamp() {
        return cart.getItems().get(0).getQuantity();
    }
}
