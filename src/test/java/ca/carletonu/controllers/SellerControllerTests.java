package ca.carletonu.controllers;

import ca.carletonu.cqrs.commands.CreateShopCommand;
import ca.carletonu.cqrs.handlers.CreateShopCommandHandler;
import ca.carletonu.models.Seller;
import ca.carletonu.models.repositories.SellerRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Wiring tests for the seller controller: which view it picks, what it puts
 * on the model, and which command it dispatches.
 *
 * These mock the handler deliberately, so they say nothing about whether a
 * shop is really saved. CreateShopIntegrationTests covers that against a real
 * database.
 */
class SellerControllerTests {

    private SellerRepository sellerRepository;
    private CreateShopCommandHandler createShopCommandHandler;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        sellerRepository = mock(SellerRepository.class);
        createShopCommandHandler = mock(CreateShopCommandHandler.class);

        SellerController controller =
                new SellerController(sellerRepository, createShopCommandHandler);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void showsTheCreateShopForm() throws Exception {
        when(sellerRepository.findById(1L)).thenReturn(Optional.of(mock(Seller.class)));

        mockMvc.perform(get("/sellers/1/shops/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("shops/create"))
                .andExpect(model().attributeExists("seller"))
                .andExpect(model().attributeExists("shop"));
    }

    @Test
    void formForAnUnknownSellerRedirectsHome() throws Exception {
        when(sellerRepository.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/sellers/1/shops/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void submittingTheFormDispatchesTheCommandItWasGiven() throws Exception {
        when(sellerRepository.findById(1L)).thenReturn(Optional.of(mock(Seller.class)));

        mockMvc.perform(post("/sellers/1/shops")
                        .param("name", "Tech Store")
                        .param("description", "Electronics and gadgets")
                        .param("type", "Retail"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute(
                        "successMessage",
                        "Your shop was created successfully!"
                ));

        // The whole form has to reach the handler, not just the name.
        verify(createShopCommandHandler).handle(new CreateShopCommand(
                1L, "Tech Store", "Electronics and gadgets", "Retail"));
    }

    @Test
    void submittingForAnUnknownSellerDispatchesNothing() throws Exception {
        when(sellerRepository.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/sellers/1/shops")
                        .param("name", "Tech Store")
                        .param("type", "Retail"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        // The controller checks the seller before building a command, so the
        // handler is never reached and nobody sees a 500.
        verify(createShopCommandHandler, never()).handle(any(CreateShopCommand.class));
    }

    @Test
    void aRejectedCommandSendsTheMerchantBackToTheFormWithTheReason() throws Exception {
        when(sellerRepository.findById(1L)).thenReturn(Optional.of(mock(Seller.class)));
        when(createShopCommandHandler.handle(any(CreateShopCommand.class)))
                .thenThrow(new IllegalArgumentException("Shop name is required."));

        mockMvc.perform(post("/sellers/1/shops")
                        .param("name", "")
                        .param("type", "Retail"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/sellers/1/shops/new"))
                .andExpect(flash().attribute("shopError", "Shop name is required."));
    }
}
