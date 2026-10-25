package CNRBANK.service;

import CNRBANK.dto.RegistrationRequest;
import CNRBANK.entity.BankRegistration;
import CNRBANK.repository.BankRegistrationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class RegistrationService {

    private final BankRegistrationRepository repository;
    private final OtpService otpService;

    public RegistrationService(BankRegistrationRepository repository, OtpService otpService) {
        this.repository = repository;
        this.otpService = otpService;
    }

    @Transactional
    public BankRegistration register(RegistrationRequest request) {
        otpService.verifyRegistration(request);

        String phone = request.getPhone().trim();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByPhone(phone) || repository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A registration already exists for this phone number or email."
            );
        }

        BankRegistration registration = new BankRegistration();
        registration.setName(request.getName().trim());
        registration.setPhone(phone);
        registration.setEmail(email);
        registration.setAccountType(request.getAccountType().trim());
        return repository.save(registration);
    }
}
