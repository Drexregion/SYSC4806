package ca.carletonu.models;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerTests {

    @Test
    void constructorSetsFields() {
        Customer bo = new Customer("Bo", "bo@example.com", "pw", "555", "1 Main St");

        assertThat(bo.getName()).isEqualTo("Bo");
        assertThat(bo.getAddress()).isEqualTo("1 Main St");
        assertThat(bo.getFavoriteShops()).isEmpty();
        assertThat(bo.getTransactionHistory()).isEmpty();
    }

    @Test
    void addAndRemoveFavoriteShop() {
        Customer bo = new Customer("Bo", "bo@example.com", "pw", "555", "1 Main St");
        Shop shop = new Shop("S", "D", "T", null);

        bo.addShop(shop);
        assertThat(bo.getFavoriteShops()).containsExactly(shop);

        bo.removeShop(shop);
        assertThat(bo.getFavoriteShops()).isEmpty();
    }

    @Test
    void setAddress() {
        Customer bo = new Customer();
        bo.setAddress("2 Side St");

        assertThat(bo.getAddress()).isEqualTo("2 Side St");
    }
}
