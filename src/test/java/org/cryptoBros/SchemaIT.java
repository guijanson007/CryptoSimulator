package org.cryptoBros;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * The schema script must be safe to run on an existing schema.
 * Runs in its own throwaway database so the shared test database is never affected.
 */
class SchemaIT extends IntegrationTestBase {

    @Test
    @DisplayName("#44 createTables.sql can be applied twice (suspected bug #4)")
    void schema_canBeAppliedTwice() throws Exception {
        String script = Files.readString(Path.of("createTables.sql"));
        execute("DROP DATABASE IF EXISTS schema_check");
        execute("CREATE DATABASE schema_check");
        try (Connection conn = connect("schema_check"); Statement st = conn.createStatement()) {
            st.execute(script);
            assertDoesNotThrow(() -> st.execute(script), "second application of createTables.sql");
        } finally {
            execute("DROP DATABASE schema_check");
        }
    }
}
