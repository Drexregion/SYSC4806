package ca.carletonu.controllers;

import ca.carletonu.readmodel.ShopSummary;
import ca.carletonu.readmodel.ShopSummaryRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Searching shops by name or category.
 *
 * Reads the ShopSummary catalogue rather than the shop table. The summary is
 * shaped for exactly this: name and category on one flat row, no joins, and
 * it can be rebuilt from the event log whenever its shape needs to change.
 */
@Controller
public class ShopSearchController {

    private final ShopSummaryRepository shopSummaryRepository;

    public ShopSearchController(ShopSummaryRepository shopSummaryRepository) {
        this.shopSummaryRepository = shopSummaryRepository;
    }

    @GetMapping("/shops/search")
    public String search(@RequestParam(name = "q", required = false) String query, Model model) {
        String term = query == null ? "" : query.trim();
        model.addAttribute("query", term);
        model.addAttribute("searched", !term.isEmpty());
        model.addAttribute("shops", term.isEmpty()
                ? List.<ShopSummary>of()
                : shopSummaryRepository
                    .findByNameContainingIgnoreCaseOrTypeContainingIgnoreCaseOrderByNameAsc(term, term));
        return "shops/search";
    }
}
