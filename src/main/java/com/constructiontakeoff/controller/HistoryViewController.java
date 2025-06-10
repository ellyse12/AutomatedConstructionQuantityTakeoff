package com.constructiontakeoff.controller;

import com.constructiontakeoff.model.TakeoffItem;
import com.constructiontakeoff.model.TakeoffRecord;
import com.constructiontakeoff.model.User;
import com.constructiontakeoff.util.DatabaseService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.sql.Timestamp;
import java.util.List;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

import java.util.logging.Level;
import java.util.logging.Logger;

public class HistoryViewController {

    private static final Logger logger = Logger.getLogger(HistoryViewController.class.getName());

    @FXML
    private TableView<TakeoffRecord> historyTable;

    private DatabaseService databaseService;

    @FXML
    private TableColumn<TakeoffRecord, String> projectNameColumn;
    @FXML
    private TableColumn<TakeoffRecord, String> fileNameColumn;
    @FXML
    private TableColumn<TakeoffRecord, Timestamp> dateColumn;
    @FXML
    private TableColumn<TakeoffRecord, String> pdfPreviewColumn;

    @FXML
    private TableView<TakeoffItem> historyItemsTable;
    @FXML
    private TableColumn<TakeoffItem, String> materialColumn;
    @FXML
    private TableColumn<TakeoffItem, Double> quantityColumn;
    @FXML
    private TableColumn<TakeoffItem, String> unitColumn;

    @FXML
    private Button downloadExcelButton;

    private User currentUser;

    public HistoryViewController() {

    }

