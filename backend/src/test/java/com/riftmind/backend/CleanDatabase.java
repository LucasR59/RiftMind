package com.riftmind.backend;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Vide toutes les tables (sauf l'historique Flyway) avant et après chaque test de la classe.
 *
 * <p>Obligatoire pour les tests qui écrivent en base <b>sans</b> transaction de test
 * ({@code @Transactional(propagation = NOT_SUPPORTED)}) : leurs données sont validées et resteraient en base. Le
 * conteneur PostgreSQL est partagé par toutes les classes de test qui ont la même configuration Spring : sans ce
 * nettoyage, une classe qui passe ensuite trouverait des lignes inattendues (clés dupliquées, comptes faux). L'ordre
 * des classes n'est pas le même sous Windows et sous Linux (CI), d'où des échecs visibles seulement en CI.
 *
 * <p>Les classes transactionnelles ({@code @DataJpaTest} par défaut) n'en ont pas besoin : tout est annulé à la fin
 * de chaque test.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(CleanDatabase.Extension.class)
public @interface CleanDatabase {

    /** Exécutée avant les méthodes {@code @BeforeEach} de la classe, et après ses {@code @AfterEach}. */
    class Extension implements BeforeEachCallback, AfterEachCallback {

        @Override
        public void beforeEach(ExtensionContext context) {
            truncateAll(context);
        }

        @Override
        public void afterEach(ExtensionContext context) {
            truncateAll(context);
        }

        private static void truncateAll(ExtensionContext context) {
            DataSource dataSource = SpringExtension.getApplicationContext(context).getBean(DataSource.class);
            JdbcClient jdbc = JdbcClient.create(dataSource);
            List<String> tables = jdbc.sql("""
                    SELECT quote_ident(tablename) FROM pg_tables
                    WHERE schemaname = current_schema() AND tablename <> 'flyway_schema_history'
                    """).query(String.class).list();
            if (!tables.isEmpty()) {
                jdbc.sql("TRUNCATE TABLE " + String.join(", ", tables) + " CASCADE").update();
            }
        }
    }
}
