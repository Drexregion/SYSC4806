package ca.carletonu.models;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit tests for Shop
 */
class ShopTests {

    @Test
    void constructorSetsFields() {
        Seller seller = new Seller("Ada", "ada@example.com", "pw", "555-0100");
        Shop shop = new Shop("Kettle Corner", "Teas", "Tea", seller);

        assertThat(shop.getName()).isEqualTo("Kettle Corner");
        assertThat(shop.getDescription()).isEqualTo("Teas");
        assertThat(shop.getType()).isEqualTo("Tea");
        assertThat(shop.getSeller()).isSameAs(seller);
        assertThat(shop.getId()).isNull();
    }

    @Test
    void newShopHasNoProductsOrCustomers() {
        Shop shop = new Shop();

        assertThat(shop.getProducts()).isNotNull().isEmpty();
        assertThat(shop.getCustomers()).isNotNull().isEmpty();
    }

    @Test
    void addAndRemoveProduct() {
        Shop shop = new Shop("S", "D", "T", null);
        Item lamp = new Item("Lamp", "Brass", 10.0, 2, "Lighting");
        Service repair = new Service("Repair", "Fix it", 20.0, 30);

        shop.addProduct(lamp);
        shop.addProduct(repair);
        assertThat(shop.getProducts()).containsExactly(lamp, repair);

        shop.removeProduct(lamp);
        assertThat(shop.getProducts()).containsExactly(repair);
    }

    @Test
    void removingAProductTheShopDoesNotHaveChangesNothing() {
        Shop shop = new Shop("S", "D", "T", null);
        Item lamp = new Item("Lamp", "Brass", 10.0, 2, "Lighting");
        shop.addProduct(lamp);

        shop.removeProduct(new Item("Other", "x", 1.0, 1, "x"));

        assertThat(shop.getProducts()).containsExactly(lamp);
    }

    @Test
    void addAndRemoveCustomer() {
        Shop shop = new Shop("S", "D", "T", null);
        Customer bo = new Customer("Bo", "bo@example.com", "pw", "555", "1 Main St");

        shop.addCustomer(bo);
        assertThat(shop.getCustomers()).containsExactly(bo);

        shop.removeCustomer(bo);
        assertThat(shop.getCustomers()).isEmpty();
    }

    @Test
    void settersUpdateFields() {
        Shop shop = new Shop();
        Seller seller = new Seller();

        shop.setName("New name");
        shop.setDescription("New description");
        shop.setType("New type");
        shop.setSeller(seller);

        assertThat(shop.getName()).isEqualTo("New name");
        assertThat(shop.getDescription()).isEqualTo("New description");
        assertThat(shop.getType()).isEqualTo("New type");
        assertThat(shop.getSeller()).isSameAs(seller);
    }
}
