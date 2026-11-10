package ca.carletonu.controllers;

import ca.carletonu.models.repositories.ShopRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Controller for searching shops by name or category.
 */
@Controller
public class ShopSearchController {

    private final ShopRepository shopRepository;

    public ShopSearchController(ShopRepository shopRepository) {
        this.shopRepository = shopRepository;
    }

    /**
     * Search for shops by name or category.
     */
    @GetMapping("/shops/search")
    public String search(@RequestParam(name = "q", required = false) String query, Model model) {
        String term = query == null ? "" : query.trim();
        model.addAttribute("query", term);
        model.addAttribute("searched", !term.isEmpty());
        model.addAttribute("shops", term.isEmpty()
                ? List.of()
                : shopRepository.findByNameContainingIgnoreCaseOrTypeContainingIgnoreCase(term, term));
        return "shops/search";
    }
}
