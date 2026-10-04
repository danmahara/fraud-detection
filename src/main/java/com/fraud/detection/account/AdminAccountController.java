package com.fraud.detection.account;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.fraud.detection.account.dto.AccountAdminResponse;
import com.fraud.detection.account.dto.UpdateAccountRequest;

@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {

    private final AdminAccountService service;

    public AdminAccountController(AdminAccountService service) {
        this.service = service;
    }

    @PatchMapping("/{id}")
    public AccountAdminResponse update(@PathVariable Long id,
            @Valid @RequestBody UpdateAccountRequest request,
            Authentication auth) {
        return service.update(id, request, auth.getName());
    }
}
