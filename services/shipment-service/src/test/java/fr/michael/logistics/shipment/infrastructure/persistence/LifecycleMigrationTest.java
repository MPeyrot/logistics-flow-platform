package fr.michael.logistics.shipment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Migration V4 (cycle de vie), appliquée sur une base qui contient déjà des expéditions de V1 à V3. */
class LifecycleMigrationTest {

	private static Flyway flyway(PostgreSQLContainer db, String target) {
		return Flyway.configure()
				.dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
				.target(target)
				.load();
	}

	@Test
	void migrationKeepsExistingShipmentsAndEnforcesOneOpenAnomalyPerType() throws SQLException {
		try (PostgreSQLContainer db = new PostgreSQLContainer(DockerImageName.parse("postgres:17"))) {
			db.start();
			flyway(db, "3").migrate();
			UUID shipment = UUID.randomUUID();
			try (Connection c = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
					Statement s = c.createStatement()) {
				s.execute("INSERT INTO shipment (id, reference, origin, destination, status, created_at) VALUES ('"
						+ shipment + "', 'REF-1', 'Lyon', 'Paris', 'CREATED', now())");

				flyway(db, "4").migrate();

				var rs = s.executeQuery("SELECT expected_delivery_at FROM shipment WHERE id = '" + shipment + "'");
				assertThat(rs.next()).isTrue();
				assertThat(rs.getTimestamp(1)).isNull();

				s.execute(anomaly(shipment, "LATE"));
				assertThatThrownBy(() -> s.execute(anomaly(shipment, "LATE")))
						.isInstanceOf(SQLException.class).hasMessageContaining("duplicate key");
				s.execute(anomaly(shipment, "STALLED"));
				s.execute("UPDATE anomaly SET resolved_at = now() WHERE type = 'LATE'");
				s.execute(anomaly(shipment, "LATE"));

				String replay = "INSERT INTO dlt_replay (dlt_partition, dlt_offset, replayed_at, replayed_by) "
						+ "VALUES (0, 42, now(), 'admin')";
				s.execute(replay);
				assertThatThrownBy(() -> s.execute(replay)).isInstanceOf(SQLException.class);

				s.execute("INSERT INTO scan_event (event_id, shipment_id, type, site, occurred_at, reason) VALUES ('"
						+ UUID.randomUUID() + "', '" + shipment + "', 'DELIVERY_FAILED', 'Paris', now(), 'absent')");
			}
		}
	}

	private static String anomaly(UUID shipment, String type) {
		return "INSERT INTO anomaly (id, shipment_id, type, detected_at) VALUES ('" + UUID.randomUUID()
				+ "', '" + shipment + "', '" + type + "', now())";
	}

}
