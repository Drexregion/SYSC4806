package ca.carletonu.controllers;

import ca.carletonu.readmodel.ShopSummary;
import ca.carletonu.readmodel.ShopSummaryRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * The query side of the catalogue.
 *
 * This controller has no access to ShopRepository at all. Everything it
 * renders comes from the read model, which exists only because events were
 * replayed into it. That separation is the whole point: compare it with
 * ShopController, which reads the write model directly.
 */
@Controller
public class CatalogueController {

    private final ShopSummaryRepository shopSummaryRepository;

    public CatalogueController(ShopSummaryRepository shopSummaryRepository) {
        this.shopSummaryRepository = shopSummaryRepository;
    }

    @GetMapping("/catalogue")
    public String catalogue(@RequestParam(name = "q", required = false) String query, Model model) {
        String term = query == null ? "" : query.trim();

        List<ShopSummary> shops = term.isEmpty()
                ? shopSummaryRepository.findAllByOrderByNameAsc()
                : shopSummaryRepository
                    .findByNameContainingIgnoreCaseOrTypeContainingIgnoreCaseOrderByNameAsc(term, term);

        model.addAttribute("query", term);
        model.addAttribute("shops", shops);
        model.addAttribute("projectionEmpty", shopSummaryRepository.count() == 0);

        return "catalogue";
    }
}
