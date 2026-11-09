package ca.carletonu.controllers;

import ca.carletonu.models.Customer;
import ca.carletonu.models.Person;
import ca.carletonu.models.repositories.PersonRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Shows and updates a user's account 
 * Sellers and customers share one page: both are stored as a Person, so one
 * lookup through PersonRepository finds either, and the template switches on
 * which kind it got.
 */
@Controller
public class AccountController {

    private final PersonRepository personRepository;

    public AccountController(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @GetMapping("/accounts/{id}")
    public String view(@PathVariable Long id, Model model) {
        Person person = find(id);
        model.addAttribute("person", person);
        model.addAttribute("isCustomer", person instanceof Customer);
        return "accounts/account";
    }

    /**
     * Copies only the editable fields onto the stored account. Binding a whole
     * Person from the form instead would null the password, which the form
     * never sends.
     */
    @PostMapping("/accounts/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam(defaultValue = "") String name,
                         @RequestParam(defaultValue = "") String email,
                         @RequestParam(required = false) String phone,
                         @RequestParam(required = false) String address,
                         RedirectAttributes redirectAttributes) {
        Person person = find(id);
        if (name.isBlank() || email.isBlank()) {
            redirectAttributes.addFlashAttribute("accountError", "Name and email are required.");
            return "redirect:/accounts/" + id;
        }

        person.setName(name.trim());
        person.setEmail(email.trim());
        person.setPhone(phone);
        if (person instanceof Customer customer) {
            customer.setAddress(address);
        }
        personRepository.save(person);

        // Redirect after post, so refreshing the page does not resubmit the form
        redirectAttributes.addFlashAttribute("accountMessage", "Account updated.");
        return "redirect:/accounts/" + id;
    }

    private Person find(Long id) {
        return personRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No account with id " + id));
    }
}
