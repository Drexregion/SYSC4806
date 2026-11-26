package ca.carletonu.models;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionTests {

    private Shop shop;
    private Customer customer;
    private Item lamp;

    @BeforeEach
    void setUp() {
        shop = new Shop("Lantern Works", "Lamps", "Lighting", null);
        customer = new Customer("Bo", "bo@example.com", "pw", "555", "1 Main St");
        lamp = new Item("Lamp", "Brass", 89.0, 4, "Lighting");
        shop.addProduct(lamp);
    }

    @Test
    void constructorSetsParties() {
        Transaction transaction = new Transaction(shop, lamp, customer);

        assertThat(transaction.getShop()).isSameAs(shop);
        assertThat(transaction.getProduct()).isSameAs(lamp);
        assertThat(transaction.getCustomer()).isSameAs(customer);
        assertThat(transaction.getId()).isNull();
    }

    @Test
    void buyingAProductTheShopSellsSucceeds() {
        Transaction transaction = new Transaction(shop, lamp, customer);

        assertThat(transaction.getDate()).isNull();

        assertThat(transaction.buy(lamp)).isTrue();
        assertThat(transaction.getDate()).isNotNull();
    }

    @Test
    void buyingRemovesTheProductFromTheShop() {
        // Current behaviour: buy() takes the product off the shop entirely,
        // whatever its inventory. Update this test when checkout decrements stock instead.
        new Transaction(shop, lamp, customer).buy(lamp);

        assertThat(shop.getProducts()).doesNotContain(lamp);
    }

    @Test
    void buyingAProductTheShopDoesNotSellFails() {
        Item other = new Item("Other", "Not sold here", 5.0, 1, "x");
        Transaction transaction = new Transaction(shop, other, customer);

        assertThat(transaction.buy(other)).isFalse();
        assertThat(transaction.getDate()).isNull();
        assertThat(shop.getProducts()).containsExactly(lamp);
    }

    @Test
    void buyingTheSameProductTwiceFailsTheSecondTime() {
        Transaction transaction = new Transaction(shop, lamp, customer);

        assertThat(transaction.buy(lamp)).isTrue();
        assertThat(transaction.buy(lamp)).isFalse();
    }

    @Test
    void settersUpdateParties() {
        Transaction transaction = new Transaction();
        transaction.setShop(shop);
        transaction.setProduct(lamp);
        transaction.setCustomer(customer);

        assertThat(transaction.getShop()).isSameAs(shop);
        assertThat(transaction.getProduct()).isSameAs(lamp);
        assertThat(transaction.getCustomer()).isSameAs(customer);
    }

    @Test
    void aCheckoutPurchaseRecordsItsCartQuantityPriceAndDate() {
        Transaction transaction = new Transaction(7L, shop, lamp, customer, 3, 89.0);

        assertThat(transaction.getCartId()).isEqualTo(7L);
        assertThat(transaction.getShop()).isSameAs(shop);
        assertThat(transaction.getProduct()).isSameAs(lamp);
        assertThat(transaction.getCustomer()).isSameAs(customer);
        assertThat(transaction.getQuantity()).isEqualTo(3);
        assertThat(transaction.getUnitPrice()).isEqualTo(89.0);
        assertThat(transaction.getDate()).isNotNull();
    }

    @Test
    void aCheckoutPurchaseLeavesTheShopsProductsAlone() {
        new Transaction(7L, shop, lamp, customer, 1, 89.0);

        // Unlike buy(), which takes the product off the shop
        assertThat(shop.getProducts()).containsExactly(lamp);
    }

    @Test
    void theSubtotalIsUnitPriceTimesQuantity() {
        assertThat(new Transaction(7L, shop, lamp, customer, 3, 89.0).getSubtotal()).isEqualTo(267.0);
    }

    @Test
    void aMissingPriceOrQuantityGivesASubtotalOfZero() {
        assertThat(new Transaction(7L, shop, lamp, customer, 3, null).getSubtotal()).isZero();
        assertThat(new Transaction(shop, lamp, customer).getSubtotal()).isZero();
    }
}
