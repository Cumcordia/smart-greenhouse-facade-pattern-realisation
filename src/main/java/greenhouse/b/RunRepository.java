package greenhouse.b;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Шлюз подсистемы B к SQLite. Таблицы: runs (запуски полива) и ventilation (одна строка
 * с текущим и прежним режимом вентиляции). Соединение открывается на каждую операцию,
 * поэтому «рестарт» — это просто новый RunRepository на тот же файл.
 */
public class RunRepository {

    /** Ошибка хранилища (не бизнес-ошибка). */
    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(cause == null || cause.getMessage() == null ? message : message + ": " + cause.getMessage(), cause);
        }
    }

    /** Состояние вентиляции: текущий режим и режим, к которому нужно вернуться (null, если откатывать нечего). */
    public record VentilationState(VentilationPolicy.Mode current, VentilationPolicy.Mode previous) {}

    private final String url;

    public RunRepository(String dbPath) {
        this.url = "jdbc:sqlite:" + dbPath;
        init();
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(url);
    }

    private void init() {
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS runs ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "bed_id TEXT NOT NULL, "
                    + "liters REAL NOT NULL, "
                    + "status TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS ventilation ("
                    + "id INTEGER PRIMARY KEY CHECK (id = 1), "
                    + "current_mode TEXT NOT NULL, "
                    + "previous_mode TEXT)");
        } catch (SQLException e) {
            throw new StorageException("Cannot initialise subsystem B storage", e);
        }
    }

    // ---------- runs ----------

    /** Вставляет новый запуск (id == null) или обновляет статус существующего. */
    public void save(IrrigationRun run) {
        try (Connection c = open()) {
            if (run.getId() == null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO runs(bed_id, liters, status) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, run.getBedId());
                    ps.setDouble(2, run.getLiters());
                    ps.setString(3, run.getStatus().name());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        run.assignId(keys.getLong(1));
                    }
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE runs SET status = ? WHERE id = ?")) {
                    ps.setString(1, run.getStatus().name());
                    ps.setLong(2, run.getId());
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot save run", e);
        }
    }

    public Optional<IrrigationRun> findById(long id) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, bed_id, liters, status FROM runs WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot read run " + id, e);
        }
    }

    /** Запуск грядки в статусе STARTED, если такой есть. */
    public Optional<IrrigationRun> findActiveByBed(String bedId) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, bed_id, liters, status FROM runs "
                             + "WHERE bed_id = ? AND status = 'STARTED' ORDER BY id DESC LIMIT 1")) {
            ps.setString(1, bedId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot read active run for " + bedId, e);
        }
    }

    public List<IrrigationRun> findAll() {
        List<IrrigationRun> result = new ArrayList<>();
        try (Connection c = open();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, bed_id, liters, status FROM runs ORDER BY id")) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot list runs", e);
        }
        return result;
    }

    private IrrigationRun map(ResultSet rs) throws SQLException {
        return IrrigationRun.restore(
                rs.getLong("id"),
                rs.getString("bed_id"),
                rs.getDouble("liters"),
                IrrigationRun.Status.valueOf(rs.getString("status")));
    }

    // ---------- ventilation ----------

    /** Если строки ещё нет — вентиляция выключена и откатывать нечего. */
    public VentilationState loadVentilation() {
        try (Connection c = open();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT current_mode, previous_mode FROM ventilation WHERE id = 1")) {
            if (!rs.next()) {
                return new VentilationState(VentilationPolicy.Mode.OFF, null);
            }
            String previous = rs.getString("previous_mode");
            return new VentilationState(
                    VentilationPolicy.Mode.valueOf(rs.getString("current_mode")),
                    previous == null ? null : VentilationPolicy.Mode.valueOf(previous));
        } catch (SQLException e) {
            throw new StorageException("Cannot read ventilation state", e);
        }
    }

    public void saveVentilation(VentilationState state) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR REPLACE INTO ventilation(id, current_mode, previous_mode) VALUES (1, ?, ?)")) {
            ps.setString(1, state.current().name());
            if (state.previous() == null) {
                ps.setNull(2, Types.VARCHAR);
            } else {
                ps.setString(2, state.previous().name());
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Cannot save ventilation state", e);
        }
    }

    /** Полный сброс хранилища B (для CLI и тестов). */
    public void clear() {
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM runs");
            s.execute("DELETE FROM ventilation");
            s.execute("DELETE FROM sqlite_sequence WHERE name = 'runs'");
        } catch (SQLException e) {
            throw new StorageException("Cannot clear subsystem B storage", e);
        }
    }
}
