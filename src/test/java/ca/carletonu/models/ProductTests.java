package ca.carletonu.models;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Product and its two subtypes: Item (physical, has inventory) and Service (has a duration).
 */
class ProductTests {

    @Test
    void productConstructorSetsFields() {
        Product product = new Product("Mug", "Ceramic", 8.5);

        assertThat(product.getName()).isEqualTo("Mug");
        assertThat(product.getDescription()).isEqualTo("Ceramic");
        assertThat(product.getPrice()).isEqualTo(8.5);
        assertThat(product.getId()).isNull();
    }

    @Test
    void itemIsAProductWithInventory() {
        Item item = new Item("Lamp", "Brass", 89.0, 4, "Lighting");

        assertThat(item).isInstanceOf(Product.class);
        assertThat(item.getName()).isEqualTo("Lamp");
        assertThat(item.getPrice()).isEqualTo(89.0);
        assertThat(item.getQuantity()).isEqualTo(4);
        assertThat(item.getProductType()).isEqualTo("Lighting");
    }

    @Test
    void serviceIsAProductWithDuration() {
        Service service = new Service("Repair", "Rewiring", 30.0, 45);

        assertThat(service).isInstanceOf(Product.class);
        assertThat(service.getDuration()).isEqualTo(45);
    }

    @Test
    void settersUpdateFields() {
        Item item = new Item();
        item.setName("N");
        item.setDescription("D");
        item.setPrice(1.25);
        item.setQuantity(3);
        item.setProductType("T");

        Service service = new Service();
        service.setDuration(60);

        assertThat(item.getName()).isEqualTo("N");
        assertThat(item.getDescription()).isEqualTo("D");
        assertThat(item.getPrice()).isEqualTo(1.25);
        assertThat(item.getQuantity()).isEqualTo(3);
        assertThat(item.getProductType()).isEqualTo("T");
        assertThat(service.getDuration()).isEqualTo(60);
    }
}
