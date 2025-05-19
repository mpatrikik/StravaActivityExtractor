import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public class ExcelExporter {

    private static final String JSON_FILE = "src/main/resources/activities.json";
    private static final String OUTPUT_EXCEL = "C:\\Users\\mpatr\\Desktop\\connect_strava_tempus.xlsx";
    private static final String START_CELL = "I3";

    public static void main(String[] args) throws IOException {
        JSONArray activities = loadActivitiesFromFile();
        writeToExcel(activities, OUTPUT_EXCEL, START_CELL);
        System.out.println("✅ Export complete: " + OUTPUT_EXCEL);
    }

    private static JSONArray loadActivitiesFromFile() {
        File file = Paths.get(JSON_FILE).toFile();
        if (!file.exists()) return new JSONArray();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder jsonText = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonText.append(line);
            }
            return new JSONArray(jsonText.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return new JSONArray();
        }
    }

    private static void writeToExcel(JSONArray activities, String filePath, String startCell) throws IOException {
        int startCol = startCell.charAt(0) - 'A';
        int startRow = Integer.parseInt(startCell.substring(1)) - 1;

        FileInputStream fis = new FileInputStream(filePath);
        Workbook workbook = new XSSFWorkbook(fis);
        fis.close();

        Sheet sheet = workbook.getSheetAt(0);

        CellStyle dateStyle = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        short dateFormat = createHelper.createDataFormat().getFormat("yyyy.MM.dd");
        dateStyle.setDataFormat(dateFormat);

        for (int i = 0; i < activities.length(); i++) {
            JSONObject activity = activities.getJSONObject(i);
            Row row = sheet.getRow(startRow + i);
            if (row == null) {
                row = sheet.createRow(startRow + i);
            }

            // 1. cella (I): név
            Cell nameCell = row.getCell(startCol);
            if (nameCell == null) nameCell = row.createCell(startCol);
            nameCell.setCellValue(activity.getString("name"));

            // 2. cella (J): dátum
            Cell dateCell = row.getCell(startCol + 1);
            if (dateCell == null) dateCell = row.createCell(startCol + 1);
            LocalDate localDate = Instant.parse(activity.getString("start_date"))
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate();
            dateCell.setCellValue(localDate);
            dateCell.setCellStyle(dateStyle);

            Cell distCell = row.getCell(startCol + 2);
            if (distCell == null) distCell = row.createCell(startCol + 2);
            double distInKm = activity.getDouble("distance") / 1000.0;
            distCell.setCellValue(Math.round(distInKm * 100.0) / 100.0);
        }

        for (int i = 0; i < 3; i++) {
            sheet.autoSizeColumn(startCol + i);
        }

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            workbook.write(fos);
        }
        workbook.close();
    }


}
