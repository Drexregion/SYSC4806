package ca.carletonu.messaging;

import ca.carletonu.events.ProductAddedEvent;
import ca.carletonu.events.CartItemAddedEvent;
import ca.carletonu.events.CheckoutCompletedEvent;
import ca.carletonu.events.ShopCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads shop-created events off the log.
 *
 * For now, it only logs what it receives. In Milestone 2 this is where the
 * read models for search and browsing get updated, which is the point of
 * splitting the write side from the read side in the first place.
 *
 * Only exists under the "kafka" profile, so no listener container starts
 * when there is no broker to listen to.
 */
@Component
@Profile("kafka")
public class KafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumer.class);

    @KafkaListener(topics = "shop-created", groupId = "mini-shopify-group")
    public void consumeShopCreated(ShopCreatedEvent shopCreatedEvent) {
        log.info("ShopCreatedEvent received: shopId={} name={} sellerId={}",
                shopCreatedEvent.shopId(),
                shopCreatedEvent.name(),
                shopCreatedEvent.sellerId());
    }

    @KafkaListener(topics = "product-added", groupId = "mini-shopify-group")
    public void consumeProductAdded(ProductAddedEvent productAddedEvent) {
        log.info("ProductAddedEvent received: shopId={} productId={} kind={} name={}",
                productAddedEvent.shopId(),
                productAddedEvent.productId(),
                productAddedEvent.kind(),
                productAddedEvent.name());
    }

    /**
     * Nothing projects cart events yet, since no page reads a cart from a read
     * model. Logging them is what shows the flow working end to end.
     */
    @KafkaListener(topics = "cart-item-added", groupId = "mini-shopify-group")
    public void consumeCartItemAdded(CartItemAddedEvent cartItemAddedEvent) {
        log.info("CartItemAddedEvent received: cartId={} customerId={} productId={} added={} lineNow={}",
                cartItemAddedEvent.cartId(),
                cartItemAddedEvent.customerId(),
                cartItemAddedEvent.productId(),
                cartItemAddedEvent.quantityAdded(),
                cartItemAddedEvent.lineQuantity());
    }

    /**
     * Nothing projects orders yet. Logging them is what shows the flow
     * working end to end.
     */
    @KafkaListener(topics = "checkout-completed", groupId = "mini-shopify-group")
    public void consumeCheckoutCompleted(CheckoutCompletedEvent checkoutCompletedEvent) {
        log.info("CheckoutCompletedEvent received: cartId={} customerId={} lines={} total={}",
                checkoutCompletedEvent.cartId(),
                checkoutCompletedEvent.customerId(),
                checkoutCompletedEvent.lines().size(),
                checkoutCompletedEvent.total());
    }
}
