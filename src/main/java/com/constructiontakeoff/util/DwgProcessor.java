package com.constructiontakeoff.util;

import com.aspose.cad.Image;
import com.aspose.cad.imageoptions.CadRasterizationOptions;
import com.aspose.cad.imageoptions.PdfOptions;
import com.aspose.cad.imageoptions.DxfOptions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.logging.Level;

public class DwgProcessor {
    private final Path workspacePath;
    private int pageWidth = 1600;
    private int pageHeight = 1200;
    private String[] layouts = { "Model" };
    private static final Logger logger = Logger.getLogger(DwgProcessor.class.getName());

    public DwgProcessor(Path workspacePath) {
        this.workspacePath = workspacePath;
    }

    public void setRasterizationOptions(int pageWidth, int pageHeight, String[] layouts) {
        this.pageWidth = pageWidth;
        this.pageHeight = pageHeight;
        this.layouts = layouts;
    }

    public CompletableFuture<File> convertToPdf(File dwgFile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(workspacePath.resolve("pdf"));
                String fileName = dwgFile.getName().replaceFirst("[.][^.]+$", "");
                Path outputPath = workspacePath.resolve("pdf").resolve(fileName + ".pdf");

                Image cadImage = Image.load(dwgFile.getAbsolutePath());

                PdfOptions pdfOptions = new PdfOptions();
                CadRasterizationOptions rasterizationOptions = new CadRasterizationOptions();
                rasterizationOptions.setPageWidth(pageWidth);
                rasterizationOptions.setPageHeight(pageHeight);
                rasterizationOptions.setLayouts(layouts);
                pdfOptions.setVectorRasterizationOptions(rasterizationOptions);
                logger.info(String.format("Converting DWG to PDF with dimensions %dx%d", pageWidth, pageHeight));

                cadImage.save(outputPath.toString(), pdfOptions);

                File pdfFile = outputPath.toFile();
                if (!pdfFile.exists()) {
                    throw new IOException("PDF file was not created");
                }

                return pdfFile;
            } catch (IOException e) {
                logger.log(Level.SEVERE, "I/O error during DWG to PDF conversion", e);
                throw new RuntimeException("Failed to convert DWG to PDF: I/O error - " + e.getMessage(), e);
            } catch (Throwable e) {
                StringBuilder fullErrorMsg = new StringBuilder();
                Throwable current = e;
                while (current != null) {
                    if (current.getMessage() != null) {
                        fullErrorMsg.append(current.getMessage()).append(" ");
                    }
                    current = current.getCause();
                }

                String errorText = fullErrorMsg.toString().toLowerCase();
                if (errorText.contains("another process") ||
                        errorText.contains("işlem") ||
                        errorText.contains("locked") ||
                        errorText.contains("access") ||
                        errorText.contains("erişemiyor")) {

                    logger.log(Level.SEVERE, "DWG file locked or inaccessible", e);
                    throw new RuntimeException(
                            "Unable to open DWG file—it may be locked by another application. Please close it and try again.",
                            e);
                }

                logger.log(Level.SEVERE, "Unexpected error during DWG to PDF conversion", e);
                throw new RuntimeException(
                        "Failed to convert DWG to PDF: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"),
                        e);
            }
        });
    }

    public CompletableFuture<File> convertToDxf(File dwgFile) {
        logger.info("Starting DWG to DXF conversion for: " + dwgFile.getName());
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!dwgFile.exists()) {
                    throw new FileNotFoundException("DWG file does not exist: " + dwgFile.getAbsolutePath());
                }

                if (!dwgFile.getName().toLowerCase().endsWith(".dwg")) {
                    logger.warning("File does not have .dwg extension: " + dwgFile.getName());
                }

                if (dwgFile.length() == 0) {
                    throw new IOException("DWG file is empty: " + dwgFile.getAbsolutePath());
                }

                Files.createDirectories(workspacePath.resolve("dxf"));
                String fileName = dwgFile.getName().replaceFirst("[.][^.]+$", "");
                Path outputPath = workspacePath.resolve("dxf").resolve(fileName + ".dxf");

                Image cadImage = Image.load(dwgFile.getAbsolutePath());

                DxfOptions dxfOptions = new DxfOptions();

                cadImage.save(outputPath.toString(), dxfOptions);

                File dxfFile = outputPath.toFile();
                if (!dxfFile.exists()) {
                    throw new IOException("DXF file was not created: " + outputPath);
                }

                logger.info("DWG to DXF conversion completed successfully: " + dxfFile.getName());
                return dxfFile;

            } catch (FileNotFoundException e) {
                logger.severe("DWG file not found: " + e.getMessage());
                throw new RuntimeException("DWG file not found: " + e.getMessage(), e);
            } catch (IOException e) {
                logger.severe("I/O error during DWG to DXF conversion: " + e.getMessage());
                throw new RuntimeException("Failed to convert DWG to DXF: I/O error - " + e.getMessage(), e);
            } catch (Exception e) {
                logger.severe("Unexpected error during DWG to DXF conversion: " + e.getMessage());
                throw new RuntimeException("Failed to convert DWG to DXF: " + e.getMessage(), e);
            }
        });
    }

}
