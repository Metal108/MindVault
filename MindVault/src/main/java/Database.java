import java.io.InputStream;
import java.sql.*;
import java.util.Properties;

public final class Database {
    private static final Properties P = new Properties();
    static {
        try (InputStream in = Database.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in == null) throw new IllegalStateException("Copy db.properties.example to db.properties");
            P.load(in);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private Database() {}
    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(P.getProperty("db.url"), P.getProperty("db.user"), P.getProperty("db.password"));
    }
}