package com.steph.simeme.config;

import com.steph.simeme.entity.Token;
import com.steph.simeme.entity.User;
import com.steph.simeme.entity.Wallet;
import com.steph.simeme.repository.TokenRepository;
import com.steph.simeme.repository.UserRepository;
import com.steph.simeme.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final TokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    @Override
    public void run(String... args) {
        seedFeaturedTokens();
        seedDemoUser();
    }

    private void seedFeaturedTokens() {
        if (tokenRepository.count() > 0) {
            log.info("Data token sudah ada di database, melewati proses seeding token.");
            return;
        }

        log.info("Mengisi 10 Meme Coin pilihan ke dalam database...");

        List<Token> initialTokens = List.of(
            createToken("DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263", "BONK", "Bonk", 5),
            createToken("EKpQGSJtjMFqKZ9KQanSqYXRcF8fBopzLHYxdM65zcjm", "WIF", "dogwifhat", 6),
            createToken("7GCihgDB8fe6KNjn2MYtkzZcRjQy3t9GHdC8uHYmW2hr", "POPCAT", "Popcat", 9),
            createToken("ukHH6c7mMyiWCf1b9pnWe25TSpkDDt3H5pQZgZ74J82", "BOME", "BOOK OF MEME", 6),
            createToken("MEW1gQWJ3nEXg2qgERiKu7FAFj79PHvQVREQUzScPP5", "MEW", "cat in a dogs world", 5),
            createToken("2qEHjDLDLbuBgRYvsxhc5D6uDWAivNFZGan56P1tpump", "PNUT", "Peanut the Squirrel", 6),
            createToken("CzLSujWBLFsSjncfkh59rUFqvafWcY5tzedWJSuypump", "GOAT", "Goatseus Maximus", 6),
            createToken("6p6xgHyF7AeE6TZkSmFsko444wqoP15icUSqi2jfGiPN", "TRUMP", "OFFICIAL TRUMP", 6),
            createToken("HeLp6NuQkmYB4pYWo2zYs22mESHXPQYzXbB8n4V98jwC", "AI16Z", "ai16z", 9),
            createToken("A8C3xuqscfmyLrte3VmTqrAq8kgMASius9AFNANwpump", "FWOG", "Fwog", 6)
        );

        tokenRepository.saveAll(initialTokens);
        log.info("Berhasil menyimpan 10 token awal!");
    }

    private Token createToken(String address, String symbol, String name, int decimals) {
        return Token.builder()
                .contractAddress(address)
                .symbol(symbol)
                .name(name)
                .decimals(decimals)
                .chain("solana")
                .isFeatured(true)
                .lastKnownPrice(BigDecimal.ZERO)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private void seedDemoUser() {
        if (userRepository.findByUsername("trader_satu").isPresent()) {
            return;
        }

        User demoUser = User.builder()
                .username("trader_satu")
                .createdAt(LocalDateTime.now())
                .build();
        userRepository.save(demoUser);

        // Berikan modal awal simulasi sebesar $1,000 USD
        Wallet demoWallet = Wallet.builder()
                .user(demoUser)
                .balanceUsd(new BigDecimal("1000.00000000"))
                .build();
        walletRepository.save(demoWallet);

        log.info("Berhasil membuat user demo 'trader_satu' dengan saldo awal $1,000 USD");
    }
}