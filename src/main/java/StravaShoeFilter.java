import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import org.json.JSONArray;
import org.json.JSONObject;

public class StravaShoeFilter {
    private static final String ACCESS_TOKEN = "f944556962157c2371b38559e0efe74851b6023f";
    private static final String STRAVA_API_URL = "https://www.strava.com/api/v3/athlete/activities";
    private static final String SHOE_NAME = "Saucony Tempus training shoes 6.0";

    public static void main(String[] args) throws IOException {
        String jsonResponse = getStravaActivities();
        filterActivitiesByShoe(jsonResponse, SHOE_NAME);
    }

    private static String getStravaActivities() throws IOException {
        URL url = new URL(STRAVA_API_URL);
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

        return response.toString();
    }

    private static void filterActivitiesByShoe(String jsonResponse, String shoeName) {
        JSONArray activities = new JSONArray(jsonResponse);
        for (int i = 0; i < activities.length(); i++) {
            JSONObject activity = activities.getJSONObject(i);
            if (activity.has("gear_id") && !activity.isNull("gear_id")) {
                String gearId = activity.getString("gear_id");
                String gearName = getGearName(gearId);
                if (gearName.equalsIgnoreCase(shoeName)) {
                    System.out.println("Activity name: " + activity.getString("name") +
                            ",  Date: " + activity.getString("start_date"));
                }
            }
        }
    }

    private static String getGearName(String gearId) {
        try {
            URL url = new URL("https://www.strava.com/api/v3/gear/" + gearId + "?access_token=" + ACCESS_TOKEN);
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
