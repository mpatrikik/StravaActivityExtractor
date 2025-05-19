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
    private static final String ACCESS_TOKEN = "d59be6e4d3220a37995b04b11f3e97095f75f7c3"; //it needs to be used from postman
    private static final String STRAVA_API_URL = "https://www.strava.com/api/v3/athlete/activities";
    private static final String GEAR_ID = "g16810693"; //This is the gear ID for the Saucony Tempus training shoes 6.0
    private static final String JSON_FILE = "src/main/resources/activities.json";
    private static final int MAX_ACTIVITIES = 100;

    public static void main(String[] args) throws IOException, InterruptedException {
        JSONArray savedActivities = loadActivitiesFromFile();
        long lastSavedTimestamp = getLastSavedTimestamp(savedActivities);
        System.out.println("Using 'after' timestamp: " + lastSavedTimestamp + "( " + Instant.ofEpochSecond(lastSavedTimestamp) + " )");

        JSONArray newActivities = getNewStravaActivities(lastSavedTimestamp);

        System.out.println("Fetched new activities... " + newActivities.length());

        JSONArray filteredActivities = new JSONArray();
        Set<String> existingIds = getExistingActivityIds(savedActivities);

        for (int i = 0; i < newActivities.length(); i++) {
            JSONObject activity = newActivities.getJSONObject(i);
            String startDate = activity.getString("start_date");
            System.out.println("Received activity with start_date: " + startDate);
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
        System.out.println("\n✅ Data updated.");
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
            String urlString;
            if (afterTimestamp > 0) {
                urlString = STRAVA_API_URL + "?after=" + afterTimestamp + "&page=" + page + "&per_page=" + MAX_ACTIVITIES;
            } else {
                urlString = STRAVA_API_URL + "?page=" + page + "&per_page=" + MAX_ACTIVITIES;
            }
            URL url = new URL(urlString);
            System.out.println("Fetching activities from URL: " + url);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + ACCESS_TOKEN);
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() == 429) {
                int retryAfter = conn.getHeaderFieldInt("Retry-After", 900);
                System.out.println("\nAPI limit exceeded. Waiting" + retryAfter + "...");
                Thread.sleep(retryAfter * 1000L);
                continue;
            }

            if (conn.getResponseCode() >= 400) {
                System.err.println("HTTP Error: " + conn.getResponseCode() + " " + conn.getResponseMessage());
                break;
            }

            Scanner scanner;
            try {
                scanner = new Scanner(conn.getInputStream());
            } catch (IOException e) {
                System.err.println("Could not get InputStream for URL: " + url + " - Response code: " + conn.getResponseCode());
                break;
            }

            StringBuilder response = new StringBuilder();
            while (scanner.hasNext()) {
                response.append(scanner.nextLine());
            }
            scanner.close();
            conn.disconnect();

            JSONArray activities;
            try {
                activities = new JSONArray(response.toString());
            } catch (JSONException e) {
                System.err.println("Error parsing JSON response: " + response.toString());
                e.printStackTrace();
                break;
            }
            if (activities.length() == 0) {
                break;
            }

            for (int i = 0; i < activities.length(); i++) {
                allActivities.put(activities.getJSONObject(i));
            }
            page++;
        }
        return allActivities;
    }

    private static void saveActivitiesToFile(JSONArray newActivities) {
        JSONArray existingActivities = loadActivitiesFromFile();
        Set<String> existingIds = new HashSet<>();

        for (int i = 0; i < existingActivities.length(); i++) {
            existingIds.add(existingActivities.getJSONObject(i).get("id").toString());
        }

        for (int i = newActivities.length() - 1; i >= 0; i--) {
            JSONObject activity = newActivities.getJSONObject(i);
            if (!existingIds.contains(activity.get("id").toString())) {
                existingActivities.put(0, activity);
            }
        }

        try (FileWriter file = new FileWriter(JSON_FILE)) {
            file.write(existingActivities.toString(2));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static long ISO8601ToUnix(String date) {
        return Instant.from(DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneOffset.UTC).parse(date))
                .getLong(ChronoField.INSTANT_SECONDS);
    }
}