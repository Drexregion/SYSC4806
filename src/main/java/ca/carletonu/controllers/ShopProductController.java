package ca.carletonu.controllers;

import ca.carletonu.cqrs.commands.AddProductCommand;
import ca.carletonu.cqrs.handlers.AddProductCommandHandler;
import ca.carletonu.cqrs.handlers.ShopNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Lets a merchant add products, items or services, to one of their shops.
 *
 * Only translates the form into an AddProductCommand. The rules and the save
 * live in AddProductCommandHandler.
 */
@Controller
public class ShopProductController {

    private final AddProductCommandHandler addProductCommandHandler;

    public ShopProductController(AddProductCommandHandler addProductCommandHandler) {
        this.addProductCommandHandler = addProductCommandHandler;
    }

    /**
     * Saves the submitted product against the shop in the URL.
     */
    @PostMapping("/shops/{shopId}/products")
    public String addProduct(@PathVariable Long shopId,
                             @ModelAttribute ProductForm form,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes) {
        try {
            // A field that is not a number never reaches the command, because
            // the form could not be bound. That is a web concern, so it stays here.
            if (bindingResult.hasErrors()) {
                throw new IllegalArgumentException("Price, inventory and duration must be numbers.");
            }

            addProductCommandHandler.handle(new AddProductCommand(
                    shopId,
                    form.getKind(),
                    form.getName(),
                    form.getDescription(),
                    form.getPrice(),
                    form.getQuantity(),
                    form.getProductType(),
                    form.getDuration()
            ));
        } catch (ShopNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            // Flash attributes survive one redirect, so the shop page can show
            // the error and refill the form without this controller rendering it
            redirectAttributes.addFlashAttribute("productError", e.getMessage());
            redirectAttributes.addFlashAttribute("productForm", form);
        }

        // Redirect after post, so refreshing the page does not add the product again
        return "redirect:/shops/" + shopId;
    }
}
