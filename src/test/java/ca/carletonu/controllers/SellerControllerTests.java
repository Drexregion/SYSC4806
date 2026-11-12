package ca.carletonu.controllers;

import ca.carletonu.models.Seller;
import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.SellerRepository;
import ca.carletonu.models.repositories.ShopRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SellerControllerTest {

    private SellerRepository sellerRepository;
    private ShopRepository shopRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        sellerRepository = mock(SellerRepository.class);
        shopRepository = mock(ShopRepository.class);

        SellerController controller =
                new SellerController(shopRepository, sellerRepository);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testCreateShopForm() throws Exception {
        // Arrange
        Seller seller = mock(Seller.class);

        when(sellerRepository.findById(1L))
                .thenReturn(Optional.of(seller));

        // Act and Assert
        mockMvc.perform(get("/1/shops/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("shops/create"))
                .andExpect(model().attributeExists("seller"))
                .andExpect(model().attributeExists("shop"));
    }

    @Test
    void testCreateShopFormSellerNotFound() throws Exception {
        // Arrange
        when(sellerRepository.findById(1L))
                .thenReturn(Optional.empty());

        // Act and Assert
        mockMvc.perform(get("/1/shops/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void testCreateShop() throws Exception {
        // Arrange
        Seller seller = mock(Seller.class);

        when(sellerRepository.findById(1L))
                .thenReturn(Optional.of(seller));

        // Act and Assert
        mockMvc.perform(post("/1/shops"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute(
                        "successMessage",
                        "Your shop was created successfully!"
                ));

        // Verify that the shop was saved and associated with the seller.
        verify(shopRepository).save(any(Shop.class));
    }

    @Test
    void testCreateShopSellerNotFound() throws Exception {
        // Arrange
        when(sellerRepository.findById(1L))
                .thenReturn(Optional.empty());

        // Act and Assert
        mockMvc.perform(post("/1/shops"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        // No shop should be saved if the seller does not exist.
        verify(shopRepository, never()).save(any(Shop.class));
    }
}
