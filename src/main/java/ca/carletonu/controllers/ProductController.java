package ca.carletonu.controllers;

import ca.carletonu.models.Product;
import ca.carletonu.models.repositories.ProductRepository;
import ca.carletonu.models.repositories.ShopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shows the details page for a single product, along with the shop that sells it.
 */
@Controller
public class ProductController {

    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;

    public ProductController(ProductRepository productRepository, ShopRepository shopRepository) {
        this.productRepository = productRepository;
        this.shopRepository = shopRepository;
    }

    @GetMapping("/products/{id}")
    public String product(@PathVariable Long id, Model model) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        model.addAttribute("product", product);

        model.addAttribute("shop", shopRepository.findFirstByProducts_Id(id).orElse(null));
        return "products/product";
    }
}
