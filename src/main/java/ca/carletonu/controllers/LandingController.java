package ca.carletonu.controllers;

import ca.carletonu.models.Seller;
import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.SellerRepository;
import ca.carletonu.models.repositories.ShopRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Serves the landing page: an overview of the platform plus a handful of
 * featured shops pulled from the database.
 */
@Controller
public class LandingController {

    static final int FEATURED_SHOP_COUNT = 6;

    private final ShopRepository shopRepository;
    private final SellerRepository sellerRepository;

    public LandingController(ShopRepository shopRepository, SellerRepository sellerRepository) {
        this.shopRepository = shopRepository;
        this.sellerRepository = sellerRepository;
    }

    @GetMapping("/")
    public String landing(Model model) {
        List<Shop> featuredShops = StreamSupport
                .stream(shopRepository.findAll().spliterator(), false)
                .limit(FEATURED_SHOP_COUNT)
                .toList();

        model.addAttribute("featuredShops", featuredShops);
        model.addAttribute("shopCount", shopRepository.count());
        model.addAttribute("sellerId", currentSellerId());

        return "index";
    }

    /**
     * Which seller the "Create a shop" link acts as.
     *
     * There is no sign-in yet, so the page stands in the first seller in the
     * database. The seed data always writes one, and when the table is empty
     * this returns null and the template leaves the link out rather than
     * pointing it at a seller that does not exist.
     *
     * This goes away in Milestone 2, when the signed-in seller is the answer.
     */
    private Long currentSellerId() {
        return StreamSupport
                .stream(sellerRepository.findAll().spliterator(), false)
                .map(Seller::getId)
                .findFirst()
                .orElse(null);
    }
}
