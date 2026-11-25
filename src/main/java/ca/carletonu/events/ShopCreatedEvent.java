package ca.carletonu.events;

public record ShopCreatedEvent(
        Long shopId,
        Long sellerId,
        String name,
        String description,
        String type
) {
}