    @FXML
    public void initialize() {
        try {

            this.databaseService = DatabaseService.getInstance();

            projectNameColumn.setCellValueFactory(new PropertyValueFactory<>("projectName"));
            fileNameColumn.setCellValueFactory(new PropertyValueFactory<>("originalFileName"));
            dateColumn.setCellValueFactory(new PropertyValueFactory<>("takeoffTimestamp"));

            pdfPreviewColumn.setCellFactory(createPdfPreviewCellFactory());

            materialColumn.setCellValueFactory(new PropertyValueFactory<>("material"));
            quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
            unitColumn.setCellValueFactory(new PropertyValueFactory<>("unit"));

            quantityColumn.setCellFactory(column -> new TableCell<TakeoffItem, Double>() {
                @Override
                protected void updateItem(Double item, boolean empty) {
                    super.updateItem(item, empty);

                    if (item == null || empty) {
                        setText(null);
                        setStyle("");
                    } else {
                        String unit = "";
                        if (getTableRow() != null && getTableRow().getItem() != null) {
                            TakeoffItem takeoffItem = (TakeoffItem) getTableRow().getItem();
                            unit = takeoffItem.getUnit();
                        }

                        if (unit.equals("pcs")) {
                            setText(String.format("%,d", item.intValue()));
                        } else {
                            setText(String.format("%,.3f", item));
                        }

                        setStyle("-fx-alignment: CENTER-RIGHT;");
                    }
                }
            });

            setupSelectionListener();

            downloadExcelButton.setDisable(true);

            if (currentUser != null) {
                loadTakeoffHistory();
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error initializing HistoryViewController", e);
            showAlert(Alert.AlertType.ERROR, "Initialization Error",
                    "Failed to initialize the history view: " + e.getMessage());
        }
    }

    private Callback<TableColumn<TakeoffRecord, String>, TableCell<TakeoffRecord, String>> createPdfPreviewCellFactory() {
        return new Callback<>() {
            @Override
            public TableCell<TakeoffRecord, String> call(final TableColumn<TakeoffRecord, String> param) {
                return new TableCell<>() {
                    private final Button btn = new Button("Preview");

                    {
                        btn.setOnAction(event -> {
                            TakeoffRecord data = getTableView().getItems().get(getIndex());
                            handlePdfPreview(data);
                        });
                    }

                    @Override
                    public void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setGraphic(null);
                        } else {
                            TakeoffRecord record = getTableView().getItems().get(getIndex());

                            boolean hasPdf = record.getProcessedFileName() != null
                                    && !record.getProcessedFileName().isEmpty();
                            btn.setDisable(!hasPdf);
                            setGraphic(btn);
                        }
                    }
                };
            }
        };
    }

    private void handlePdfPreview(TakeoffRecord record) {
        if (record == null) {
            logger.warning("Cannot preview PDF: record is null");
            return;
        }

        String pdfPath = record.getPdfAbsolutePath();
        if (pdfPath == null || pdfPath.isEmpty()) {
            logger.info("No PDF path available for record: " + record.getId());
            showAlert(AlertType.INFORMATION, "No PDF Available", "No PDF is available for this record.");
            return;
        }

        try {
            File pdfFile = new File(pdfPath);
            if (!pdfFile.exists()) {
                logger.warning("PDF file not found at path: " + pdfPath);
                showAlert(AlertType.ERROR, "File Not Found",
                        "The PDF file could not be found at: " + pdfPath);
                return;
            }

            Stage pdfStage = new Stage();
            pdfStage.setTitle("PDF Preview: " + record.getOriginalFileName());

            WebView webView = new WebView();
            WebEngine webEngine = webView.getEngine();

            String fileUrl = pdfFile.toURI().toURL().toExternalForm();

            try {
                Desktop.getDesktop().open(pdfFile);
                logger.info("Opened PDF in default viewer: " + pdfPath);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Could not open PDF in default viewer, falling back to WebView", e);

                String googleDocsUrl = "https://docs.google.com/viewer?url=" +
                        URLEncoder.encode(fileUrl, "UTF-8") + "&embedded=true";
                webEngine.load(googleDocsUrl);

                Scene scene = new Scene(webView, 900, 700);
                pdfStage.setScene(scene);
                pdfStage.show();

                logger.info("PDF preview opened in WebView for record ID: " + record.getId());
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error opening PDF preview for record ID: " + record.getId(), e);
            showAlert(AlertType.ERROR, "Error",
                    "Could not open PDF preview: " + e.getMessage());
        }
    }

    public void setCurrentUser(User user) {
        try {
            logger.info("Setting current user: " + (user != null ? user.getUsername() : "null"));
            this.currentUser = user;
            if (this.currentUser != null) {
                logger.info("Current user set successfully: " + this.currentUser.getUsername() + " (ID: "
                        + this.currentUser.getId() + ")");
                loadTakeoffHistory();
            } else {
                logger.severe("Cannot set current user: user object is null");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error setting current user.", e);
        }
    }

    public void refreshHistory() {
        logger.info("Refreshing takeoff history...");
        loadTakeoffHistory();
    }

    private void loadTakeoffHistory() {
        logger.info("Attempting to load takeoff history.");
        if (currentUser == null) {
            logger.warning("Cannot load takeoff history, current user is null. History table will be empty.");
            historyTable.setItems(FXCollections.emptyObservableList());
            showAlert(Alert.AlertType.WARNING, "No User", "No user is currently logged in. History is not available.");
            return;
        }

        if (currentUser.getId() <= 0) {
            logger.warning("Invalid user ID: " + currentUser.getId() + " for user: " + currentUser.getUsername());
            showAlert(Alert.AlertType.ERROR, "Error", "Invalid user session. Please log in again.");
            return;
        }

        logger.info("Loading takeoff history for user: " + currentUser.getUsername() + " (ID: " + currentUser.getId()
                + ")");
        try {
            historyTable.getSelectionModel().clearSelection();

            historyItemsTable.setItems(FXCollections.emptyObservableList());

            List<TakeoffRecord> records = databaseService.getTakeoffHistoryForUser(currentUser);

            if (records == null) {
                logger.warning("getTakeoffHistoryForUser returned null for user: " + currentUser.getUsername());
                showAlert(Alert.AlertType.ERROR, "Error", "Failed to load history: No data returned from database.");
                return;
            }

            logger.info(
                    "Retrieved " + records.size() + " records from database for user: " + currentUser.getUsername());

            ObservableList<TakeoffRecord> observableRecords = FXCollections.observableArrayList(records);
            historyTable.setItems(observableRecords);

            for (TakeoffRecord record : records) {
                logger.fine(String.format("Record - ID: %d, Project: %s, File: %s, Timestamp: %s",
                        record.getId(),
                        record.getProjectName(),
                        record.getOriginalFileName(),
                        record.getTakeoffTimestamp()));
            }

            if (!records.isEmpty()) {
                historyTable.getSelectionModel().selectFirst();
                logger.info("Selected first record in the history table");
            } else {
                logger.info("No history records found for user: " + currentUser.getUsername());
                showAlert(Alert.AlertType.INFORMATION, "No History", "No takeoff history found for the current user.");
            }

        } catch (Exception e) {
            String errorMsg = "Error loading takeoff history for user: " + currentUser.getUsername();
            logger.log(Level.SEVERE, errorMsg, e);

            showAlert(Alert.AlertType.ERROR, "Error", errorMsg + ": " + e.getMessage());

            historyTable.setItems(FXCollections.emptyObservableList());
        }
    }

    private void setupSelectionListener() {
        historyTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                logger.info("Selected record: " + newSelection.getOriginalFileName());
                loadTakeoffItems(newSelection);
                downloadExcelButton.setDisable(false);
            } else {
                logger.info("History record selection cleared.");
                historyItemsTable.setItems(FXCollections.emptyObservableList());
                downloadExcelButton.setDisable(true);
            }
        });
    }

    private void loadTakeoffItems(TakeoffRecord selectedRecord) {
        if (selectedRecord == null) {
            logger.warning("loadTakeoffItems called with null selectedRecord.");
            historyItemsTable.setItems(FXCollections.emptyObservableList());
            return;
        }

        int recordId = selectedRecord.getId();
        String projectName = selectedRecord.getProjectName();

        logger.info("Loading items for selected record ID: " + recordId + ", Project: " + projectName);

        try {
            historyItemsTable.setItems(FXCollections.emptyObservableList());

            List<TakeoffItem> items = databaseService.getTakeoffItemsForRecord(recordId);

            if (items == null) {
                String errorMsg = "getTakeoffItemsForRecord returned null for record ID: " + recordId;
                logger.warning(errorMsg);
                showAlert(Alert.AlertType.ERROR, "Error", "Failed to load items: " + errorMsg);
                return;
            }

            logger.info("Retrieved " + items.size() + " items for record ID: " + recordId);

            for (TakeoffItem item : items) {
                logger.fine(String.format("Item - ID: %d, Material: %s, Quantity: %f %s",
                        item.getId(),
                        item.getMaterial(),
                        item.getQuantity(),
                        item.getUnit()));
            }

            ObservableList<TakeoffItem> observableItems = FXCollections.observableArrayList(items);
            historyItemsTable.setItems(observableItems);

            historyItemsTable.getColumns().forEach(column -> {
                if (column instanceof TableColumn) {
                    ((TableColumn<?, ?>) column).prefWidthProperty().bind(
                            historyItemsTable.widthProperty()
                                    .subtract(2)
                                    .multiply(0.33));
                }
            });

            downloadExcelButton.setDisable(items.isEmpty());

        } catch (Exception e) {
            String errorMsg = "Error loading items for record ID: " + recordId;
            logger.log(Level.SEVERE, errorMsg, e);

            showAlert(Alert.AlertType.ERROR, "Error", errorMsg + ": " + e.getMessage());

            historyItemsTable.setItems(FXCollections.emptyObservableList());
            downloadExcelButton.setDisable(true);
        }
    }

    private void showAlert(AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    private void handleDownloadExcelAction() {
        TakeoffRecord selectedRecord = historyTable.getSelectionModel().getSelectedItem();
        if (selectedRecord != null) {
            logger.info("Download Excel action triggered for record ID: " + selectedRecord.getId() + ", Project: "
                    + selectedRecord.getProjectName());

            List<TakeoffItem> items = databaseService.getTakeoffItemsForRecord(selectedRecord.getId());
            if (items == null || items.isEmpty()) {
                logger.warning("No items found for record ID: " + selectedRecord.getId() + ". Excel export aborted.");

                return;
            }

            XSSFWorkbook workbook = new XSSFWorkbook();
            XSSFSheet sheet = workbook
                    .createSheet(selectedRecord.getProjectName().replaceAll("[^a-zA-Z0-9_\\.-]", "_"));

            Row headerRow = sheet.createRow(0);
            String[] headers = { "Material", "Quantity", "Unit" };
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            int rowNum = 1;
            for (TakeoffItem item : items) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.getMaterial());
                row.createCell(1).setCellValue(item.getQuantity());
                row.createCell(2).setCellValue(item.getUnit());
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Takeoff Report");
            String defaultFileName = (selectedRecord.getProjectName() + "_Takeoff_Report.xlsx")
                    .replaceAll("[^a-zA-Z0-9_\\.-]", "_");
            fileChooser.setInitialFileName(defaultFileName);
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook (*.xlsx)", "*.xlsx"));

            Stage stage = (Stage) downloadExcelButton.getScene().getWindow();
            File file = fileChooser.showSaveDialog(stage);

            if (file != null) {
                try (FileOutputStream outputStream = new FileOutputStream(file)) {
                    workbook.write(outputStream);
                    logger.info("Excel report saved to: " + file.getAbsolutePath());
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Error writing Excel file for record ID: " + selectedRecord.getId(), e);
                }
            } else {
                logger.info("Excel export cancelled by user.");
            }

            try {
                workbook.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error closing workbook.", e);
            }

        } else {
            logger.warning("Download Excel action triggered but no record selected.");
        }
    }
}
