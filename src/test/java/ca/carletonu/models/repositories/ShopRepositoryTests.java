package ca.carletonu.models.repositories;

import ca.carletonu.models.Shop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the shop search query against a real database.
 */
@DataJpaTest
class ShopRepositoryTests {

    @Autowired
    private ShopRepository shopRepository;

    @BeforeEach
    void setUp() {
        shopRepository.save(new Shop("Bean There Coffee", "Espresso and pastries", "Cafe", null));
        shopRepository.save(new Shop("Chapter One", "New and used books", "Books", null));
        shopRepository.save(new Shop("Bookworm Books", "Children's books", "Books", null));
    }

    @Test
    void matchesPartOfNameIgnoringCase() {
        assertThat(search("coffee")).containsExactly("Bean There Coffee");
    }

    @Test
    void matchesCategoryIgnoringCase() {
        assertThat(search("books")).containsExactlyInAnyOrder("Chapter One", "Bookworm Books");
    }

    @Test
    void shopMatchingOnNameAndCategoryAppearsOnce() {
        assertThat(search("book")).containsExactlyInAnyOrder("Chapter One", "Bookworm Books");
    }

    @Test
    void noMatchReturnsEmptyList() {
        assertThat(search("hardware")).isEmpty();
    }

    private List<String> search(String term) {
        return shopRepository.findByNameContainingIgnoreCaseOrTypeContainingIgnoreCase(term, term)
                .stream()
                .map(Shop::getName)
                .toList();
    }
}
