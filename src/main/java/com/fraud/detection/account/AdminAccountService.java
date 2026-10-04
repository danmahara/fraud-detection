package com.fraud.detection.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fraud.detection.account.dto.AccountAdminResponse;
import com.fraud.detection.account.dto.UpdateAccountRequest;
import jakarta.transaction.Transactional;

import com.fraud.detection.repository.AccountRepository;
import com.fraud.detection.entity.Account;

@Service
public class AdminAccountService {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountService.class);

    private final AccountRepository accountRepository;

    public AdminAccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public AccountAdminResponse update(Long id, UpdateAccountRequest req, String adminEmail) {
        Account account = accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account not found"));

        log.info("ADMIN {} updated account {}: status {} -> {}, balance {} -> {}",
                adminEmail, account.getAccountNumber(),
                account.getStatus(), req.status(),
                account.getBalance(), req.balance());

        account.setStatus(req.status());
        account.setBalance(req.balance());
        accountRepository.save(account);

        return new AccountAdminResponse(
                account.getId(), account.getAccountNumber(),
                account.getBalance(), account.getStatus());
    }
}
