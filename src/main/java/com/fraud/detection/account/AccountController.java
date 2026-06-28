package com.fraud.detection.account;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fraud.detection.account.dto.AccountView;
import com.fraud.detection.entity.User;
import com.fraud.detection.repository.AccountRepository;
import com.fraud.detection.repository.UserRepository;

@RestController
@RequestMapping("/api/me")
public class AccountController {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    public AccountController(UserRepository userRepository,
            AccountRepository accountRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

    // GET /api/me/accounts -> the logged-in user's own accounts.
    @GetMapping("/accounts")
    public List<AccountView> myAccounts(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        return accountRepository.findByUserId(user.getId()).stream()
                .map(a -> new AccountView(
                        a.getId(),
                        a.getAccountNumber(),
                        a.getBalance(),
                        a.getStatus().name()))
                .toList();
    }
}