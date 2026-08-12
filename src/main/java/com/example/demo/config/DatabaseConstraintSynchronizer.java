package com.example.demo.config;

import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDocument;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.enums.TypeCommercant;
import com.example.demo.enums.TypeDocument;
import com.example.demo.enums.TypeNotification;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseConstraintSynchronizer implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public DatabaseConstraintSynchronizer(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!isSqlServer()) {
            return;
        }

        allowNullablePasswordForKeycloakManagedUsers();
        synchronizeEnumConstraint("utilisateurs", "role", "CK_utilisateurs_role_app", enumValues(RoleUser.values()));
        synchronizeEnumConstraint("commercants", "type", "CK_commercants_type_app", enumValues(TypeCommercant.values()));
        synchronizeEnumConstraint("dossiers_affiliation", "status", "CK_dossiers_affiliation_status_app", enumValues(StatusDossier.values()));
        synchronizeEnumConstraint("dossiers_affiliation", "type_affiliation", "CK_dossiers_affiliation_type_affiliation_app", enumValues(TypeAffiliation.values()));
        synchronizeEnumConstraint("documents", "type_document", "CK_documents_type_document_app", enumValues(TypeDocument.values()));
        synchronizeEnumConstraint("documents", "statut_document", "CK_documents_statut_document_app", enumValues(StatusDocument.values()));
        synchronizeEnumConstraint("notifications", "type_notification", "CK_notifications_type_notification_app", enumValues(TypeNotification.values()));
    }

    private boolean isSqlServer() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String databaseProductName = metadata.getDatabaseProductName();
            return databaseProductName != null
                && databaseProductName.toLowerCase(Locale.ROOT).contains("sql server");
        }
    }

    private void synchronizeEnumConstraint(
        String tableName,
        String columnName,
        String constraintName,
        List<String> allowedValues
    ) {
        Set<ConstraintTarget> constraintsToDrop = new LinkedHashSet<>();

        constraintsToDrop.addAll(
            jdbcTemplate.query(
                """
                SELECT cc.name, OBJECT_NAME(cc.parent_object_id) AS table_name
                FROM sys.check_constraints cc
                WHERE cc.name = ?
                """,
                (resultSet, rowNum) ->
                    new ConstraintTarget(
                        resultSet.getString("table_name"),
                        resultSet.getString("name")
                    ),
                constraintName
            )
        );

        constraintsToDrop.addAll(
            jdbcTemplate.query(
            """
            SELECT cc.name, t.name AS table_name
            FROM sys.check_constraints cc
            JOIN sys.columns col
              ON cc.parent_object_id = col.object_id
             AND cc.parent_column_id = col.column_id
            JOIN sys.tables t
              ON t.object_id = cc.parent_object_id
            WHERE t.name = ?
              AND col.name = ?
            """,
                (resultSet, rowNum) ->
                    new ConstraintTarget(
                        resultSet.getString("table_name"),
                        resultSet.getString("name")
                    ),
                tableName,
                columnName
            )
        );

        for (ConstraintTarget existingConstraint : constraintsToDrop) {
            if (
                existingConstraint.tableName() == null
                    || existingConstraint.constraintName() == null
            ) {
                continue;
            }

            jdbcTemplate.execute(
                "ALTER TABLE " + quotedIdentifier(existingConstraint.tableName())
                    + " DROP CONSTRAINT "
                    + quotedIdentifier(existingConstraint.constraintName())
            );
        }

        String allowedValuesClause = allowedValues.stream()
            .map(value -> "'" + value.replace("'", "''") + "'")
            .collect(Collectors.joining(","));

        Integer existingConstraintCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys.check_constraints WHERE name = ?",
            Integer.class,
            constraintName
        );

        if (existingConstraintCount != null && existingConstraintCount > 0) {
            return;
        }

        jdbcTemplate.execute(
            "ALTER TABLE " + quotedIdentifier(tableName)
                + " WITH NOCHECK ADD CONSTRAINT "
                + quotedIdentifier(constraintName)
                + " CHECK ("
                + quotedIdentifier(columnName)
                + " IN ("
                + allowedValuesClause
                + "))"
        );
    }

    private void allowNullablePasswordForKeycloakManagedUsers() {
        Integer notNullablePasswordColumns = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = ?
              AND COLUMN_NAME = ?
              AND IS_NULLABLE = 'NO'
            """,
            Integer.class,
            "utilisateurs",
            "password"
        );

        if (notNullablePasswordColumns == null || notNullablePasswordColumns == 0) {
            return;
        }

        jdbcTemplate.execute("ALTER TABLE [utilisateurs] ALTER COLUMN [password] NVARCHAR(255) NULL");
    }

    private List<String> enumValues(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    private String quotedIdentifier(String identifier) {
        return "[" + identifier.replace("]", "]]") + "]";
    }

    private record ConstraintTarget(String tableName, String constraintName) {
    }
}
