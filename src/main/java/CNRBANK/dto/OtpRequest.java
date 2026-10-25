package CNRBANK.dto;

import jakarta.validation.constraints.NotBlank;

public class OtpRequest {

    @NotBlank(message = "OTP channel is required")
    private String channel;

    @NotBlank(message = "Phone number or email address is required")
    private String destination;

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }
}
