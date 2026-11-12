package ca.carletonu.models;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SellerTests {

    @Test
    void constructorSetsPersonFields() {
        Seller seller = new Seller("Ada", "ada@example.com", "pw", "555-0100");

        assertThat(seller.getName()).isEqualTo("Ada");
        assertThat(seller.getEmail()).isEqualTo("ada@example.com");
        assertThat(seller.getPassword()).isEqualTo("pw");
        assertThat(seller.getPhone()).isEqualTo("555-0100");
        assertThat(seller.getShops()).isEmpty();
    }

    @Test
    void addShopLinksBothSides() {
        Seller seller = new Seller("Ada", "ada@example.com", "pw", "555");
        Shop shop = new Shop("S", "D", "T", null);

        seller.addShop(shop);

        assertThat(seller.getShops()).containsExactly(shop);
        assertThat(shop.getSeller()).isSameAs(seller);
    }

    @Test
    void addShopReassignsTheShopsSeller() {
        Seller first = new Seller("First", "a@example.com", "pw", "1");
        Seller second = new Seller("Second", "b@example.com", "pw", "2");
        Shop shop = new Shop("S", "D", "T", first);

        second.addShop(shop);

        assertThat(shop.getSeller()).isSameAs(second);
    }

    @Test
    void removeShopUnlinksBothSides() {
        Seller seller = new Seller("Ada", "ada@example.com", "pw", "555");
        Shop shop = new Shop("S", "D", "T", null);
        seller.addShop(shop);

        seller.removeShop(shop);

        assertThat(seller.getShops()).isEmpty();
        assertThat(shop.getSeller()).isNull();
    }

    @Test
    void personSettersUpdateFields() {
        Seller seller = new Seller();
        seller.setName("N");
        seller.setEmail("e@example.com");
        seller.setPassword("p");
        seller.setPhone("123");
        seller.setId(7L);

        assertThat(seller.getName()).isEqualTo("N");
        assertThat(seller.getEmail()).isEqualTo("e@example.com");
        assertThat(seller.getPassword()).isEqualTo("p");
        assertThat(seller.getPhone()).isEqualTo("123");
        assertThat(seller.getId()).isEqualTo(7L);
    }
}
