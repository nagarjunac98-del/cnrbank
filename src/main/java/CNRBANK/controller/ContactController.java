
package CNRBANK.controller;

import CNRBANK.dto.ApiResponse;
import CNRBANK.dto.ContactRequest;
import CNRBANK.entity.ContactInquiry;
import CNRBANK.service.ContactService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse> saveContact(
            @Valid @RequestBody ContactRequest request) {

        ContactInquiry inquiry = contactService.saveInquiry(request);
        ApiResponse response = new ApiResponse(
                true,
                "Contact inquiry saved successfully.",
                inquiry.getId()
        );

        return ResponseEntity.ok(response);
    }
}
