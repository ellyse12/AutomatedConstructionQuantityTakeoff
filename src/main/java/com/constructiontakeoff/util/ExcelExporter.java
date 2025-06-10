package com.constructiontakeoff.util;

import com.constructiontakeoff.model.QuantityItem;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;

public class ExcelExporter {
    private static final Logger logger = Logger.getLogger(ExcelExporter.class.getName());

    public static void exportToExcel(List<QuantityItem> items, File file) throws IOException {
        logger.info("Exporting " + items.size() + " items to Excel file: " + file.getAbsolutePath());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Takeoff Results");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle integerStyle = workbook.createCellStyle();
            integerStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            integerStyle.setAlignment(HorizontalAlignment.RIGHT);

            CellStyle decimalStyle = workbook.createCellStyle();
            decimalStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.000"));
            decimalStyle.setAlignment(HorizontalAlignment.RIGHT);

            Row headerRow = sheet.createRow(0);
            String[] headers = { "Layer/Material", "Quantity", "Unit" };
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (QuantityItem item : items) {
                Row row = sheet.createRow(rowNum++);
                Cell materialCell = row.createCell(0);
                materialCell.setCellValue(item.getMaterial());

                Cell quantityCell = row.createCell(1);
                String unit = item.getUnit();
                double quantity = item.getQuantity();

                if ("pcs".equals(unit)) {
                    quantityCell.setCellValue((int) quantity);
                    quantityCell.setCellStyle(integerStyle);
                } else {
                    quantityCell.setCellValue(quantity);
                    quantityCell.setCellStyle(decimalStyle);
                }

                Cell unitCell = row.createCell(2);
                unitCell.setCellValue(unit);
            }

            Row timestampRow = sheet.createRow(rowNum + 1);
            Cell timestampCell = timestampRow.createCell(0);
            timestampCell.setCellValue("Generated: " +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                workbook.write(outputStream);
                logger.info("Successfully exported takeoff results to: " + file.getAbsolutePath());
            }
        } catch (Exception e) {
            logger.severe("Error exporting to Excel: " + e.getMessage());
            throw e;
        }
    }
}
