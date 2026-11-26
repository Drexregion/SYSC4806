package ca.carletonu.cqrs.commands;

public record ChangeCartQuantityCommand(
        Long customerId,
        Long productId,
        Integer quantity
) {
}
