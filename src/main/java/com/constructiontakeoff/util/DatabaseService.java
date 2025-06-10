package com.constructiontakeoff.util;

import com.constructiontakeoff.model.QuantityItem;
import com.constructiontakeoff.model.TakeoffItem;
import com.constructiontakeoff.model.TakeoffRecord;
import com.constructiontakeoff.model.User;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseService {

    private static DatabaseService instance;

    private static final Logger logger = Logger.getLogger(DatabaseService.class.getName());
    private static final String DB_URL = "jdbc:h2:./takeoff_history;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";

    private Connection dbConnection;

    private DatabaseService() {
        try {
            this.dbConnection = DriverManager.getConnection(DB_URL);
            logger.info("Database connection established successfully to: " + DB_URL);
            initDatabase();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to establish database connection or initialize schema", e);
            throw new RuntimeException("Failed to initialize DatabaseService: " + e.getMessage(), e);
        }
    }

    public static synchronized DatabaseService getInstance() {
        if (instance == null) {
            instance = new DatabaseService();
        }
        return instance;
    }

    private void initDatabase() {
        String createUserTableSQL = "CREATE TABLE IF NOT EXISTS USERS (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "username VARCHAR(255) UNIQUE NOT NULL," +
                "email VARCHAR(255) UNIQUE," +
                "password_hash VARCHAR(255)," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ");";

        String createTakeoffHistoryTableSQL = "CREATE TABLE IF NOT EXISTS TAKEOFF_HISTORY (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "user_id INT NOT NULL," +
                "original_file_name VARCHAR(255) NOT NULL," +
                "processed_file_name VARCHAR(255)," +
                "project_name VARCHAR(255)," +
                "status VARCHAR(50)," +
                "takeoff_timestamp TIMESTAMP NOT NULL," +
                "pdf_absolute_path VARCHAR(1024) NULL," +
                "FOREIGN KEY (user_id) REFERENCES USERS(id)" +
                ");";

        String createTakeoffItemsTableSQL = "CREATE TABLE IF NOT EXISTS TAKEOFF_ITEMS (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "history_id INT NOT NULL," +
                "material VARCHAR(255) NOT NULL," +
                "quantity DOUBLE NOT NULL," +
                "unit VARCHAR(50) NOT NULL," +
                "FOREIGN KEY (history_id) REFERENCES TAKEOFF_HISTORY(id) ON DELETE CASCADE" +
                ");";

        try (Statement stmt = this.dbConnection.createStatement()) {
            stmt.execute(createUserTableSQL);
            logger.info("USERS table checked/created successfully.");
            stmt.execute(createTakeoffHistoryTableSQL);
            logger.info("TAKEOFF_HISTORY table checked/created successfully.");
            stmt.execute(createTakeoffItemsTableSQL);
            logger.info("TAKEOFF_ITEMS table checked/created successfully.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error initializing database schema on existing connection", e);
            throw new RuntimeException("Failed to initialize database schema.", e);
        }
    }

    public User getOrCreateUser(String username) {
        if (this.dbConnection == null) {
            logger.severe("Database connection is not available in getOrCreateUser.");
            throw new IllegalStateException("Database connection not initialized.");
        }
        String selectUserSQL = "SELECT id, username, email, password_hash FROM USERS WHERE username = ?";
        String insertUserSQL = "INSERT INTO USERS (username) VALUES (?)";

        User user = null;
        try (PreparedStatement pstmtSelect = this.dbConnection.prepareStatement(selectUserSQL)) {
            pstmtSelect.setString(1, username);
            try (ResultSet rs = pstmtSelect.executeQuery()) {
                if (rs.next()) {
                    user = new User();
                    user.setId(rs.getInt("id"));
                    user.setUsername(rs.getString("username"));
                    user.setEmail(rs.getString("email"));
                    user.setPasswordHash(rs.getString("password_hash"));
                    logger.info("User '" + username + "' found with ID: " + user.getId());
                    return user;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error selecting user: " + username, e);
            throw new RuntimeException("Database error when selecting user: " + username, e);
        }

        logger.info("User '" + username + "' not found. Creating new user.");
        try (PreparedStatement pstmtInsert = this.dbConnection.prepareStatement(insertUserSQL,
                Statement.RETURN_GENERATED_KEYS)) {
            pstmtInsert.setString(1, username);
            int affectedRows = pstmtInsert.executeUpdate();

            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmtInsert.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        user = new User();
                        user.setId(generatedKeys.getInt(1));
                        user.setUsername(username);
                        logger.info("User '" + username + "' created successfully with ID: " + user.getId());
                        return user;
                    } else {
                        logger.severe("Failed to retrieve ID for newly created user: " + username);
                        throw new SQLException("Creating user failed, no ID obtained.");
                    }
                }
            } else {
                logger.severe("Creating user '" + username + "' failed, no rows affected.");
                throw new SQLException("Creating user failed, no rows affected.");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error creating user: " + username, e);
            throw new RuntimeException("Database error when creating user: " + username, e);
        }
    }

    public int saveTakeoff(User user, String originalFileName, String processedFileName, String status,
            String projectName, List<QuantityItem> quantityItems, String pdfAbsolutePath) {
        logger.info("saveTakeoff called with user ID=" + user.getId() + ", originalFile=" + originalFileName
                + ", processedFile=" + processedFileName + ", status=" + status + ", project=" + projectName
                + ", items count=" + quantityItems.size());

        if (this.dbConnection == null) {
            logger.severe("Database connection is not available in saveTakeoff.");
            throw new IllegalStateException("Database connection not initialized.");
        }

        String insertHistorySQL = "INSERT INTO TAKEOFF_HISTORY (user_id, original_file_name, processed_file_name, status, project_name, takeoff_timestamp, pdf_absolute_path) VALUES (?, ?, ?, ?, ?, ?, ?)";
        String insertItemSQL = "INSERT INTO TAKEOFF_ITEMS (history_id, material, quantity, unit) VALUES (?, ?, ?, ?)";

        logger.info("Using SQL: " + insertHistorySQL + " and " + insertItemSQL);
        boolean originalAutoCommitState = true;
        SQLException caughtException = null;
        int historyId = -1;

        try {
            originalAutoCommitState = this.dbConnection.getAutoCommit();
            logger.info("Original autoCommit state: " + originalAutoCommitState);
            this.dbConnection.setAutoCommit(false);
            logger.info("Set autoCommit to false for transaction");

            try (PreparedStatement pstmtHistory = this.dbConnection.prepareStatement(insertHistorySQL,
                    Statement.RETURN_GENERATED_KEYS)) {
                logger.info("Setting history parameters: user_id=" + user.getId() + ", original_file_name="
                        + originalFileName + ", processed_file_name=" + processedFileName + ", status=" + status
                        + ", project_name=" + projectName);
                pstmtHistory.setInt(1, user.getId());
                pstmtHistory.setString(2, originalFileName);
                pstmtHistory.setString(3, processedFileName);
                pstmtHistory.setString(4, status);
                pstmtHistory.setString(5, projectName);
                pstmtHistory.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
                pstmtHistory.setString(7, pdfAbsolutePath);

                logger.info("Executing history insert");
                int historyRowsAffected = pstmtHistory.executeUpdate();
                logger.info("History insert affected " + historyRowsAffected + " rows");

                try (ResultSet generatedKeys = pstmtHistory.getGeneratedKeys()) {
                    logger.info("Retrieving generated history ID");
                    if (generatedKeys.next()) {
                        historyId = generatedKeys.getInt(1);
                        logger.info("Created takeoff record with ID: " + historyId);
                    } else {
                        logger.severe("No generated keys returned from history insert");
                        throw new SQLException("Creating takeoff history failed, no ID obtained.");
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error inserting takeoff history", e);
                throw e;
            }

            if (!quantityItems.isEmpty()) {
                logger.info("Preparing to insert " + quantityItems.size() + " items for history ID " + historyId);
                try (PreparedStatement pstmtItem = this.dbConnection.prepareStatement(insertItemSQL)) {
                    int itemCount = 0;
                    for (QuantityItem item : quantityItems) {
                        pstmtItem.setLong(1, historyId);
                        pstmtItem.setString(2, item.getMaterial());
                        pstmtItem.setDouble(3, item.getQuantity());
                        pstmtItem.setString(4, item.getUnit());
                        pstmtItem.addBatch();
                        itemCount++;

                        if (itemCount % 100 == 0) {
                            logger.info("Executing batch for " + itemCount + " items");
                            pstmtItem.executeBatch();
                        }
                    }

                    logger.info("Executing final batch for " + (quantityItems.size() % 100) + " remaining items");
                    pstmtItem.executeBatch();

                    logger.info("Successfully inserted " + quantityItems.size() + " items for history ID " + historyId);
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Error inserting takeoff items", e);
                    throw e;
                }
            } else {
                logger.info("No items to insert for history ID " + historyId);
            }

            logger.info("Committing transaction for history ID: " + historyId);
            this.dbConnection.commit();
            logger.info("Transaction committed successfully for history ID: " + historyId);

            return historyId;

        } catch (SQLException e) {
            caughtException = e;
            logger.log(Level.SEVERE, "Error saving takeoff for file: " + originalFileName, e);

            try {
                if (this.dbConnection != null && !this.dbConnection.isClosed()) {
                    logger.info("Attempting to rollback transaction for file: " + originalFileName);
                    this.dbConnection.rollback();
                    logger.info("Transaction rolled back successfully for file: " + originalFileName);
                }
            } catch (SQLException rollbackEx) {
                logger.log(Level.SEVERE, "Error rolling back transaction for file: " + originalFileName, rollbackEx);
                throw new RuntimeException("Error saving takeoff and failed to rollback: " + e.getMessage()
                        + "; Rollback error: " + rollbackEx.getMessage(), e);
            }

            throw new RuntimeException("Error saving takeoff: " + e.getMessage(), e);

        } finally {
            if (this.dbConnection != null) {
                try {
                    if (this.dbConnection.getAutoCommit() != originalAutoCommitState) {
                        this.dbConnection.setAutoCommit(originalAutoCommitState);
                        logger.info("Restored auto-commit to: " + originalAutoCommitState);
                    }
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Error resetting auto-commit after saving takeoff", e);
                    if (caughtException != null) {
                        throw new RuntimeException(
                                "Error saving takeoff and failed to reset auto-commit. Original error: " +
                                        caughtException.getMessage() + "; Auto-commit error: " + e.getMessage(),
                                caughtException);
                    } else {
                        throw new RuntimeException("Error resetting auto-commit after saving takeoff", e);
                    }
                }
            }
        }
    }

    public List<TakeoffRecord> getTakeoffHistoryForUser(User user) {
        if (this.dbConnection == null) {
            logger.severe("Database connection is not available in getTakeoffHistoryForUser.");
            throw new IllegalStateException("Database connection not initialized.");
        }
        List<TakeoffRecord> historyRecords = new ArrayList<>();

        String selectHistorySQL = "SELECT id, user_id, original_file_name, processed_file_name, project_name, status, takeoff_timestamp, pdf_absolute_path "
                +
                "FROM TAKEOFF_HISTORY WHERE user_id = ? ORDER BY takeoff_timestamp DESC";

        logger.info("Executing SQL query: " + selectHistorySQL);

        try (PreparedStatement pstmt = this.dbConnection.prepareStatement(selectHistorySQL)) {
            pstmt.setInt(1, user.getId());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    TakeoffRecord record = new TakeoffRecord();
                    record.setId(rs.getInt("id"));
                    record.setUserId(rs.getInt("user_id"));
                    record.setOriginalFileName(rs.getString("original_file_name"));
                    record.setProcessedFileName(rs.getString("processed_file_name"));
                    record.setProjectName(rs.getString("project_name"));
                    record.setStatus(rs.getString("status"));
                    record.setTakeoffTimestamp(rs.getTimestamp("takeoff_timestamp"));
                    record.setPdfAbsolutePath(rs.getString("pdf_absolute_path"));

                    historyRecords.add(record);
                    logger.fine("Loaded record: ID=" + record.getId() +
                            ", Project=" + record.getProjectName() +
                            ", OriginalFile=" + record.getOriginalFileName() +
                            ", ProcessedFile=" + record.getProcessedFileName() +
                            ", Status=" + record.getStatus());
                }
            }
            logger.info("Retrieved " + historyRecords.size() + " history records for user ID: " + user.getId());
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error fetching takeoff history for user ID: " + user.getId(), e);
            return new ArrayList<>();
        }
        return historyRecords;
    }

    public List<TakeoffItem> getTakeoffItemsForRecord(int takeoffRecordId) {
        if (this.dbConnection == null) {
            logger.severe("Database connection is not available in getTakeoffItemsForRecord.");
            throw new IllegalStateException("Database connection not initialized.");
        }
        List<TakeoffItem> items = new ArrayList<>();
        String selectItemsSQL = "SELECT id, history_id, material, quantity, unit " +
                "FROM TAKEOFF_ITEMS WHERE history_id = ?";

        try (PreparedStatement pstmt = this.dbConnection.prepareStatement(selectItemsSQL)) {
            pstmt.setInt(1, takeoffRecordId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    TakeoffItem item = new TakeoffItem();
                    item.setId(rs.getInt("id"));
                    item.setTakeoffRecordId(rs.getInt("history_id"));
                    item.setMaterial(rs.getString("material"));
                    item.setQuantity(rs.getDouble("quantity"));
                    item.setUnit(rs.getString("unit"));
                    items.add(item);
                }
            }
            logger.info("Retrieved " + items.size() + " items for takeoff record ID: " + takeoffRecordId);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error fetching items for takeoff record ID: " + takeoffRecordId, e);
            throw new RuntimeException("Database error when fetching items for takeoff record ID: " + takeoffRecordId,
                    e);
        }
        return items;
    }

    public void closeConnection() {
        if (this.dbConnection != null) {
            try {
                if (!this.dbConnection.isClosed()) {
                    this.dbConnection.close();
                    logger.info("Database connection closed.");
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error closing database connection", e);
            } finally {
                this.dbConnection = null;
            }
        }
    }
    public Connection getConnection() {
        if (this.dbConnection == null) {
            logger.severe("Database connection is not available in getConnection.");
            throw new IllegalStateException("Database connection not initialized.");
        }
        return this.dbConnection;
    }

    public Optional<User> authenticateUser(String username, String password) {
        if (this.dbConnection == null) {
            logger.severe("Database connection is not available in authenticateUser.");
            throw new IllegalStateException("Database connection not initialized.");
        }

        String sql = "SELECT id, username, email, password_hash FROM USERS WHERE username = ?";
        try (PreparedStatement pstmt = this.dbConnection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    if (storedHash != null && BCrypt.checkpw(password, storedHash)) {
                        User user = new User();
                        user.setId(rs.getInt("id"));
                        user.setUsername(rs.getString("username"));
                        user.setEmail(rs.getString("email"));
                        return Optional.of(user);
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error authenticating user: " + username, e);
        }
        return Optional.empty();
    }

    public boolean registerUser(String username, String email, String password) {
        if (this.dbConnection == null) {
            logger.severe("Database connection is not available in registerUser.");
            throw new IllegalStateException("Database connection not initialized.");
        }

        String sql = "INSERT INTO USERS (username, email, password_hash) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = this.dbConnection.prepareStatement(sql)) {
            String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
            pstmt.setString(1, username);
            pstmt.setString(2, email);
            pstmt.setString(3, passwordHash);
            pstmt.executeUpdate();
            logger.info("User registered successfully: " + username);
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error registering user: " + username, e);
            return false;
        }
    }

    public boolean isUsernameTaken(String username) {
        if (this.dbConnection == null) {
            logger.severe("Database connection is not available in isUsernameTaken.");
            throw new IllegalStateException("Database connection not initialized.");
        }

        String sql = "SELECT COUNT(*) FROM USERS WHERE username = ?";
        try (PreparedStatement pstmt = this.dbConnection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error checking if username is taken: " + username, e);
            return false;
        }
    }

    public boolean isEmailTaken(String email) {
        if (this.dbConnection == null) {
            logger.severe("Database connection is not available in isEmailTaken.");
            throw new IllegalStateException("Database connection not initialized.");
        }

        String sql = "SELECT COUNT(*) FROM USERS WHERE email = ?";
        try (PreparedStatement pstmt = this.dbConnection.prepareStatement(sql)) {
            pstmt.setString(1, email);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error checking if email is taken: " + email, e);
            return false;
        }
    }
}
