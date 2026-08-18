package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Couvre explicitement les equals()/hashCode()/toString() ajoutes aux records
 * de telechargement a champ byte[] (Sonar S6218 : les records generent ces
 * methodes par identite de reference sur un tableau, pas par contenu). Ces
 * records ne sont jamais compares/loggues dans les chemins deja testes
 * end-to-end ailleurs (l'assertion s'y limite au contenu des octets recus
 * par HTTP), d'ou l'absence de couverture sur ces branches precises.
 */
class DownloadRecordsEqualsHashCodeToStringTest {

    @Test
    void contratTelechargeEqualsHashCodeToString() {
        var a = new ServiceDocumentContratAffiliation.ContratTelecharge("c.pdf", "application/pdf", new byte[] {1, 2, 3});
        var sameContent = new ServiceDocumentContratAffiliation.ContratTelecharge("c.pdf", "application/pdf", new byte[] {1, 2, 3});
        var differentName = new ServiceDocumentContratAffiliation.ContratTelecharge("other.pdf", "application/pdf", new byte[] {1, 2, 3});
        var differentType = new ServiceDocumentContratAffiliation.ContratTelecharge("c.pdf", "application/octet-stream", new byte[] {1, 2, 3});
        var differentBytes = new ServiceDocumentContratAffiliation.ContratTelecharge("c.pdf", "application/pdf", new byte[] {9, 9, 9});
        var nullBytes = new ServiceDocumentContratAffiliation.ContratTelecharge("c.pdf", "application/pdf", null);

        assertThat(a).isEqualTo(a);
        assertThat(a).isEqualTo(sameContent);
        assertThat(a.hashCode()).isEqualTo(sameContent.hashCode());
        assertThat(a).isNotEqualTo(differentName);
        assertThat(a).isNotEqualTo(differentType);
        assertThat(a).isNotEqualTo(differentBytes);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("pas un ContratTelecharge");

        assertThat(a.toString()).contains("nomFichier=c.pdf", "typeContenu=application/pdf", "3 octets");
        assertThat(nullBytes.toString()).contains("contenu=null");
    }

    @Test
    void documentAFusionnerEqualsHashCodeToString() {
        var a = new ServiceDocumentContratAffiliation.DocumentAFusionner("doc.pdf", "application/pdf", new byte[] {1, 2});
        var sameContent = new ServiceDocumentContratAffiliation.DocumentAFusionner("doc.pdf", "application/pdf", new byte[] {1, 2});
        var differentContent = new ServiceDocumentContratAffiliation.DocumentAFusionner("doc.pdf", "application/pdf", new byte[] {9});
        var nullBytes = new ServiceDocumentContratAffiliation.DocumentAFusionner("doc.pdf", "application/pdf", null);

        assertThat(a).isEqualTo(a);
        assertThat(a).isEqualTo(sameContent);
        assertThat(a.hashCode()).isEqualTo(sameContent.hashCode());
        assertThat(a).isNotEqualTo(differentContent);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("pas un DocumentAFusionner");

        assertThat(a.toString()).contains("fileName=doc.pdf", "contentType=application/pdf", "2 octets");
        assertThat(nullBytes.toString()).contains("content=null");
    }

    @Test
    void ticketEqualsHashCodeToString() {
        var a = new MerchantTicketService.Ticket(new byte[] {5, 6}, "ticket.pdf");
        var sameContent = new MerchantTicketService.Ticket(new byte[] {5, 6}, "ticket.pdf");
        var differentContent = new MerchantTicketService.Ticket(new byte[] {7}, "ticket.pdf");
        var nullBytes = new MerchantTicketService.Ticket(null, "ticket.pdf");

        assertThat(a).isEqualTo(a);
        assertThat(a).isEqualTo(sameContent);
        assertThat(a.hashCode()).isEqualTo(sameContent.hashCode());
        assertThat(a).isNotEqualTo(differentContent);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("pas un Ticket");

        assertThat(a.toString()).contains("2 octets", "nomFichier=ticket.pdf");
        assertThat(nullBytes.toString()).contains("contenu=null");
    }

    @Test
    void pdfEqualsHashCodeToString() {
        var a = new ReclamationPdfService.Pdf(new byte[] {3, 4}, "reclamation.pdf");
        var sameContent = new ReclamationPdfService.Pdf(new byte[] {3, 4}, "reclamation.pdf");
        var differentContent = new ReclamationPdfService.Pdf(new byte[] {8}, "reclamation.pdf");
        var nullBytes = new ReclamationPdfService.Pdf(null, "reclamation.pdf");

        assertThat(a).isEqualTo(a);
        assertThat(a).isEqualTo(sameContent);
        assertThat(a.hashCode()).isEqualTo(sameContent.hashCode());
        assertThat(a).isNotEqualTo(differentContent);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("pas un Pdf");

        assertThat(a.toString()).contains("2 octets", "nomFichier=reclamation.pdf");
        assertThat(nullBytes.toString()).contains("contenu=null");
    }

    @Test
    void documentDownloadEqualsHashCodeToString() {
        var a = new StaffAffiliationManagementService.DocumentDownload("doc.pdf", "application/pdf", new byte[] {1});
        var sameContent = new StaffAffiliationManagementService.DocumentDownload("doc.pdf", "application/pdf", new byte[] {1});
        var differentContent = new StaffAffiliationManagementService.DocumentDownload("doc.pdf", "application/pdf", new byte[] {2});
        var nullBytes = new StaffAffiliationManagementService.DocumentDownload("doc.pdf", "application/pdf", null);

        assertThat(a).isEqualTo(a);
        assertThat(a).isEqualTo(sameContent);
        assertThat(a.hashCode()).isEqualTo(sameContent.hashCode());
        assertThat(a).isNotEqualTo(differentContent);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("pas un DocumentDownload");

        assertThat(a.toString()).contains("fileName=doc.pdf", "contentType=application/pdf", "1 octets");
        assertThat(nullBytes.toString()).contains("content=null");
    }
}
