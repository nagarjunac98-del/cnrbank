package CNRBANK.service;

import CNRBANK.dto.OtpRequest;
import CNRBANK.dto.RegistrationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    private static final int OTP_LIFETIME_SECONDS = 300;
    private static final int RESEND_WAIT_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 5;

    private final JavaMailSender mailSender;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] otpHashSecret = new byte[32];
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastSentAt = new ConcurrentHashMap<>();

    @Value("${app.otp.twilio-account-sid:}")
    private String twilioAccountSid;

    @Value("${app.otp.twilio-auth-token:}")
    private String twilioAuthToken;

    @Value("${app.otp.twilio-from-number:}")
    private String twilioFromNumber;

    @Value("${app.otp.email-from:}")
    private String emailFrom;

    public OtpService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
        secureRandom.nextBytes(otpHashSecret);
    }

    public synchronized void sendOtp(OtpRequest request) {
        String channel = request.getChannel().trim().toLowerCase(Locale.ROOT);
        String destination = normalizeDestination(channel, request.getDestination());
        String key = challengeKey(channel, destination);
        Instant sentAt = lastSentAt.get(key);

        if (sentAt != null && Instant.now().isBefore(sentAt.plusSeconds(RESEND_WAIT_SECONDS))) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait 60 seconds before requesting another OTP."
            );
        }

        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        deliver(channel, destination, otp);
        challenges.put(key, new Challenge(hash(key, otp), Instant.now().plusSeconds(OTP_LIFETIME_SECONDS)));
        lastSentAt.put(key, Instant.now());
    }

    public synchronized void verifyRegistration(RegistrationRequest request) {
        String phoneKey = challengeKey("phone", normalizeDestination("phone", request.getPhone()));
        String emailKey = challengeKey("email", normalizeDestination("email", request.getEmail()));

        if (!isValid(phoneKey, request.getPhoneOtp()) || !isValid(emailKey, request.getEmailOtp())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "One or both OTPs are invalid or expired. Request new OTPs and try again."
            );
        }

        challenges.remove(phoneKey);
        challenges.remove(emailKey);
    }

    private boolean isValid(String key, String otp) {
        Challenge challenge = challenges.get(key);
        if (challenge == null) {
            return false;
        }
        if (Instant.now().isAfter(challenge.expiresAt())) {
            challenges.remove(key, challenge);
            return false;
        }
        if (challenge.attempts() >= MAX_ATTEMPTS) {
            challenges.remove(key, challenge);
            return false;
        }

        byte[] providedHash = hash(key, otp);
        if (MessageDigest.isEqual(challenge.otpHash(), providedHash)) {
            return true;
        }

        challenge.incrementAttempts();
        if (challenge.attempts() >= MAX_ATTEMPTS) {
            challenges.remove(key, challenge);
        }
        return false;
    }

    private void deliver(String channel, String destination, String otp) {
        if ("phone".equals(channel)) {
            sendSms(destination, otp);
        } else if ("email".equals(channel)) {
            sendEmail(destination, otp);
        } else {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "OTP channel must be phone or email."
            );
        }
    }

    private void sendSms(String phone, String otp) {
        if (isBlank(twilioAccountSid) || isBlank(twilioAuthToken) || isBlank(twilioFromNumber)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SMS delivery is not configured. Set TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, and TWILIO_FROM_NUMBER."
            );
        }

        String form = "To=" + encode("+91" + phone)
                + "&From=" + encode(twilioFromNumber)
                + "&Body=" + encode("Your CNRBANK verification code is " + otp
                + ". It expires in 5 minutes. Do not share this code.");
        String credentials = Base64.getEncoder().encodeToString(
                (twilioAccountSid + ":" + twilioAuthToken).getBytes(StandardCharsets.UTF_8)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/"
                        + encodePath(twilioAccountSid) + "/Messages.json"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        try {
            HttpResponse<Void> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.discarding()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Twilio rejected the SMS. Check the Twilio account, trial restrictions, and destination number."
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SMS delivery was interrupted. Please try again."
            );
        } catch (java.io.IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not reach the SMS provider. Check the backend network connection."
            );
        }
    }

    private void sendEmail(String destination, String otp) {
        if (isBlank(emailFrom)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Email delivery is not configured. Set SMTP_FROM and SMTP server settings."
            );
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(emailFrom);
        message.setTo(destination);
        message.setSubject("Your CNRBANK verification code");
        message.setText("Your CNRBANK verification code is " + otp
                + ". It expires in 5 minutes. Do not share this code.");

        try {
            mailSender.send(message);
        } catch (MailException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not deliver the email OTP. Check the SMTP settings and sender authorization."
            );
        }
    }

    private String normalizeDestination(String channel, String destination) {
        String value = destination.trim();
        if ("phone".equals(channel)) {
            if (!value.matches("^[6-9][0-9]{9}$")) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Enter a valid 10-digit Indian mobile number."
                );
            }
            return value;
        }
        if ("email".equals(channel)) {
            if (!value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address.");
            }
            return value.toLowerCase(Locale.ROOT);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP channel must be phone or email.");
    }

    private String challengeKey(String channel, String destination) {
        return channel + ":" + destination;
    }

    private byte[] hash(String key, String otp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(otpHashSecret, "HmacSHA256"));
            return mac.doFinal((key + ":" + otp).getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("OTP hashing is unavailable.", exception);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodePath(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final class Challenge {
        private final byte[] otpHash;
        private final Instant expiresAt;
        private int attempts;

        private Challenge(byte[] otpHash, Instant expiresAt) {
            this.otpHash = otpHash;
            this.expiresAt = expiresAt;
        }

        private byte[] otpHash() {
            return otpHash;
        }

        private Instant expiresAt() {
            return expiresAt;
        }

        private int attempts() {
            return attempts;
        }

        private void incrementAttempts() {
            attempts++;
        }
    }
}
