import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.*;
import org.json.*;


public class StravaShoeFilter {
    private static final String ACCESS_TOKEN = "743872699e2d3caa090c34085e61cfff66de9e08"; //it needs to be used from postman
    private static final String STRAVA_API_URL = "https://www.strava.com/api/v3/athlete/activities";
    private static final String SHOE_NAME = "Saucony Tempus training shoes 6.0";
    private static final String GEAR_ID = "g16810693";
    private static final String JSON_FILE = "src/main/resources/activities.json";
    private static final int MAX_ACTIVITIES = 100;

    public static void main(String[] args) throws IOException, InterruptedException {
        while (true) {
            JSONArray savedActivities = loadActivitiesFromFile();
            long lastSavedTimestamp = getLastSavedTimestamp(savedActivities);
            JSONArray newActivities = getNewStravaActivities(lastSavedTimestamp);

            System.out.println("Fetched new activities... " + newActivities.length());

            JSONArray filteredActivities = new JSONArray();
            Set<String> existingIds = getExistingActivityIds(savedActivities);

            Set<String> allGearNames = new HashSet<>();

            for (int i = 0; i < newActivities.length(); i++) {
                JSONObject activity = newActivities.getJSONObject(i);
                String activityId = activity.get("id").toString();
                if (!existingIds.contains(activityId) && activity.has("gear_id") && !activity.isNull("gear_id")) {
                    String gearId = activity.getString("gear_id");
                    if (gearId.equalsIgnoreCase(GEAR_ID)) {
                        JSONObject filteredActivity = new JSONObject();
                        filteredActivity.put("id", activityId);
                        filteredActivity.put("name", activity.getString("name"));
                        filteredActivity.put("start_date", activity.getString("start_date"));
                        filteredActivity.put("distance", activity.getDouble("distance"));
                        filteredActivities.put(filteredActivity);
                    }
                }
            }
            System.out.println("Filtered activities to save: " + filteredActivities.length());

            saveActivitiesToFile(filteredActivities);
            System.out.println("\n✅ Data updated, waiting 15 minutes...");
            Thread.sleep(15 * 60 * 1000);
        }
    }

    private static JSONArray loadActivitiesFromFile() {
        File file = new File(JSON_FILE);
        if (!file.exists()) {
            return new JSONArray();
        }

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

    private static long getLastSavedTimestamp(JSONArray activities) {
        if (activities.length() == 0) {
            return 0;
        }
        List<Long> timestamps = new ArrayList<>();
        for (int i = 0; i < activities.length(); i++) {
            String date = activities.getJSONObject(i).getString("start_date");
            timestamps.add(ISO8601ToUnix(date));
        }
        return Collections.max(timestamps);
    }

    private static Set<String> getExistingActivityIds(JSONArray activities) {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < activities.length(); i++) {
            JSONObject activity = activities.getJSONObject(i);
            if (activity.has("id")) {
                ids.add(activity.get("id").toString());
            }
        }
        return ids;
    }

    private static JSONArray getNewStravaActivities(long afterTimestamp) throws IOException, InterruptedException {
        int page = 1;
        JSONArray allActivities = new JSONArray();
        while (true) {
            //URL url = new URL(STRAVA_API_URL + "?after=" + afterTimestamp +  "&page=" + page + "&per_page=" + MAX_ACTIVITIES);
            URL url = new URL(STRAVA_API_URL + "?page=" + page + "&per_page=" + MAX_ACTIVITIES);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + ACCESS_TOKEN);
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() == 429) {
                int retryAfter = conn.getHeaderFieldInt("Retry-After", 900);
                System.out.println("\nAPI limit exceeded. Waiting" + retryAfter + "...");
                Thread.sleep(retryAfter * 1000);
                continue;
            }

            Scanner scanner = new Scanner(conn.getInputStream());
            StringBuilder response = new StringBuilder();
            while (scanner.hasNext()) {
                response.append(scanner.nextLine());
            }
            scanner.close();

            JSONArray activities = new JSONArray(response.toString());
            if (activities.length() == 0) {
                break;
            }
            allActivities.putAll(activities);
            page++;
            if (page > 5){
                break;
            }
        }
        return allActivities;
    }


    private static void saveActivitiesToFile(JSONArray newActivities) {
        JSONArray existingActivities = loadActivitiesFromFile();
        Set<String> existingIds = new HashSet<>();

        for (int i = 0; i < existingActivities.length(); i++) {
            existingIds.add(existingActivities.getJSONObject(i).get("id").toString());
        }

        for (int i = 0; i < newActivities.length(); i++) {
            JSONObject activity = newActivities.getJSONObject(i);
            if (!existingIds.contains(activity.get("id").toString())) {
                existingActivities.put(activity);
            }
        }

        try (FileWriter file = new FileWriter(JSON_FILE)) {
            file.write(existingActivities.toString(2));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    private static String getGearName(String gearId) {
//        try {
//            URL url = new URL("https://www.strava.com/api/v3/gear/" + gearId);
//            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//            conn.setRequestMethod("GET");
//            conn.setRequestProperty("Authorization", "Bearer " + ACCESS_TOKEN);
//            conn.setRequestProperty("Accept", "application/json");
//
//            Scanner scanner = new Scanner(conn.getInputStream());
//            StringBuilder response = new StringBuilder();
//            while (scanner.hasNext()) {
//                response.append(scanner.nextLine());
//            }
//            scanner.close();
//
//            JSONObject gearData = new JSONObject(response.toString());
//            return gearData.getString("name");
//        } catch (Exception e) {
//            return "N/A";
//        }
//    }

    private static long ISO8601ToUnix(String date) {
        return Instant.from(DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneOffset.UTC).parse(date))
                .getLong(ChronoField.INSTANT_SECONDS);
    }
}