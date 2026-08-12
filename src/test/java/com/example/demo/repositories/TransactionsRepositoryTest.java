package com.example.demo.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.entities.commercant;
import com.example.demo.entities.pdv;
import com.example.demo.entities.tpe;
import com.example.demo.entities.transactions;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifie l'ordre (date puis heure decroissants) et le cloisonnement par
 * commercant des transactions les plus recentes.
 */
@SpringBootTest
@Transactional
class TransactionsRepositoryTest {

    @Autowired
    private TransactionsRepository transactionsRepository;

    @Autowired
    private TpeRepository tpeRepository;

    @Autowired
    private PdvRepository pdvRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    private tpe tpeA;

    @BeforeEach
    void setUp() {
        commercant commercantA = new commercant();
        commercantA.setNomCommercial("Boutique Transactions Test");
        commercantA = commercantRepository.save(commercantA);

        pdv pdvA = new pdv();
        pdvA.setCommercant(commercantA);
        pdvA = pdvRepository.save(pdvA);

        tpeA = new tpe();
        tpeA.setPdv(pdvA);
        tpeA.setNumeroSerie("TPE-TX-TEST-1");
        tpeA = tpeRepository.save(tpeA);
    }

    private transactions newTransaction(LocalDate date, LocalTime heure) {
        transactions t = new transactions();
        t.setTpe(tpeA);
        t.setDateTransaction(date);
        t.setHeureTransaction(heure);
        return transactionsRepository.save(t);
    }

    @Test
    void top8OrdersByDateThenHeureDescending() {
        transactions older = newTransaction(LocalDate.now().minusDays(1), LocalTime.of(10, 0));
        transactions sameDayEarlier = newTransaction(LocalDate.now(), LocalTime.of(9, 0));
        transactions sameDayLater = newTransaction(LocalDate.now(), LocalTime.of(15, 0));

        List<transactions> results = transactionsRepository
            .findTop8ByTpe_Pdv_Commercant_IdCommercantOrderByDateTransactionDescHeureTransactionDesc(
                tpeA.getPdv().getCommercant().getIdCommercant()
            );

        assertThat(results).extracting(transactions::getIdTransaction)
            .containsExactly(
                sameDayLater.getIdTransaction(),
                sameDayEarlier.getIdTransaction(),
                older.getIdTransaction()
            );
    }

    @Test
    void countByCommercantMatchesNumberOfTransactions() {
        newTransaction(LocalDate.now(), LocalTime.NOON);
        newTransaction(LocalDate.now(), LocalTime.NOON);

        long count = transactionsRepository.countByTpe_Pdv_Commercant_IdCommercant(
            tpeA.getPdv().getCommercant().getIdCommercant()
        );

        assertThat(count).isEqualTo(2);
    }
}
