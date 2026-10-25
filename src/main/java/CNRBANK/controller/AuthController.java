package CNRBANK.controller;

import CNRBANK.dto.ApiResponse;
import CNRBANK.dto.OtpRequest;
import CNRBANK.dto.RegistrationRequest;
import CNRBANK.entity.BankRegistration;
import CNRBANK.service.OtpService;
import CNRBANK.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final OtpService otpService;
    private final RegistrationService registrationService;

    public AuthController(OtpService otpService, RegistrationService registrationService) {
        this.otpService = otpService;
        this.registrationService = registrationService;
    }

    @PostMapping("/otp")
    public ResponseEntity<ApiResponse> sendOtp(@Valid @RequestBody OtpRequest request) {
        otpService.sendOtp(request);
        return ResponseEntity.ok(new ApiResponse(true, "OTP sent successfully.", null));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse> register(
            @Valid @RequestBody RegistrationRequest request) {
        BankRegistration registration = registrationService.register(request);
        return ResponseEntity.ok(new ApiResponse(
                true,
                "Registration saved successfully.",
                registration.getId()
        ));
    }
}
