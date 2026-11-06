package ca.carletonu.models.repositories;

import ca.carletonu.models.Person;
import org.springframework.data.repository.CrudRepository;

public interface PersonRepository extends CrudRepository<Person, Long> {

}
