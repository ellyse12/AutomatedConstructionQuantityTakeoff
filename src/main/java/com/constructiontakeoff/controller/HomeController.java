package com.constructiontakeoff.controller;

import com.constructiontakeoff.model.QuantityItem;
import com.constructiontakeoff.model.User;
import com.constructiontakeoff.util.DwgProcessor;
import com.constructiontakeoff.util.ExcelExporter;

import com.constructiontakeoff.util.DatabaseService;
import javafx.collections.ObservableList;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;

import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import com.constructiontakeoff.util.engine.TakeoffEngineRefactored;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.lang.ref.SoftReference;

import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.event.Event;

public class HomeController {
    private static final Logger logger = Logger.getLogger(HomeController.class.getName());
    @FXML
    private Label currentFileLabel;
    @FXML
    private Button takeoffButton;
    @FXML
    private VBox pdfViewer;
    private double zoomLevel = 1.0;
    @FXML
    private Label zoomLabel;
    @FXML
    private TabPane mainTabPane;
    private List<ImageView> pageViews = new ArrayList<>();
    private PDDocument currentDocument;
    private PDFRenderer pdfRenderer;
    private String currentPdfAbsolutePath;
    private int totalPages;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private final Map<Integer, SoftReference<Image>> pageCache = new ConcurrentHashMap<>();
    private ScrollPane scrollPane;
    private boolean scrollListenerAttached = false;
    private double lastWidth = 0;
    private TakeoffEngineRefactored takeoffEngine;

    @FXML
    private TableView<QuantityItem> resultsTable;
    @FXML
    private TableColumn<QuantityItem, String> materialColumn;
    @FXML
    private TableColumn<QuantityItem, Number> quantityColumn;
    @FXML
    private TableColumn<QuantityItem, String> unitColumn;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private BorderPane rootPane;

    private File currentDwgFile;
    private Path userWorkspace;
    private DwgProcessor dwgProcessor;
    private User currentUser;

    @FXML
    public void initialize() {
        takeoffEngine = TakeoffEngineRefactored.createDefault();

        ensureUserCreated();

        Platform.runLater(() -> {
            setupKeyboardShortcuts();
            setupWindowHandlers();
            setupTabChangeListener();
        });
    }

