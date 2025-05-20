import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.*;

public class StravaShoeFilter {
    private static final String ACCESS_TOKEN = "0b9508ecc94fea9ce7ca1a6b2d70427824198d66";
    private static final String STRAVA_API_URL = "https://www.strava.com/api/v3/athlete/activities";
    private static final String GEAR_ID = "g16810693";
    private static final String JSON_FILE = "src/main/resources/activities.json";
    private static final String EXCEL_FILE = "C:/Users/mpatr/Desktop/connect_strava_tempus.xlsx";
    private static final String START_CELL = "I3";
    private static final int MAX_ACTIVITIES = 100;

    public static void main(String[] args) throws IOException, InterruptedException {
        JSONArray savedActivities = loadActivitiesFromFile();
        long lastTimestamp = getLastSavedTimestamp(savedActivities);
        JSONArray newActivities = getNewStravaActivities(lastTimestamp);

        System.out.println("Fetched new activities... " + newActivities.length());

        JSONArray filteredActivities = new JSONArray();
        Set<String> existingIds = getExistingActivityIds(savedActivities);

        for (int i = 0; i < newActivities.length(); i++) {
            JSONObject activity = newActivities.getJSONObject(i);
            String activityId = activity.get("id").toString();
            if (!existingIds.contains(activityId) && activity.has("gear_id") && !activity.isNull("gear_id")) {
                if (activity.getString("gear_id").equalsIgnoreCase(GEAR_ID)) {
                    JSONObject filteredActivity = new JSONObject();
                    filteredActivity.put("id", activityId);
                    filteredActivity.put("name", activity.getString("name"));
                    filteredActivity.put("start_date", activity.getString("start_date"));
                    filteredActivity.put("distance", activity.getDouble("distance"));
                    filteredActivities.put(filteredActivity);
                }
            }
        }

        saveActivitiesToFile(filteredActivities);
        exportToExcel(loadActivitiesFromFile(), EXCEL_FILE, START_CELL);

        System.out.println("\n✅ Data updated.");
    }

    private static JSONArray loadActivitiesFromFile() {
        File file = new File(JSON_FILE);
        if (!file.exists()) return new JSONArray();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder jsonText = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) jsonText.append(line);
            return new JSONArray(jsonText.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return new JSONArray();
        }
    }

    private static long getLastSavedTimestamp(JSONArray activities) {
        long max = 0;
        for (int i = 0; i < activities.length(); i++) {
            long ts = ISO8601ToUnix(activities.getJSONObject(i).getString("start_date"));
            max = Math.max(max, ts);
        }
        System.out.println("⏱️ Last saved timestamp: " + max);
        return max;
    }

    private static Set<String> getExistingActivityIds(JSONArray activities) {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < activities.length(); i++) {
            ids.add(activities.getJSONObject(i).get("id").toString());
        }
        return ids;
    }

    private static JSONArray getNewStravaActivities(long afterTimestamp) throws IOException, InterruptedException {
        int page = 1;
        JSONArray allActivities = new JSONArray();
        while (true) {
            String urlString = STRAVA_API_URL + "?page=" + page + "&per_page=" + MAX_ACTIVITIES + (afterTimestamp > 0 ? "&after=" + afterTimestamp : "");
            HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + ACCESS_TOKEN);
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() == 429) {
                Thread.sleep(conn.getHeaderFieldInt("Retry-After", 900) * 1000L);
                continue;
            }
            if (conn.getResponseCode() >= 400) break;

            Scanner scanner = new Scanner(conn.getInputStream());
            StringBuilder response = new StringBuilder();
            while (scanner.hasNext()) response.append(scanner.nextLine());
            scanner.close();
            conn.disconnect();

            JSONArray activities = new JSONArray(response.toString());
            if (activities.length() == 0) break;
            for (int i = 0; i < activities.length(); i++) allActivities.put(activities.getJSONObject(i));
            page++;
        }
        return allActivities;
    }

    private static void saveActivitiesToFile(JSONArray newActivities) {
        JSONArray existing = loadActivitiesFromFile();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < existing.length(); i++) ids.add(existing.getJSONObject(i).get("id").toString());

        List<JSONObject> combined = new ArrayList<>();
        for (int i = 0; i < newActivities.length(); i++) {
            JSONObject act = newActivities.getJSONObject(i);
            if (!ids.contains(act.get("id").toString())) combined.add(act);
        }
        for (int i = 0; i < existing.length(); i++) combined.add(existing.getJSONObject(i));

        combined.sort((a, b) -> Long.compare(ISO8601ToUnix(b.getString("start_date")), ISO8601ToUnix(a.getString("start_date"))));

        JSONArray merged = new JSONArray();
        combined.forEach(merged::put);

        try (FileWriter file = new FileWriter(JSON_FILE)) {
            file.write(merged.toString(2));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void exportToExcel(JSONArray activities, String filePath, String startCell) throws IOException {
        int col = startCell.charAt(0) - 'A';
        int row = Integer.parseInt(startCell.substring(1)) - 1;

        FileInputStream fis = new FileInputStream(filePath);
        Workbook wb = new XSSFWorkbook(fis);
        fis.close();
        Sheet sheet = wb.getSheetAt(0);

        CellStyle dateStyle = wb.createCellStyle();
        CreationHelper createHelper = wb.getCreationHelper();
        dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy.MM.dd"));

        for (int i = 0; i < activities.length(); i++) {
            JSONObject activity = activities.getJSONObject(i);
            String name = activity.getString("name");
            LocalDate date = Instant.parse(activity.getString("start_date")).atZone(ZoneOffset.UTC).toLocalDate();
            double distance = Math.round(activity.getDouble("distance") / 10.0) / 100.0;

            Row r = sheet.getRow(row + i);
            if (r == null) r = sheet.createRow(row + i);

            String[] currentValues = new String[3];
            for (int c = 0; c < 3; c++) {
                Cell cell = r.getCell(col + c);
                currentValues[c] = (cell == null) ? "" : cell.toString();
            }

            boolean needsUpdate = !currentValues[0].equals(name)
                    || !currentValues[1].contains(date.toString())
                    || !currentValues[2].contains(String.format("%.2f", distance));

            if (needsUpdate) {
                r.createCell(col).setCellValue(name);
                Cell dateCell = r.createCell(col + 1);
                dateCell.setCellValue(date);
                dateCell.setCellStyle(dateStyle);
                r.createCell(col + 2).setCellValue(distance);
            }
        }

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            wb.write(fos);
        }
        wb.close();
    }

    private static long ISO8601ToUnix(String date) {
        return Instant.from(DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneOffset.UTC).parse(date)).getLong(ChronoField.INSTANT_SECONDS);
    }
}
