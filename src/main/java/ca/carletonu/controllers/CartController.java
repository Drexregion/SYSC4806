package ca.carletonu.controllers;

import ca.carletonu.cqrs.commands.AddToCartCommand;
import ca.carletonu.cqrs.handlers.AddToCartCommandHandler;
import ca.carletonu.cqrs.handlers.ProductNotFoundException;
import ca.carletonu.models.CartItem;
import ca.carletonu.models.Customer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Lets the shopper put products in their cart.
 *
 * The controller does no business logic of its own: it turns the submitted
 * form into an AddToCartCommand, hands it to the handler, and decides where to
 * send the shopper next.
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

    public CartController(CurrentCustomer currentCustomer, AddToCartCommandHandler addToCartCommandHandler) {
        this.currentCustomer = currentCustomer;
        this.addToCartCommandHandler = addToCartCommandHandler;
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
        String back = "redirect:" + returnPath(returnTo, productId);

        Optional<Customer> customer = currentCustomer.find();
        if (customer.isEmpty()) {
            redirectAttributes.addFlashAttribute("cartError", "There is no customer account to add to yet.");
            return back;
        }

        try {
            CartItem line = addToCartCommandHandler.handle(
                    new AddToCartCommand(customer.get().getId(), productId, quantity));
            redirectAttributes.addFlashAttribute("cartMessage", addedMessage(line, quantity));
        } catch (ProductNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("cartError", e.getMessage());
        }

        return back;
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
                + "It now holds " + total + (total == 1 ? " item." : " items.");
    }
}
