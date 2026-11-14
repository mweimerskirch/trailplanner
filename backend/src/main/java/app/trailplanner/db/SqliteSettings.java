package app.trailplanner.db;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Write-ahead logging lets readers continue while a write is in progress. The setting is stored in
 * the database file, but cannot be changed inside a transaction, so it is not a migration.
 */
@Component
public class SqliteSettings implements InitializingBean {

  private final JdbcClient jdbc;

  public SqliteSettings(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void afterPropertiesSet() {
    jdbc.sql("PRAGMA journal_mode = WAL").query().singleValue();
  }
}
