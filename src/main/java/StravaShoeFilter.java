import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import org.json.*;

public class StravaShoeFilter {
    private static final String ACCESS_TOKEN = "f50cd2b299ef92aae4435f7bd3b6a345fb389853"; //it needs to be used from postman
    private static final String STRAVA_API_URL = "https://www.strava.com/api/v3/athlete/activities";
    private static final String SHOE_NAME = "Saucony Tempus training shoes 6.0";
    private static final String JSON_FILE = "src/main/resources/activities.json";
    private static final int MAX_ACTIVITIES = 70;

    public static void main(String[] args) throws IOException, InterruptedException {
        JSONArray savedActivities = loadActivitiesFromFile();
        String lastSavedDate = getLastSavedDate(savedActivities);
        JSONArray newActivities = getNewStravaActivities(lastSavedDate);

        JSONArray filteredActivities = new JSONArray();
        for (int i = 0; i < newActivities.length(); i++) {
            JSONObject activity = newActivities.getJSONObject(i);
            if (activity.has("gear_id") && !activity.isNull("gear_id")) {
                String gearId = activity.getString("gear_id");
                String gearName = getGearName(gearId);
                if (gearName.equalsIgnoreCase(SHOE_NAME)) {
                    JSONObject filteredActivity = new JSONObject();
                    filteredActivity.put("name", activity.getString("name"));
                    filteredActivity.put("start_date", activity.getString("start_date"));
                    filteredActivity.put("distance", activity.getDouble("distance"));
                    filteredActivities.put(filteredActivity);
                }
            }
        }
        saveActivitiesToFile(filteredActivities);
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

    private static void saveActivitiesToFile(JSONArray activities) {
        try (FileWriter file = new FileWriter(JSON_FILE)) {
            file.write(activities.toString(2));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getLastSavedDate(JSONArray activities) {
        if (activities.length() == 0) {
            return null;
        }

        List<String> dates = new ArrayList<>();
        for (int i = 0; i < activities.length(); i++) {
            dates.add(activities.getJSONObject(i).getString("start_date"));
        }
        dates.sort(Collections.reverseOrder());
        return dates.get(0);
    }

    private static JSONArray getNewStravaActivities(String lastSavedDate) throws IOException, InterruptedException {
        int page = 1;
        JSONArray allActivities = new JSONArray();
        while (true) {
            URL url = new URL(STRAVA_API_URL + "?page=" + page + "&per_page=" + MAX_ACTIVITIES);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + ACCESS_TOKEN);
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() == 429) {
                System.out.println("API limit exceeded. Waiting 15 minutes...");
                Thread.sleep(15 * 60 * 1000);
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
            for (int i = 0; i < activities.length(); i++) {
                String activityDate = activities.getJSONObject(i).getString("start_date");
                if (lastSavedDate != null && activityDate.compareTo(lastSavedDate) <= 0) {
                    return allActivities;
                }
                allActivities.put(activities.getJSONObject(i));
            }
            page++;
            if (page > 5) {
                break;
            }
        }
        return allActivities;
    }

    private static String getGearName(String gearId) {
        try {
            URL url = new URL("https://www.strava.com/api/v3/gear/" + gearId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + ACCESS_TOKEN);
            conn.setRequestProperty("Accept", "application/json");

            Scanner scanner = new Scanner(conn.getInputStream());
            StringBuilder response = new StringBuilder();
            while (scanner.hasNext()) {
                response.append(scanner.nextLine());
            }
            scanner.close();

            JSONObject gearData = new JSONObject(response.toString());
            return gearData.getString("name");
        } catch (Exception e) {
            return "N/A";
        }
    }
}