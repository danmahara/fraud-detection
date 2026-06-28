package com.fraud.detection.merchant;

import com.fraud.detection.entity.Merchant;
import com.fraud.detection.merchant.dto.MerchantView;
import com.fraud.detection.repository.MerchantRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/merchants")
public class MerchantController {

    private final MerchantRepository merchantRepository;

    public MerchantController(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    // Active merchants for the customer payment dropdown.
    @GetMapping
    public List<MerchantView> listActiveMerchants() {
        return merchantRepository.findActiveWithCategory().stream()
                .map(m -> new MerchantView(
                        m.getId(), m.getName(), m.getEmail(), m.getPhone(),
                        m.getCategory().getId(), m.getCategory().getCode(),
                        m.getCategory().getDisplayName(),
                        m.getLat(), m.getLon(), m.getActive()))
                .toList();
    }
}