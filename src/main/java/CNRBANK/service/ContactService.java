package CNRBANK.service;


import CNRBANK.dto.ContactRequest;
import CNRBANK.entity.ContactInquiry;
import CNRBANK.repository.ContactInquiryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactService {

    private final ContactInquiryRepository repository;

    public ContactService(ContactInquiryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ContactInquiry saveInquiry(ContactRequest request) {

        ContactInquiry inquiry = new ContactInquiry();

        inquiry.setName(request.getName().trim());
        inquiry.setWorkEmail(request.getWorkEmail().trim());
        inquiry.setPhoneNumber(request.getPhoneNumber().trim());
        inquiry.setOrganizationName(
                request.getOrganizationName().trim()
        );

        return repository.save(inquiry);
    }
}
