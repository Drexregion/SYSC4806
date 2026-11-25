package ca.carletonu.cqrs.handlers;

import ca.carletonu.cqrs.commands.AddProductCommand;
import ca.carletonu.events.ProductAddedEvent;
import ca.carletonu.events.ProductKind;
import ca.carletonu.messaging.KafkaProducer;
import ca.carletonu.models.Item;
import ca.carletonu.models.Product;
import ca.carletonu.models.Service;
import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.ProductRepository;
import ca.carletonu.models.repositories.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the write side of adding a product: the rules, the save, and
 * the event, for both items and services. No Spring context and no broker;
 * the repositories and the producer are mocks.
 */
class AddProductCommandHandlerTests {

    private static final long SHOP_ID = 7L;
    private static final long PRODUCT_ID = 42L;

    private ShopRepository shopRepository;
    private ProductRepository productRepository;
    private KafkaProducer producer;
    private Shop shop;

    @BeforeEach
    void setUp() {
        shopRepository = mock(ShopRepository.class);
        productRepository = mock(ProductRepository.class);
        producer = mock(KafkaProducer.class);

        shop = new Shop("Corner Store", "Snacks", "Grocery", null);
        ReflectionTestUtils.setField(shop, "id", SHOP_ID);
        when(shopRepository.findById(SHOP_ID)).thenReturn(Optional.of(shop));

        // Stand in for the database assigning an id on save
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
            return product;
        });
    }

    // --- Items ---------------------------------------------------------

    @Test
    void anItemIsSavedAgainstTheShopAndPublishedAsAnItem() {
        Product saved = handler(true).handle(
                AddProductCommand.item(SHOP_ID, "  Granola Bar ", "Oats and honey", 2.50, 40, "Snack"));

        assertThat(saved).isInstanceOf(Item.class);
        assertThat(saved.getId()).isEqualTo(PRODUCT_ID);
        assertThat(saved.getName()).isEqualTo("Granola Bar");
        assertThat(shop.getProducts()).containsExactly(saved);

        assertThat(publishedEvent()).isEqualTo(new ProductAddedEvent(
                SHOP_ID, PRODUCT_ID, ProductKind.ITEM, "Granola Bar", "Oats and honey", 2.50,
                40, "Snack", null));
    }

    @Test
    void anItemWithoutInventoryIsRejected() {
        assertRejected(AddProductCommand.item(SHOP_ID, "Widget", null, 1.0, null, null),
                "Inventory is required for an item.");
    }

    @Test
    void negativeInventoryIsRejected() {
        assertRejected(AddProductCommand.item(SHOP_ID, "Widget", null, 1.0, -1, null),
                "Inventory cannot be negative.");
    }

    @Test
    void aFreeItemWithNoStockIsAllowed() {
        Item saved = (Item) handler(true).handle(AddProductCommand.item(SHOP_ID, "Sample", null, 0.0, 0, null));

        assertThat(saved.getPrice()).isZero();
        assertThat(saved.getQuantity()).isZero();
    }

    // --- Services ------------------------------------------------------

    @Test
    void aServiceIsSavedAgainstTheShopAndPublishedAsAService() {
        Product saved = handler(true).handle(
                AddProductCommand.service(SHOP_ID, "Guided Tasting", "Six teas", 35.0, 60));

        assertThat(saved).isInstanceOf(Service.class);
        assertThat(((Service) saved).getDuration()).isEqualTo(60);
        assertThat(shop.getProducts()).containsExactly(saved);

        assertThat(publishedEvent()).isEqualTo(new ProductAddedEvent(
                SHOP_ID, PRODUCT_ID, ProductKind.SERVICE, "Guided Tasting", "Six teas", 35.0,
                null, null, 60));
    }

    @Test
    void aServiceIgnoresItemFieldsSentWithIt() {
        Product saved = handler(true).handle(new AddProductCommand(
                SHOP_ID, ProductKind.SERVICE, "Repair", null, 45.0, -5, "Lamp", 45));

        assertThat(saved).isInstanceOf(Service.class);
        assertThat(publishedEvent().quantity()).isNull();
        assertThat(publishedEvent().productType()).isNull();
    }

    @Test
    void aServiceWithoutDurationIsRejected() {
        assertRejected(AddProductCommand.service(SHOP_ID, "Repair", null, 45.0, null),
                "Duration is required for a service.");
    }

    @Test
    void aServiceWithZeroDurationIsRejected() {
        assertRejected(AddProductCommand.service(SHOP_ID, "Repair", null, 45.0, 0),
                "Duration must be at least one minute.");
    }

    // --- Both ----------------------------------------------------------

    @Test
    void withoutABrokerTheProductIsStillSaved() {
        Product saved = handler(false).handle(AddProductCommand.item(SHOP_ID, "Granola Bar", null, 2.50, 40, null));

        assertThat(shop.getProducts()).containsExactly(saved);
        verify(producer, never()).sendProductAddedEvent(any());
    }

    @Test
    void anUnknownShopIsNotFound() {
        when(shopRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler(true).handle(
                AddProductCommand.item(99L, "Widget", null, 1.0, 1, null)))
                .isInstanceOf(ShopNotFoundException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void aMissingKindIsRejected() {
        assertRejected(new AddProductCommand(SHOP_ID, null, "Widget", null, 1.0, 1, null, null),
                "Choose whether this is an item or a service.");
    }

    @Test
    void aBlankNameIsRejected() {
        assertRejected(AddProductCommand.service(SHOP_ID, "   ", null, 1.0, 30), "Name is required.");
    }

    @Test
    void aNameOverOneHundredTwentyCharactersIsRejected() {
        assertRejected(AddProductCommand.item(SHOP_ID, "x".repeat(121), null, 1.0, 1, null),
                "Name has to be 120 characters or fewer.");
    }

    @Test
    void aMissingPriceIsRejected() {
        assertRejected(AddProductCommand.item(SHOP_ID, "Widget", null, null, 1, null),
                "Price must be zero or more.");
    }

    @Test
    void aNegativePriceIsRejected() {
        assertRejected(AddProductCommand.service(SHOP_ID, "Repair", null, -0.01, 30),
                "Price must be zero or more.");
    }

    private ProductAddedEvent publishedEvent() {
        ArgumentCaptor<ProductAddedEvent> event = ArgumentCaptor.forClass(ProductAddedEvent.class);
        verify(producer).sendProductAddedEvent(event.capture());
        return event.getValue();
    }

    private void assertRejected(AddProductCommand command, String message) {
        assertThatThrownBy(() -> handler(true).handle(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);

        verify(productRepository, never()).save(any());
        verify(producer, never()).sendProductAddedEvent(any());
        assertThat(shop.getProducts()).isEmpty();
    }

    /** A handler whose producer is either there (kafka profile) or not. */
    @SuppressWarnings("unchecked")
    private AddProductCommandHandler handler(boolean brokerAvailable) {
        ObjectProvider<KafkaProducer> provider = mock(ObjectProvider.class);
        doAnswer(invocation -> {
            if (brokerAvailable) {
                ((Consumer<KafkaProducer>) invocation.getArgument(0)).accept(producer);
            }
            return null;
        }).when(provider).ifAvailable(any());

        return new AddProductCommandHandler(shopRepository, productRepository, provider);
    }
}
