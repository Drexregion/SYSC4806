package ca.carletonu.testsupport;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Empties every table before each test, ahead of its {@code @BeforeEach} methods.
 * <p>
 * Tables are read from the schema rather than listed, so a new entity is
 * covered without touching this class. Foreign key checks are off while the
 * tables are emptied, so it does not matter which table refers to which.
 */
class EmptyDatabaseExtension implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) throws SQLException {
        DataSource dataSource = SpringExtension.getApplicationContext(context).getBean(DataSource.class);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            String url = connection.getMetaData().getURL();
            if (!url.startsWith("jdbc:h2:mem:")) {
                throw new IllegalStateException("Refusing to empty a database that is not in-memory H2: " + url);
            }

            List<String> tables = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery(
                    "select table_name from information_schema.tables "
                            + "where table_schema = 'PUBLIC' and table_type = 'BASE TABLE'")) {
                while (rows.next()) {
                    tables.add(rows.getString(1));
                }
            }

            statement.execute("set referential_integrity false");
            try {
                for (String table : tables) {
                    statement.execute("truncate table \"" + table + "\"");
                }
            } finally {
                statement.execute("set referential_integrity true");
            }
        }
    }
}
