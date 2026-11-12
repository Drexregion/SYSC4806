package ca.carletonu.controllers;

import ca.carletonu.models.Seller;
import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.SellerRepository;
import ca.carletonu.models.repositories.ShopRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

/**
 * Controller for searching shops by name or category.
 */
@Controller
public class SellerController {
    private ShopRepository shopRepository;
    private SellerRepository sellerRepository;
    public SellerController(ShopRepository shopRepository,  SellerRepository sellerRepository) {
        this.shopRepository = shopRepository;
        this.sellerRepository = sellerRepository;
    }

    /**
     * Display the form for creating a shop.
     */
    @GetMapping("/{id}/shops/new")
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
     * Process the shop creation form.
     */
    @PostMapping("/{id}/shops")
    public String createShop(@PathVariable Long id,
                             @ModelAttribute Shop shop,
                             RedirectAttributes redirectAttributes) {

        Optional<Seller> seller = sellerRepository.findById(id);

        if (seller.isEmpty()) {
            return "redirect:/";
        }

        // Associate the shop with its seller.
        shop.setSeller(seller.get());

        // Save the shop to the database.
        shopRepository.save(shop);

        redirectAttributes.addFlashAttribute(
                "successMessage", "Your shop was created successfully!"
        );

        return "redirect:/";
    }
}

