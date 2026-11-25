package ca.carletonu.controllers;

import ca.carletonu.cqrs.commands.AddToCartCommand;
import ca.carletonu.cqrs.commands.CheckoutCommand;
import ca.carletonu.cqrs.handlers.AddToCartCommandHandler;
import ca.carletonu.cqrs.handlers.CheckoutCommandHandler;
import ca.carletonu.cqrs.handlers.CheckoutResult;
import ca.carletonu.cqrs.handlers.ProductNotFoundException;
import ca.carletonu.models.CartItem;
import ca.carletonu.models.Customer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Lets the shopper put products in their cart and check it out.
 *
 * The controller does no business logic of its own: it turns the submitted
 * form into a command, hands it to its handler, and decides where to send the
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
    private final AddToCartCommandHandler addToCartCommandHandler;
    private final CheckoutCommandHandler checkoutCommandHandler;

    public CartController(CurrentCustomer currentCustomer,
                          AddToCartCommandHandler addToCartCommandHandler,
                          CheckoutCommandHandler checkoutCommandHandler) {
        this.currentCustomer = currentCustomer;
        this.addToCartCommandHandler = addToCartCommandHandler;
        this.checkoutCommandHandler = checkoutCommandHandler;
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

        return "redirect:" + returnPath(returnTo, "/products/" + productId);
    }

    /**
     * Dispatches a CheckoutCommand for the shopper's open cart and sends them
     * back with a message saying what was bought, or why it was refused.
     *
     * Without a page of its own yet, it returns to the page the shopper came
     * from when that is one of ours, otherwise the landing page.
     */
    @PostMapping("/cart/checkout")
    public String checkout(@RequestParam(required = false) String returnTo,
                           RedirectAttributes redirectAttributes) {
        try {
            CheckoutResult result = checkoutCommandHandler.handle(new CheckoutCommand(currentCustomerId()));
            redirectAttributes.addFlashAttribute("cartMessage", checkedOutMessage(result));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("cartError", e.getMessage());
        }

        return "redirect:" + returnPath(returnTo, "/");
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
     * one of ours, otherwise the fallback.
     */
    static String returnPath(String returnTo, String fallback) {
        if (returnTo != null && RETURN_PATH.matcher(returnTo).matches()) {
            return returnTo;
        }
        return fallback;
    }

    private static String addedMessage(CartItem line, int quantity) {
        int total = line.getCart().getItemCount();
        return "Added " + quantity + " × " + line.getProduct().getName() + " to your cart. "
                + "It now holds " + total + (total == 1 ? " item." : " items.");
    }

    private static String checkedOutMessage(CheckoutResult result) {
        return "Checked out " + result.itemCount() + (result.itemCount() == 1 ? " item" : " items")
                + " for $" + String.format(Locale.ROOT, "%.2f", result.total()) + ".";
    }
}
