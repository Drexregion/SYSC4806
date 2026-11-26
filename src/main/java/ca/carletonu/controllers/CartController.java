package ca.carletonu.controllers;

import ca.carletonu.cqrs.commands.AddToCartCommand;
import ca.carletonu.cqrs.commands.ChangeCartQuantityCommand;
import ca.carletonu.cqrs.commands.CheckoutCommand;
import ca.carletonu.cqrs.commands.RemoveFromCartCommand;
import ca.carletonu.cqrs.handlers.AddToCartCommandHandler;
import ca.carletonu.cqrs.handlers.ChangeCartQuantityCommandHandler;
import ca.carletonu.cqrs.handlers.CheckoutCommandHandler;
import ca.carletonu.cqrs.handlers.CheckoutResult;
import ca.carletonu.cqrs.handlers.ProductNotFoundException;
import ca.carletonu.cqrs.handlers.RemoveFromCartCommandHandler;
import ca.carletonu.models.Cart;
import ca.carletonu.models.CartItem;
import ca.carletonu.models.Customer;
import ca.carletonu.models.repositories.CartRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Lets the shopper put products in their cart, see it, change it, and check
 * it out.
 *
 * The controller does no business logic of its own: it turns the submitted
 * form into a command, hands it to the handler, and decides where to send the
 * shopper next. Every rule, including having a customer at all, is the
 * handler's; a refusal comes back as an IllegalArgumentException whose
 * message is shown on the page.
 */
@Controller
public class CartController {

    /**
     * The pages that offer Add to cart. Only these are accepted as a place to
     * send the shopper back to, so the form cannot be used to redirect
     * somewhere else entirely.
     */
    private static final Pattern RETURN_PATH = Pattern.compile("/(shops|products)/\\d+");

    private final CurrentCustomer currentCustomer;
    private final CartRepository cartRepository;
    private final AddToCartCommandHandler addToCartCommandHandler;
    private final ChangeCartQuantityCommandHandler changeCartQuantityCommandHandler;
    private final RemoveFromCartCommandHandler removeFromCartCommandHandler;
    private final CheckoutCommandHandler checkoutCommandHandler;

    public CartController(CurrentCustomer currentCustomer,
                          CartRepository cartRepository,
                          AddToCartCommandHandler addToCartCommandHandler,
                          ChangeCartQuantityCommandHandler changeCartQuantityCommandHandler,
                          RemoveFromCartCommandHandler removeFromCartCommandHandler,
                          CheckoutCommandHandler checkoutCommandHandler) {
        this.currentCustomer = currentCustomer;
        this.cartRepository = cartRepository;
        this.addToCartCommandHandler = addToCartCommandHandler;
        this.changeCartQuantityCommandHandler = changeCartQuantityCommandHandler;
        this.removeFromCartCommandHandler = removeFromCartCommandHandler;
        this.checkoutCommandHandler = checkoutCommandHandler;
    }

    @GetMapping("/cart")
    public String cart(Model model) {
        Optional<Cart> cart = currentCustomer.find().flatMap(cartRepository::findOpenCart);
        List<CartItem> lines = cart.map(Cart::getItems).orElse(List.of()).stream()
                .sorted(Comparator.comparing(CartItem::getId))
                .toList();
        model.addAttribute("lines", lines);
        model.addAttribute("total", cart.map(Cart::getTotal).orElse(0.0));
        return "cart";
    }

    @PostMapping("/cart/items/{productId}/quantity")
    public String changeQuantity(@PathVariable Long productId,
                                 @RequestParam(required = false) Integer quantity,
                                 RedirectAttributes redirectAttributes) {
        return changeCart(redirectAttributes, "Cart updated.", () ->
                changeCartQuantityCommandHandler.handle(
                        new ChangeCartQuantityCommand(currentCustomerId(), productId, quantity)));
    }

    @PostMapping("/cart/items/{productId}/remove")
    public String remove(@PathVariable Long productId, RedirectAttributes redirectAttributes) {
        return changeCart(redirectAttributes, "Removed from your cart.", () ->
                removeFromCartCommandHandler.handle(new RemoveFromCartCommand(currentCustomerId(), productId)));
    }

    /**
     * Dispatches a CheckoutCommand for the shopper's open cart. A successful
     * checkout goes to its confirmation page; a refused one goes back to the
     * cart page with the reason.
     */
    @PostMapping("/cart/checkout")
    public String checkout(RedirectAttributes redirectAttributes) {
        try {
            CheckoutResult result = checkoutCommandHandler.handle(new CheckoutCommand(currentCustomerId()));
            // A page of its own rather than a flash message, so the order can
            // be seen again after a refresh.
            return "redirect:/checkout/confirmation/" + result.cartId();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("cartError", e.getMessage());
            return "redirect:/cart";
        }
    }

    /**
     * Runs a change from the cart page and lands back on it, with the message
     * on success or the handler's reason on refusal.
     */
    private String changeCart(RedirectAttributes redirectAttributes, String message, Runnable change) {
        try {
            change.run();
            redirectAttributes.addFlashAttribute("cartMessage", message);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("cartError", e.getMessage());
        }
        return "redirect:/cart";
    }

    /**
     * Takes the submitted form, dispatches an AddToCartCommand, and sends the
     * shopper back to the page they added from with a message saying what
     * happened.
     *
     * Redirect after post, so refreshing the page does not add it again. A
     * command the handler refuses comes back as a message on the page rather
     * than an error page.
     */
    @PostMapping("/cart/items")
    public String addToCart(@RequestParam Long productId,
                            @RequestParam(defaultValue = "1") Integer quantity,
                            @RequestParam(required = false) String returnTo,
                            RedirectAttributes redirectAttributes) {
        try {
            CartItem line = addToCartCommandHandler.handle(
                    new AddToCartCommand(currentCustomerId(), productId, quantity));
            redirectAttributes.addFlashAttribute("cartMessage", addedMessage(line, quantity));
        } catch (ProductNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("cartError", e.getMessage());
        }

        return "redirect:" + returnPath(returnTo, productId);
    }

    /**
     * The shopper's id, or null when there is no customer to stand in. Null
     * goes to the handler like any other value, and the handler refuses it.
     */
    private Long currentCustomerId() {
        return currentCustomer.find().map(Customer::getId).orElse(null);
    }

    /**
     * Where to send the shopper back to: the page they came from when it is
     * one of ours, otherwise the product's own page.
     */
    static String returnPath(String returnTo, Long productId) {
        if (returnTo != null && RETURN_PATH.matcher(returnTo).matches()) {
            return returnTo;
        }
        return "/products/" + productId;
    }

    private static String addedMessage(CartItem line, int quantity) {
        int total = line.getCart().getItemCount();
        return "Added " + quantity + " × " + line.getProduct().getName() + " to your cart. "
                + "Cart has " + total + (total == 1 ? " item." : " items.");
    }
}
