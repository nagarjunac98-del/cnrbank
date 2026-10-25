package CNRBANK.repository;

import CNRBANK.entity.BankRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankRegistrationRepository extends JpaRepository<BankRegistration, Long> {

    boolean existsByPhone(String phone);

    boolean existsByEmailIgnoreCase(String email);
}
