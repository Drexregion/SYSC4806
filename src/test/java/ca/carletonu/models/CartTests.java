package ca.carletonu.models;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Cart and CartItem rules, with no database.
 */
class CartTests {

    private Customer bo;
    private Item lamp;
    private Service rewiring;
    private Cart cart;

    @BeforeEach
    void setUp() {
        bo = new Customer("Bo", "bo@example.com", "pw", "555", "1 Main St");
        lamp = new Item("Lamp", "Brass", 89.0, 4, "Lighting");
        rewiring = new Service("Rewiring", "Make it safe", 45.0, 45);
        cart = new Cart(bo);
    }

    @Test
    void aNewCartIsOpenAndEmpty() {
        assertThat(cart.getCustomer()).isSameAs(bo);
        assertThat(cart.isOpen()).isTrue();
        assertThat(cart.getStatus()).isEqualTo(CartStatus.OPEN);
        assertThat(cart.getItems()).isEmpty();
        assertThat(cart.getItemCount()).isZero();
        assertThat(cart.getTotal()).isZero();
    }

    @Test
    void aCartNeedsACustomer() {
        assertThatThrownBy(() -> new Cart(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addingAProductMakesALineWithThatQuantity() {
        CartItem line = cart.addProduct(lamp, 2);

        assertThat(cart.getItems()).containsExactly(line);
        assertThat(line.getProduct()).isSameAs(lamp);
        assertThat(line.getQuantity()).isEqualTo(2);
        assertThat(line.getCart()).isSameAs(cart);
    }

    @Test
    void addingTheSameProductAgainRaisesTheQuantityInsteadOfAddingALine() {
        cart.addProduct(lamp, 1);
        cart.addProduct(lamp, 2);

        assertThat(cart.getItems()).singleElement()
                .satisfies(line -> assertThat(line.getQuantity()).isEqualTo(3));
    }

    @Test
    void theSameSavedProductLoadedTwiceIsStillOneLine() {
        ReflectionTestUtils.setField(lamp, "id", 5L);
        Item sameLampLoadedAgain = new Item("Lamp", "Brass", 89.0, 4, "Lighting");
        ReflectionTestUtils.setField(sameLampLoadedAgain, "id", 5L);

        cart.addProduct(lamp, 1);
        cart.addProduct(sameLampLoadedAgain, 1);

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItemCount()).isEqualTo(2);
    }

    @Test
    void itemsAndServicesCanShareACart() {
        cart.addProduct(lamp, 2);
        cart.addProduct(rewiring, 1);

        assertThat(cart.getItems()).hasSize(2);
        assertThat(cart.getItemCount()).isEqualTo(3);
        assertThat(cart.getTotal()).isCloseTo(2 * 89.0 + 45.0, within(0.001));
    }

    @Test
    void zeroOrNegativeQuantityCannotBeAdded() {
        assertThatThrownBy(() -> cart.addProduct(lamp, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cart.addProduct(lamp, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void aLineNeedsAProduct() {
        assertThatThrownBy(() -> cart.addProduct(null, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aLinesQuantityCannotBeSetToZeroDirectly() {
        CartItem line = cart.addProduct(lamp, 1);

        assertThatThrownBy(() -> line.setQuantity(0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(line.getQuantity()).isEqualTo(1);
    }

    @Test
    void settingTheQuantityThroughTheCartReplacesIt() {
        cart.addProduct(lamp, 1);

        cart.setQuantity(lamp, 4);

        assertThat(cart.getItemCount()).isEqualTo(4);
    }

    @Test
    void settingTheQuantityToZeroRemovesTheLine() {
        cart.addProduct(lamp, 3);

        cart.setQuantity(lamp, 0);

        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void settingTheQuantityOfAProductNotInTheCartIsRejected() {
        assertThatThrownBy(() -> cart.setQuantity(lamp, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removingAProductTakesItsLineOut() {
        cart.addProduct(lamp, 2);
        cart.addProduct(rewiring, 1);

        assertThat(cart.removeProduct(lamp)).isTrue();
        assertThat(cart.removeProduct(lamp)).isFalse();
        assertThat(cart.getItems()).extracting(CartItem::getProduct).containsExactly(rewiring);
    }

    @Test
    void theItemsListCannotBeChangedFromOutside() {
        cart.addProduct(lamp, 1);

        assertThatThrownBy(() -> cart.getItems().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aSubtotalIsPriceTimesQuantity() {
        CartItem line = cart.addProduct(lamp, 3);

        assertThat(line.getSubtotal()).isCloseTo(267.0, within(0.001));
    }

    @Test
    void aProductWithNoPriceAddsNothingToTheTotal() {
        cart.addProduct(new Item("Freebie", null, null, 1, null), 2);

        assertThat(cart.getTotal()).isZero();
    }

    @Test
    void checkingOutClosesTheCart() {
        cart.addProduct(lamp, 1);

        cart.checkOut();

        assertThat(cart.isOpen()).isFalse();
        assertThat(cart.getStatus()).isEqualTo(CartStatus.CHECKED_OUT);
    }

    @Test
    void anEmptyCartCannotBeCheckedOut() {
        assertThatThrownBy(cart::checkOut).isInstanceOf(IllegalStateException.class);
        assertThat(cart.isOpen()).isTrue();
    }

    @Test
    void aCheckedOutCartCannotChange() {
        cart.addProduct(lamp, 1);
        cart.checkOut();

        assertThatThrownBy(() -> cart.addProduct(rewiring, 1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cart.setQuantity(lamp, 2)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cart.removeProduct(lamp)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(cart::checkOut).isInstanceOf(IllegalStateException.class);
    }
}
