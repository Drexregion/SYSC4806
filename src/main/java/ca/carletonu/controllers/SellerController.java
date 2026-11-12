package ca.carletonu.controllers;

import ca.carletonu.cqrs.commands.CreateShopCommand;
import ca.carletonu.cqrs.handlers.CreateShopCommandHandler;
import ca.carletonu.models.Seller;
import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.SellerRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

/**
 * Lets a seller create a shop.
 *
 * The controller does no business logic of its own: it turns the submitted
 * form into a command and hands it to the handler, then decides where to send
 * the merchant next.
 */
@Controller
public class SellerController {

    private final SellerRepository sellerRepository;
    private final CreateShopCommandHandler createShopCommandHandler;

    public SellerController(SellerRepository sellerRepository,
                            CreateShopCommandHandler createShopCommandHandler) {
        this.sellerRepository = sellerRepository;
        this.createShopCommandHandler = createShopCommandHandler;
    }

    /**
     * Shows the create-a-shop form.
     */
    @GetMapping("/sellers/{id}/shops/new")
    public String createShopForm(@PathVariable Long id, Model model) {

        Optional<Seller> seller = sellerRepository.findById(id);

        if (seller.isEmpty()) {
            return "redirect:/";
        }

        model.addAttribute("seller", seller.get());
        model.addAttribute("shop", new Shop());

        return "shops/create";
    }

    /**
     * Takes the submitted form, dispatches a CreateShopCommand, and redirects.
     *
     * Redirect after post, so refreshing the page does not create the shop a
     * second time. A command the handler rejects sends the merchant back to
     * the form with the reason, rather than showing an error page.
     */
    @PostMapping("/sellers/{id}/shops")
    public String createShop(@PathVariable Long id,
                             @ModelAttribute Shop shop,
                             RedirectAttributes redirectAttributes) {

        if (sellerRepository.findById(id).isEmpty()) {
            return "redirect:/";
        }

        CreateShopCommand command = new CreateShopCommand(
                id,
                shop.getName(),
                shop.getDescription(),
                shop.getType()
        );

        try {
            createShopCommandHandler.handle(command);
        } catch (IllegalArgumentException rejected) {
            redirectAttributes.addFlashAttribute("shopError", rejected.getMessage());
            redirectAttributes.addFlashAttribute("shopForm", shop);
            return "redirect:/sellers/" + id + "/shops/new";
        }

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Your shop was created successfully!"
        );

        return "redirect:/";
    }
}
