package ca.carletonu.controllers;

import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.ShopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

@Controller
public class ShopController {

    private final ShopRepository shopRepository;

    public ShopController(ShopRepository shopRepository) {
        this.shopRepository = shopRepository;
    }

    @GetMapping("/shops")
    public String allShops(Model model) {
        List<Shop> shops = StreamSupport.stream(shopRepository.findAll().spliterator(), false)
                .sorted(Comparator.comparing(Shop::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
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
