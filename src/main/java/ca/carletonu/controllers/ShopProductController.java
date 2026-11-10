package ca.carletonu.controllers;

import ca.carletonu.models.Item;
import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.ShopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Lets a merchant add products to one of their shops.
 */
@Controller
public class ShopProductController {

    private final ShopRepository shopRepository;

    public ShopProductController(ShopRepository shopRepository) {
        this.shopRepository = shopRepository;
    }

    /**
     * Saves the submitted product against the shop in the URL.
     */
    @PostMapping("/shops/{shopId}/products")
    public String addProduct(@PathVariable Long shopId,
                             @ModelAttribute Item item,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No shop with id " + shopId));

        String error = validate(item, bindingResult);
        if (error != null) {
            // Flash attributes survive one redirect, so the shop page can show
            // the error and refill the form without this controller rendering it
            redirectAttributes.addFlashAttribute("productError", error);
            redirectAttributes.addFlashAttribute("productForm", item);
            return "redirect:/shops/" + shopId;
        }

        shop.addProduct(item);
        shopRepository.save(shop);

        // Redirect after post, so refreshing the page does not add the product again
        return "redirect:/shops/" + shopId;
    }

    private String validate(Item item, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) return "Price and inventory must be numbers.";
        if (item.getName() == null || item.getName().isBlank()) return "Name is required.";
        if (item.getPrice() == null || item.getPrice() < 0) return "Price must be zero or more.";
        if (item.getQuantity() < 0) return "Inventory cannot be negative.";
        return null;
    }
}
