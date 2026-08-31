package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.exception.OtpVerificationException;
import com.example.my_first_spring_api.model.OtpVerification;
import com.example.my_first_spring_api.repository.OtpRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Service
public class OtpService {

    private final OtpRepository otpRepository;
    private static final int OTP_LENGTH = 4;
    private static final int OTP_VALIDITY_MINUTES = 10;

    @Autowired
    public OtpService(OtpRepository otpRepository) {
        this.otpRepository = otpRepository;
    }

    @Transactional
    public String generateOtp(String mobileNumber) {
        String otpCode = String.format("%0" + OTP_LENGTH + "d", new Random().nextInt((int) Math.pow(10, OTP_LENGTH)));
        OtpVerification otp = new OtpVerification(mobileNumber, otpCode, LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES));
        otpRepository.save(otp);
        return otpCode;
    }

    @Transactional
    public void verifyOtp(String mobileNumber, String otpCode) {
        OtpVerification otp = otpRepository.findTopByMobileNumberOrderByCreatedAtDesc(mobileNumber)
                .orElseThrow(() -> new OtpVerificationException("No OTP found for this mobile number. Please request a new OTP."));

        if (otp.isExpired()) {
            throw new OtpVerificationException("OTP has expired. Please request a new OTP.");
        }

        if (otp.getVerified()) {
            throw new OtpVerificationException("This OTP has already been used. Please request a new OTP.");
        }

        if (!otp.getOtpCode().equals(otpCode)) {
            throw new OtpVerificationException("Invalid OTP. Please try again.");
        }

        otp.setVerified(true);
        otpRepository.save(otp);
    }
}
