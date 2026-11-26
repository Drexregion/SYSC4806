package ca.carletonu.controllers;

import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.ShopRepository;
import ca.carletonu.readmodel.ShopSummary;
import ca.carletonu.readmodel.ShopSummaryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Browsing shops.
 *
 * The list reads the query side. One shop reads the write side, because the
 * page shows each product with its price and stock and the summary only keeps
 * counts. That split is the point of CQRS: browse from the shape built for
 * browsing, and go to the real record when you need the detail.
 */
@Controller
public class ShopController {

    private final ShopRepository shopRepository;
    private final ShopSummaryRepository shopSummaryRepository;

    public ShopController(ShopRepository shopRepository, ShopSummaryRepository shopSummaryRepository) {
        this.shopRepository = shopRepository;
        this.shopSummaryRepository = shopSummaryRepository;
    }

    /**
     * Every shop in the catalogue, by name.
     *
     * findByNameIsNotNull leaves out placeholder summaries, the ones created
     * when a product event arrives before its shop's. A half known shop is not
     * something to show a shopper.
     */
    @GetMapping("/shops")
    public String allShops(Model model) {
        List<ShopSummary> shops = shopSummaryRepository.findByNameIsNotNullOrderByNameAsc();
        model.addAttribute("shops", shops);
        return "shops/list";
    }

    @GetMapping("/shops/{id}")
    public String shop(@PathVariable Long id, Model model) {
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found"));
        model.addAttribute("shop", shop);
        model.addAttribute("products", shop.getProducts());
        return "shops/shop";
    }
}
