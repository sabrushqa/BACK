package com.example.demo.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.entities.commercant;
import com.example.demo.entities.pdv;
import com.example.demo.entities.tpe;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests d'integration des requetes JPQL personnalisees de TpeRepository
 * contre SQL Server reel (master5_test).
 */
@SpringBootTest
@Transactional
class TpeRepositoryTest {

    @Autowired
    private TpeRepository tpeRepository;

    @Autowired
    private PdvRepository pdvRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    private commercant commercantA;
    private commercant commercantB;
    private pdv pdvA;
    private pdv pdvB;

    @BeforeEach
    void setUp() {
        commercantA = commercantRepository.save(newCommercant("Boutique A"));
        commercantB = commercantRepository.save(newCommercant("Boutique B"));

        pdvA = new pdv();
        pdvA.setCommercant(commercantA);
        pdvA.setNomPDV("PDV A");
        pdvA = pdvRepository.save(pdvA);

        pdvB = new pdv();
        pdvB.setCommercant(commercantB);
        pdvB.setNomPDV("PDV B");
        pdvB = pdvRepository.save(pdvB);
    }

    private commercant newCommercant(String nom) {
        commercant c = new commercant();
        c.setNomCommercial(nom);
        return c;
    }

    private tpe newTpe(pdv owner, String numeroSerie) {
        tpe t = new tpe();
        t.setPdv(owner);
        t.setNumeroSerie(numeroSerie);
        return tpeRepository.save(t);
    }

    @Test
    void findByCommercantIdOrderByIdDescReturnsOnlyOwnedTpesNewestFirst() {
        tpe first = newTpe(pdvA, "TPE-A-1");
        tpe second = newTpe(pdvA, "TPE-A-2");
        newTpe(pdvB, "TPE-B-1");

        List<tpe> results = tpeRepository.findByCommercantIdOrderByIdDesc(commercantA.getIdCommercant());

        assertThat(results).extracting(tpe::getNumeroSerie).containsExactly("TPE-A-2", "TPE-A-1");
    }

    @Test
    void findAssignedToCommercantReturnsEmptyWhenTpeBelongsToAnotherCommercant() {
        tpe tpeOfB = newTpe(pdvB, "TPE-B-1");

        Optional<tpe> result = tpeRepository.findAssignedToCommercant(
            tpeOfB.getIdTPE(),
            commercantA.getIdCommercant()
        );

        assertThat(result).isEmpty();
    }

    @Test
    void findAssignedToCommercantReturnsTpeWhenOwnedByCommercant() {
        tpe tpeOfA = newTpe(pdvA, "TPE-A-1");

        Optional<tpe> result = tpeRepository.findAssignedToCommercant(
            tpeOfA.getIdTPE(),
            commercantA.getIdCommercant()
        );

        assertThat(result).isPresent();
        assertThat(result.get().getNumeroSerie()).isEqualTo("TPE-A-1");
    }

    @Test
    void existsByNumeroSerieDetectsDuplicates() {
        newTpe(pdvA, "TPE-UNIQUE-1");

        assertThat(tpeRepository.existsByNumeroSerie("TPE-UNIQUE-1")).isTrue();
        assertThat(tpeRepository.existsByNumeroSerie("TPE-INEXISTANT")).isFalse();
    }
}