    private void ensureUserCreated() {
        try {
            String systemUsername = System.getProperty("user.name");
            DatabaseService databaseService = DatabaseService.getInstance();

            if (!databaseService.isUsernameTaken(systemUsername)) {

                databaseService.registerUser(systemUsername, systemUsername + "@example.com", "password");
                logger.info("Sistem kullanıcısı oluşturuldu: " + systemUsername);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Kullanıcı oluşturma sırasında hata", e);
        }
    }

    private void setupWindowHandlers() {
        Stage stage = (Stage) pdfViewer.getScene().getWindow();
        stage.setOnCloseRequest(event -> {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        });

        stage.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (Math.abs(lastWidth - newVal.doubleValue()) > 10) {
                lastWidth = newVal.doubleValue();
                updatePdfDisplay();
            }
        });
    }

    public void initData(User user) {
        this.currentUser = user;
        this.userWorkspace = Paths.get(System.getProperty("user.home"), "ConstructionTakeoff", user.getUsername());
        this.dwgProcessor = new DwgProcessor(userWorkspace);
        createUserWorkspace();
        initializeTable();

        if (mainTabPane.getSelectionModel().getSelectedIndex() == 1) {
            passUserToHistoryController();
        }
    }

    private void initializeTable() {
        materialColumn.setCellValueFactory(cellData -> cellData.getValue().materialProperty());
        quantityColumn.setCellValueFactory(cellData -> cellData.getValue().quantityProperty());
        unitColumn.setCellValueFactory(cellData -> cellData.getValue().unitProperty());

        quantityColumn.setCellFactory(column -> new TableCell<QuantityItem, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    String unit = "";
                    if (getTableRow() != null && getTableRow().getItem() != null) {
                        QuantityItem quantityItem = (QuantityItem) getTableRow().getItem();
                        unit = quantityItem.getUnit();
                    }

                    if (unit.equals("pcs")) {

                        setText(String.format("%,d", item.intValue()));
                    } else {

                        setText(String.format("%,.3f", item.doubleValue()));
                    }

                    setAlignment(Pos.CENTER_RIGHT);
                }
            }
        });
    }

    private void createUserWorkspace() {
        try {
            Files.createDirectories(userWorkspace);
        } catch (IOException e) {
            showError("Failed to create user workspace", e);
        }
    }

    @FXML
    private void handleOpenDwg() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open DWG File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("AutoCAD Files", "*.dwg"));

        File file = fileChooser.showOpenDialog(currentFileLabel.getScene().getWindow());
        if (file != null) {
            currentDwgFile = file;
            currentFileLabel.setText(file.getName());
            takeoffButton.setDisable(false);
            statusLabel.setText("DWG file loaded successfully");

            convertDwgToPdf(file);
        }
    }

    private void convertDwgToPdf(File dwgFile) {

        {

            javafx.application.Platform.runLater(() -> {
                progressBar.setVisible(true);
                statusLabel.setText("Converting DWG to PDF...");
            });

            dwgProcessor.convertToPdf(dwgFile)
                    .thenAcceptAsync(pdfFile -> {

                        if (pdfFile != null && pdfFile.exists()) {
                            this.currentPdfAbsolutePath = pdfFile.getAbsolutePath();
                            logger.info("PDF successfully created and path stored: " + this.currentPdfAbsolutePath);
                        } else {
                            this.currentPdfAbsolutePath = null;
                            logger.warning("PDF file was not created or does not exist.");
                        }
                        try {

                            clearCurrentDocument();
                            pageCache.clear();
                            pageViews.clear();

                            currentDocument = PDDocument.load(pdfFile);
                            pdfRenderer = new PDFRenderer(currentDocument);
                            totalPages = currentDocument.getNumberOfPages();

                            javafx.application.Platform.runLater(() -> {
                                try {

                                    pdfViewer.getChildren().clear();
                                    pageViews.clear();

                                    for (int i = 0; i < totalPages; i++) {
                                        VBox pageContainer = new VBox(5);
                                        pageContainer.setAlignment(Pos.CENTER);

                                        ImageView imageView = new ImageView();
                                        imageView.setPreserveRatio(true);
                                        imageView.setFitWidth(pdfViewer.getWidth() - 20);

                                        Label pageLabel = new Label(String.format("Page %d of %d", i + 1, totalPages));

                                        pageContainer.getChildren().addAll(imageView, pageLabel);
                                        pageViews.add(imageView);
                                        pdfViewer.getChildren().add(pageContainer);
                                    }

                                    Parent parent = pdfViewer.getParent();
                                    while (parent != null && !(parent instanceof ScrollPane)) {
                                        parent = parent.getParent();
                                    }

                                    if (parent instanceof ScrollPane) {
                                        scrollPane = (ScrollPane) parent;
                                        if (!scrollListenerAttached) {
                                            scrollPane.vvalueProperty()
                                                    .addListener((obs, oldVal, newVal) -> loadVisiblePages());
                                            scrollListenerAttached = true;
                                        }

                                        scrollPane.addEventFilter(ZoomEvent.ANY, Event::consume);

                                        scrollPane.addEventFilter(ScrollEvent.SCROLL, evt -> {
                                            if (evt.isControlDown())
                                                evt.consume();
                                        });
                                    }

                                    if (totalPages > 0) {
                                        loadPage(0);
                                        if (totalPages > 1) {
                                            executorService.submit(() -> loadPage(1));
                                        }
                                    }

                                    statusLabel.setText("PDF loaded successfully");
                                    progressBar.setVisible(false);
                                } catch (Exception e) {
                                    showError("Error initializing PDF viewer", e);
                                    progressBar.setVisible(false);
                                }
                            });
                        } catch (Exception e) {
                            javafx.application.Platform.runLater(() -> {
                                showError("Error loading PDF", e);
                                progressBar.setVisible(false);
                            });
                        }
                    })
                    .exceptionally(e -> {
                        javafx.application.Platform.runLater(() -> {
                            showError("Failed to convert DWG to PDF", e);
                            progressBar.setVisible(false);
                        });
                        return null;
                    });
        }
    }

    private void clearCurrentDocument() {
        javafx.application.Platform.runLater(() -> {
            pdfViewer.getChildren().clear();
            pageViews.clear();
        });

        if (currentDocument != null) {
            try {
                currentDocument.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            currentDocument = null;
            pdfRenderer = null;
        }
    }

    private void loadVisiblePages() {
        if (currentDocument == null)
            return;

        if (scrollPane == null) {
            Parent parent = pdfViewer.getParent();
            while (parent != null && !(parent instanceof ScrollPane)) {
                parent = parent.getParent();
            }

            if (parent instanceof ScrollPane) {
                scrollPane = (ScrollPane) parent;
                if (!scrollListenerAttached) {
                    scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> loadVisiblePages());
                    scrollListenerAttached = true;
                }
            } else {
                return;
            }
        }

        double viewportHeight = scrollPane.getViewportBounds().getHeight();
        double scrollY = scrollPane.getVvalue() * (pdfViewer.getHeight() - viewportHeight);
        double accumulatedHeight = 0;

        List<Double> pageHeights = new ArrayList<>();
        for (ImageView pageView : pageViews) {
            pageHeights.add(pageView.getBoundsInParent().getHeight());
        }

        int firstVisible = -1;
        int lastVisible = -1;
        for (int i = 0; i < pageHeights.size(); i++) {
            double pageHeight = pageHeights.get(i);

            if (firstVisible == -1 &&
                    accumulatedHeight + pageHeight >= scrollY - viewportHeight) {
                firstVisible = i;
            }

            if (lastVisible == -1 &&
                    accumulatedHeight >= scrollY + viewportHeight * 1.5) {
                lastVisible = i;
                break;
            }

            accumulatedHeight += pageHeight + 10;
        }

        if (lastVisible == -1)
            lastVisible = pageHeights.size() - 1;

        for (int i = Math.max(0, firstVisible - 1); i <= Math.min(lastVisible + 1, pageViews.size() - 1); i++) {
            loadPage(i);
        }
    }

    private void loadPage(int pageIndex) {
        if (pageIndex >= totalPages || pageIndex < 0 || pageViews.isEmpty())
            return;

        SoftReference<Image> cachedImageRef = pageCache.get(pageIndex);
        Image cachedImage = cachedImageRef != null ? cachedImageRef.get() : null;

        if (cachedImage != null && !cachedImage.isError()) {
            Platform.runLater(() -> {
                try {
                    if (pageIndex < pageViews.size()) {
                        ImageView imageView = pageViews.get(pageIndex);
                        imageView.setImage(cachedImage);
                        double viewerWidth = pdfViewer.getWidth() - 30;
                        imageView.setFitWidth(viewerWidth * zoomLevel);
                        imageView.setPreserveRatio(true);
                        imageView.setSmooth(true);
                        imageView.setCache(true);
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error displaying cached page " + (pageIndex + 1), e);
                }
            });
        } else {
            executorService.submit(() -> {
                try {
                    if (pdfRenderer == null || pageIndex >= totalPages)
                        return;

                    BufferedImage bImage = pdfRenderer.renderImageWithDPI(pageIndex, 200, ImageType.RGB);

                    if (bImage == null) {
                        logger.warning("Failed to render PDF page " + pageIndex + ": null image returned");
                        return;
                    }

                    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                        boolean success = ImageIO.write(bImage, "png", output);
                        if (!success) {
                            logger.warning("No appropriate writer found for PNG format");
                            return;
                        }

                        byte[] imageData = output.toByteArray();
                        if (imageData == null || imageData.length == 0) {
                            logger.warning("Empty image data generated for page " + pageIndex);
                            return;
                        }

                        try (ByteArrayInputStream input = new ByteArrayInputStream(imageData)) {
                            Image fxImage = new Image(input, 0, 0, true, true);

                            if (fxImage.isError()) {
                                logger.warning("Error loading JavaFX image for page " + pageIndex);
                                return;
                            }

                            pageCache.put(pageIndex, new SoftReference<>(fxImage));

                            Platform.runLater(() -> {
                                try {
                                    if (pageIndex < pageViews.size()) {
                                        ImageView imageView = pageViews.get(pageIndex);
                                        imageView.setImage(fxImage);
                                        double viewerWidth = pdfViewer.getWidth() - 30;
                                        imageView.setFitWidth(viewerWidth * zoomLevel);
                                        imageView.setPreserveRatio(true);
                                        imageView.setSmooth(true);
                                        imageView.setCache(true);
                                    }
                                } catch (Exception e) {
                                    logger.log(Level.WARNING, "Error displaying page " + (pageIndex + 1), e);
                                }
                            });
                        }
                    }

                    bImage.flush();
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error loading page " + (pageIndex + 1), e);
                    Platform.runLater(() -> {
                        if (pageIndex < pageViews.size()) {
                            ImageView imageView = pageViews.get(pageIndex);
                            imageView.setImage(null);
                        }
                    });
                }
            });
        }
    }

    @FXML
    private void handleStartTakeoff() {
        resultsTable.getItems().clear();

        if (currentDwgFile == null) {
            showError("No DWG file selected", null);
            return;
        }

        Platform.runLater(() -> {
            progressBar.setVisible(true);
            statusLabel.setText("Converting DWG to DXF...");
        });

        dwgProcessor.convertToDxf(currentDwgFile)
                .thenApply(dxfFile -> {
                    if (dxfFile == null || !dxfFile.exists()) {
                        throw new RuntimeException("DXF conversion failed - output file not found");
                    }
                    Platform.runLater(() -> statusLabel.setText("DXF conversion complete, processing takeoff..."));
                    return dxfFile;
                })
                .thenCompose(dxfFile -> {
                    Platform.runLater(() -> {
                        statusLabel.setText("Calculating quantities and saving to history...");
                    });

                    logger.info("Processing DXF with authenticated user: "
                            + (currentUser != null ? currentUser.getUsername() : "null") + " and PDF path: "
                            + this.currentPdfAbsolutePath);

                    return takeoffEngine.processDxf(dxfFile, currentUser, this.currentPdfAbsolutePath);
                })
                .thenAcceptAsync(result -> {
                    Platform.runLater(() -> {

                        if (mainTabPane.getTabs().size() > 1) {
                            Tab historyTab = mainTabPane.getTabs().get(1);
                            Node content = historyTab.getContent();
                            if (content != null && content.getUserData() instanceof HistoryViewController) {
                                HistoryViewController localHistoryViewController = (HistoryViewController) content
                                        .getUserData();
                                if (this.currentUser != null) {
                                    logger.info("Ensuring HistoryViewController has latest user: "
                                            + this.currentUser.getUsername() + " (ID: " + this.currentUser.getId()
                                            + ") before refresh.");
                                    localHistoryViewController.setCurrentUser(this.currentUser);
                                    localHistoryViewController.refreshHistory();
                                    logger.info("History view refreshed after successful takeoff with updated user.");
                                } else {
                                    logger.warning("CurrentUser is null when trying to update HistoryViewController.");
                                }
                            } else {
                                logger.warning(
                                        "Could not get HistoryViewController from tab userData or content is null.");
                            }
                        } else {
                            logger.warning("History tab not found or not enough tabs.");
                        }

                        @SuppressWarnings("unchecked")
                        ObservableList<QuantityItem> items = (ObservableList<QuantityItem>) result.get("items");

                        resultsTable.setItems(items);
                        progressBar.setVisible(false);
                        statusLabel.setText("Takeoff complete. Results saved to history.");
                        showInfo("Takeoff processed and saved successfully!");
                    });
                })
                .exceptionally(throwable -> {
                    Throwable cause = throwable.getCause();
                    String errorMessage = cause != null ? cause.getMessage() : throwable.getMessage();
                    Platform.runLater(() -> {
                        showError("Failed to process takeoff: " + errorMessage, throwable);
                        statusLabel.setText("Takeoff failed");
                        progressBar.setVisible(false);
                    });
                    return null;
                });
    }

    @FXML
    private void handleExportToExcel() {
        if (resultsTable.getItems().isEmpty()) {
            showError("No data to export", null);
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to Excel");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));

        File file = fileChooser.showSaveDialog(currentFileLabel.getScene().getWindow());
        if (file != null) {
            try {
                ExcelExporter.exportToExcel(resultsTable.getItems(), file);
                statusLabel.setText("Export complete: " + file.getName());
            } catch (IOException e) {
                showError("Failed to export to Excel", e);
            }
        }
    }

    @FXML
    private void handleViewReports() {
        mainTabPane.getSelectionModel().select(1);
    }

    private void setupTabChangeListener() {
        mainTabPane.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.intValue() == 1) {
                passUserToHistoryController();
            }
        });
    }

    private void passUserToHistoryController() {
        if (currentUser == null) {
            logger.warning("Cannot pass user to HistoryViewController: currentUser is null");
            return;
        }

        try {
            Tab historyTab = mainTabPane.getTabs().get(1);
            if (historyTab == null) {
                logger.warning("History tab not found");
                return;
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/constructiontakeoff/HistoryView.fxml"));
            Parent historyView = loader.load();
            HistoryViewController historyController = loader.getController();

            if (historyController != null) {
                logger.info("Passing user " + currentUser.getUsername() + " to HistoryViewController");
                historyController.setCurrentUser(currentUser);

                historyView.setUserData(historyController);

                historyView.getStyleClass().add("history-view");

                historyTab.setContent(historyView);
            } else {
                logger.warning("HistoryViewController not found in loaded FXML");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error passing user to HistoryViewController", e);
        }
    }

    private void showError(String header, Throwable error) {
        String message = error != null ? error.getMessage() : "Unknown error";
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void handleLogout() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/constructiontakeoff/view/login.fxml"));
            Stage stage = (Stage) currentFileLabel.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Construction Takeoff - Login");
            stage.show();
        } catch (IOException e) {
            showError("Error returning to login screen", e);
        }
    }

    private void updateZoom(double newZoom) {
        if (scrollPane == null) {
            Parent parent = pdfViewer.getParent();
            while (parent != null && !(parent instanceof ScrollPane)) {
                parent = parent.getParent();
            }
            if (parent instanceof ScrollPane) {
                scrollPane = (ScrollPane) parent;
            }
        }

        double oldVvalue = scrollPane != null ? scrollPane.getVvalue() : 0.0;
        double oldHvalue = scrollPane != null ? scrollPane.getHvalue() : 0.0;

        double oldZoom = zoomLevel;
        zoomLevel = Math.max(0.2, Math.min(3.0, newZoom));

        if (Math.abs(oldZoom - zoomLevel) > 0.01) {
            updatePdfDisplay();
            zoomLabel.setText(String.format("%.0f%%", zoomLevel * 100));

            if (scrollPane != null) {
                Platform.runLater(() -> {
                    scrollPane.setVvalue(oldVvalue);
                    scrollPane.setHvalue(oldHvalue);
                });
            }
        }
    }

    private void updatePdfDisplay() {
        if (pdfViewer.getWidth() <= 0) {
            Platform.runLater(this::updatePdfDisplay);
            return;
        }

        double viewerWidth = pdfViewer.getWidth() - 30;

        for (ImageView imageView : pageViews) {

            imageView.setFitWidth(viewerWidth * zoomLevel);
            imageView.setPreserveRatio(true);

            imageView.setSmooth(true);
            imageView.setCache(true);
        }

        pdfViewer.layout();
    }

    @FXML
    private void handleZoomIn() {
        updateZoom(zoomLevel + 0.25);
    }

    @FXML
    private void handleZoomOut() {
        updateZoom(zoomLevel * 0.75);
    }

    @FXML
    private void handleFitToWidth() {
        loadVisiblePages();
        if (!pageViews.isEmpty()) {
            ImageView firstView = pageViews.get(0);
            Image img = firstView.getImage();
            if (img != null) {
                double viewerWidth = pdfViewer.getWidth() - 20;
                double newZoom = viewerWidth / img.getWidth();
                updateZoom(newZoom);
            } else {

                javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(
                        javafx.util.Duration.millis(200));
                pause.setOnFinished(e -> handleFitToWidth());
                pause.play();
            }
        }
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void setupKeyboardShortcuts() {
        Scene scene = rootPane.getScene();
        scene.setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                switch (event.getCode()) {
                    case O:
                        handleOpenDwg();
                        break;
                    case E:
                        handleExportToExcel();
                        break;
                    case EQUALS:
                        handleZoomIn();
                        break;
                    case MINUS:
                        handleZoomOut();
                        break;
                    default:
                        break;
                }
            }
        });
    }

}
