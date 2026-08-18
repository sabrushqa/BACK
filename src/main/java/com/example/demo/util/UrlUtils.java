package com.example.demo.util;

/**
 * Petits helpers de normalisation d'URL partages par les services qui en ont
 * besoin (Keycloak, Google/Microsoft Calendar...) — factorise ici pour eviter
 * la duplication de code (Sonar CPD) qui existait quand chaque service
 * definissait sa propre copie de stripTrailingSlash().
 */
public final class UrlUtils {

    private UrlUtils() {
    }

    /**
     * Retire les "/" de fin de chaine. Implemente sans regex : un pattern du
     * type "/+$" est signale par Sonar (S8786) comme motif a backtracking
     * super-lineaire, alors qu'une boucle simple est strictement equivalente
     * et intrinsequement lineaire, sans moteur regex implique.
     */
    public static String stripTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
