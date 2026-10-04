package com.fraud.detection.transaction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.fraud.detection.entity.Account;
import com.fraud.detection.entity.UserProfile;
import com.fraud.detection.ml.MlScoringClient;
import com.fraud.detection.repository.AccountRepository;
import com.fraud.detection.repository.MerchantRepository;
import com.fraud.detection.repository.UserProfileRepository;
import com.fraud.detection.transaction.dto.CreateTransactionRequest;

class TransactionServiceTest {

    AccountRepository accountRepo = mock(AccountRepository.class);
    TransactionRepository txnRepo = mock(TransactionRepository.class);
    UserProfileRepository profileRepo = mock(UserProfileRepository.class);
    MerchantRepository merchantRepo = mock(MerchantRepository.class);
    MlScoringClient mlClient = mock(MlScoringClient.class);
    ContextScorer scorer = mock(ContextScorer.class);

    TransactionService service;
    CreateTransactionRequest request = mock(CreateTransactionRequest.class);
    Account account = mock(Account.class, RETURNS_DEEP_STUBS);

    @BeforeEach
    void setUp() {
        service = new TransactionService(accountRepo, txnRepo, profileRepo,
                mlClient, scorer, merchantRepo);
        when(request.accountId()).thenReturn(1L);
        when(account.getUser().getEmail()).thenReturn("user@test.com");
    }

    private int statusOf(Runnable call) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, call::run);
        return ex.getStatusCode().value();
    }

    @Test
    void accountNotFound() { // UT-27
        when(accountRepo.findById(1L)).thenReturn(Optional.empty());
        assertEquals(404, statusOf(() -> service.create("user@test.com", request)));
    }

    @Test
    void foreignAccountIsForbidden() { // UT-28
        when(accountRepo.findById(1L)).thenReturn(Optional.of(account));
        assertEquals(403, statusOf(() -> service.create("someone.else@test.com", request)));
    }

    @Test
    void missingProfileIsUnprocessable() { // UT-29
        when(accountRepo.findById(1L)).thenReturn(Optional.of(account));
        when(profileRepo.findByUser_Id(anyLong())).thenReturn(Optional.empty());
        assertEquals(422, statusOf(() -> service.create("user@test.com", request)));
    }

    @Test
    void noMerchantIdentifierIsBadRequest() { // UT-30
        when(accountRepo.findById(1L)).thenReturn(Optional.of(account));
        when(profileRepo.findByUser_Id(anyLong()))
                .thenReturn(Optional.of(mock(UserProfile.class)));
        when(request.merchantId()).thenReturn(null);
        when(request.merchantPhone()).thenReturn(null);
        when(request.merchantEmail()).thenReturn(null);
        assertEquals(400, statusOf(() -> service.create("user@test.com", request)));
    }

    @Test
    void unknownMerchantIdIsNotFound() { // UT-31
        when(accountRepo.findById(1L)).thenReturn(Optional.of(account));
        when(profileRepo.findByUser_Id(anyLong()))
                .thenReturn(Optional.of(mock(UserProfile.class)));
        when(request.merchantId()).thenReturn(99L);
        when(merchantRepo.findByIdWithCategory(any())).thenReturn(Optional.empty());
        assertEquals(404, statusOf(() -> service.create("user@test.com", request)));
    }
}