package com.fraud.detection.merchant;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fraud.detection.entity.Category;
import com.fraud.detection.entity.Merchant;
import com.fraud.detection.merchant.dto.CategoryView;
import com.fraud.detection.merchant.dto.MerchantRequest;
import com.fraud.detection.merchant.dto.MerchantView;
import com.fraud.detection.repository.CategoryRepository;
import com.fraud.detection.repository.MerchantRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class AdminMerchantController {

    private final MerchantRepository merchantRepository;
    private final CategoryRepository categoryRepository;

    public AdminMerchantController(MerchantRepository merchantRepository,
            CategoryRepository categoryRepository) {
        this.merchantRepository = merchantRepository;
        this.categoryRepository = categoryRepository;
    }

    // --- Categories (for the admin form dropdown) ---
    @GetMapping("/categories")
    public List<CategoryView> listCategories() {
        return categoryRepository.findAll().stream()
                .map(c -> new CategoryView(c.getId(), c.getCode(), c.getDisplayName()))
                .toList();
    }

    // --- List all merchants ---
    @GetMapping("/merchants")
    public List<MerchantView> listMerchants() {
        return merchantRepository.findAllWithCategory().stream()
                .map(this::toView)
                .toList();
    }

    // --- View one merchant ---
    @GetMapping("/merchants/{id}")
    public MerchantView getMerchant(@PathVariable Long id) {
        Merchant m = merchantRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Merchant not found"));
        return toView(m);
    }

    // --- Create ---
    @PostMapping("/merchants")
    @ResponseStatus(HttpStatus.CREATED)
    public MerchantView createMerchant(@Valid @RequestBody MerchantRequest req) {
        // Uniqueness checks (skip when the field is null/blank).
        if (notBlank(req.email()) && merchantRepository.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }
        if (notBlank(req.phone()) && merchantRepository.existsByPhone(req.phone())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone already in use");
        }

        Category category = loadCategory(req.categoryId());

        Merchant m = new Merchant();
        applyRequest(m, req, category);
        return toView(merchantRepository.save(m));
    }

    // --- Edit ---
    @PutMapping("/merchants/{id}")
    @Transactional
    public MerchantView updateMerchant(@PathVariable Long id,
            @Valid @RequestBody MerchantRequest req) {
        Merchant m = merchantRepository.findByIdWithCategory(id) // <-- was findById
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Merchant not found"));

        // Uniqueness — but allow keeping the merchant's own current value.
        if (notBlank(req.email())
                && !req.email().equals(m.getEmail())
                && merchantRepository.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }
        if (notBlank(req.phone())
                && !req.phone().equals(m.getPhone())
                && merchantRepository.existsByPhone(req.phone())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone already in use");
        }

        Category category = loadCategory(req.categoryId());
        applyRequest(m, req, category);
        return toView(merchantRepository.save(m));
    }

    // --- helpers ---

    private Category loadCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid category"));
    }

    private void applyRequest(Merchant m, MerchantRequest req, Category category) {
        m.setName(req.name());
        m.setEmail(notBlank(req.email()) ? req.email() : null);
        m.setPhone(notBlank(req.phone()) ? req.phone() : null);
        m.setCategory(category);
        m.setLat(req.lat());
        m.setLon(req.lon());
        m.setActive(req.active() == null ? true : req.active());
    }

    private MerchantView toView(Merchant m) {
        Category c = m.getCategory();
        return new MerchantView(
                m.getId(), m.getName(), m.getEmail(), m.getPhone(),
                c.getId(), c.getCode(), c.getDisplayName(),
                m.getLat(), m.getLon(), m.getActive());
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}