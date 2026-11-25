package ca.carletonu.cqrs.commands;

public record RemoveFromCartCommand(
        Long customerId,
        Long productId
) {
}
