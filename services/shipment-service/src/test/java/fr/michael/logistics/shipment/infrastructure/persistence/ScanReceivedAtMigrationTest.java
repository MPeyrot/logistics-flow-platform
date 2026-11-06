package fr.michael.logistics.shipment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Migration V5 (heure de réception des scans), appliquée sur une base qui contient déjà des scans de V4. */
class ScanReceivedAtMigrationTest {

	private static Flyway flyway(PostgreSQLContainer db, String target) {
		return Flyway.configure()
				.dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
				.target(target)
				.load();
	}

	@Test
	void existingScansAreReceivedAtTheirOccurredAtAndNewScansMustHaveAReceptionTime() throws SQLException {
		try (PostgreSQLContainer db = new PostgreSQLContainer(DockerImageName.parse("postgres:17"))) {
			db.start();
			flyway(db, "4").migrate();
			UUID shipment = UUID.randomUUID();
			UUID scan = UUID.randomUUID();
			try (Connection c = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
					Statement s = c.createStatement()) {
				s.execute("INSERT INTO shipment (id, reference, origin, destination, status, created_at) VALUES ('"
						+ shipment + "', 'REF-1', 'Lyon', 'Paris', 'IN_TRANSIT', now())");
				s.execute("INSERT INTO scan_event (event_id, shipment_id, type, site, occurred_at) VALUES ('"
						+ scan + "', '" + shipment + "', 'PICKED_UP', 'Lyon', '2026-11-01T08:30:00Z')");

				flyway(db, "latest").migrate();

				ResultSet rs = s.executeQuery("SELECT received_at FROM scan_event WHERE event_id = '" + scan + "'");
				assertThat(rs.next()).isTrue();
				assertThat(rs.getTimestamp(1).toInstant()).isEqualTo(Instant.parse("2026-11-01T08:30:00Z"));

				assertThatThrownBy(() -> s.execute(
						"INSERT INTO scan_event (event_id, shipment_id, type, site, occurred_at) VALUES ('"
								+ UUID.randomUUID() + "', '" + shipment + "', 'ARRIVED_AT_SITE', 'Paris', now())"))
						.isInstanceOf(SQLException.class).hasMessageContaining("received_at");
			}
		}
	}

}
