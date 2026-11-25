package ca.carletonu.cqrs.commands;

public record CreateShopCommand(
        Long sellerId,
        String name,
        String description,
        String type
) {
}
