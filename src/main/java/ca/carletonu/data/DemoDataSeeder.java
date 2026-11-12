package ca.carletonu.data;

import ca.carletonu.models.Item;
import ca.carletonu.models.Seller;
import ca.carletonu.models.Service;
import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.SellerRepository;
import ca.carletonu.models.repositories.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Puts a set of shops and products into the database at startup.
 *
 * The database is in-memory H2 with ddl-auto=create-drop, so every restart and
 * every Azure deployment starts with empty tables. Without this the site is
 * nothing but empty states until someone creates records by hand, which makes
 * the browse, search and shop pages impossible to show.
 *
 * It only runs when the shop table is empty, so restarting never duplicates
 * rows and it never overwrites anything created during a session.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final ShopRepository shopRepository;
    private final SellerRepository sellerRepository;

    public DemoDataSeeder(ShopRepository shopRepository, SellerRepository sellerRepository) {
        this.shopRepository = shopRepository;
        this.sellerRepository = sellerRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (shopRepository.count() > 0) {
            log.info("Shops already in the database, skipping demo data");
            return;
        }
        seed();
        log.info("Seeded {} demo shops", shopRepository.count());
    }

    /**
     * Writes the demo shops. Package private so the tests can call it directly
     * after clearing the database.
     */
    void seed() {
        Seller ada = new Seller("Ada Okafor", "ada@minishopify.test", "demo", "613-555-0100");
        Seller marcus = new Seller("Marcus Lee", "marcus@minishopify.test", "demo", "613-555-0142");

        Shop tea = shop("Kettle Corner", "Loose leaf teas sourced from twelve countries", "Tea");
        tea.addProduct(new Item("Sencha Green", "Grassy and lightly sweet, first flush", 14.50, 24, "Tea"));
        tea.addProduct(new Item("Earl Grey Supreme", "Bergamot with blue cornflower", 12.00, 0, "Tea"));
        tea.addProduct(new Service("Guided Tasting", "Six teas walked through with the owner", 35.00, 60));
        ada.addShop(tea);

        Shop lamps = shop("Lantern Works", "Hand folded paper lamps and brass fittings", "Lighting");
        lamps.addProduct(new Item("Paper Lantern", "Folded rice paper on a bamboo frame", 28.00, 11, "Lamp"));
        lamps.addProduct(new Item("Brass Desk Lamp", "Polished brass with a linen shade", 134.00, 3, "Lamp"));
        lamps.addProduct(new Service("Lamp Rewiring", "Old fittings made safe and usable again", 45.00, 45));
        ada.addShop(lamps);

        Shop coffee = shop("Bean There", "Single origin espresso and morning pastries", "Cafe");
        coffee.addProduct(new Item("Yirgacheffe 250g", "Floral Ethiopian, roasted for filter", 19.00, 30, "Coffee"));
        coffee.addProduct(new Item("Almond Croissant", "Baked each morning, sells out by noon", 4.75, 0, "Pastry"));
        coffee.addProduct(new Service("Barista Workshop", "Two hours on grind, dose and milk", 80.00, 120));
        ada.addShop(coffee);

        Shop books = shop("Maple Books", "Second hand and rare books", "Books");
        books.addProduct(new Item("1962 World Atlas", "Cloth bound, maps intact", 40.00, 1, "Book"));
        books.addProduct(new Item("Pocket Dictionary", "Pocket sized, well thumbed", 8.50, 17, "Book"));
        marcus.addShop(books);

        Shop plants = shop("Fernhill Plants", "Houseplants grown just outside Ottawa", "Garden");
        plants.addProduct(new Item("Monstera Deliciosa", "Mature plant, big split leaves", 42.00, 6, "Plant"));
        plants.addProduct(new Item("Snake Plant", "Hard to kill, happy in low light", 24.00, 0, "Plant"));
        plants.addProduct(new Service("Repotting", "Bring your plant in, leave with it rehoused", 20.00, 30));
        marcus.addShop(plants);

        Shop pens = shop("Quiet Quill", "Fountain pens, inks and paper", "Stationery");
        pens.addProduct(new Item("Fountain Pen", "Steel nib, converter filled", 65.00, 5, "Pen"));
        pens.addProduct(new Item("Ink Bottle", "Iron gall, deep blue black", 18.00, 22, "Ink"));
        marcus.addShop(pens);

        // Saving the seller cascades to its shops, and each shop cascades to
        // its products, so this writes the whole graph in two calls.
        sellerRepository.save(ada);
        sellerRepository.save(marcus);

        // One shop with no seller, so the pages that show seller details are
        // exercised with that field missing too.
        Shop bikes = shop("Northside Cycles", "Repairs and refurbished frames", "Cycling");
        bikes.addProduct(new Item("Refurbished Commuter", "Stripped, rebuilt and road tested", 320.00, 2, "Bicycle"));
        bikes.addProduct(new Service("Full Tune Up", "Gears, brakes, bearings and a wash", 55.00, 90));
        shopRepository.save(bikes);
    }

    private Shop shop(String name, String description, String type) {
        return new Shop(name, description, type, null);
    }
}
