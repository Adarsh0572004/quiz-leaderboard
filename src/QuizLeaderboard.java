import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class QuizLeaderboard {

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--demo".equals(args[0])) {
            runDemo();
            return;
        }
        if (args.length != 0) {
            System.err.println("Usage: java -cp out QuizLeaderboard [--demo]");
            System.exit(2);
        }

        String regNo = "RA2311003010551";
        String baseUrl = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";

        HttpClient client = HttpClient.newHttpClient();
        Scores scores = new Scores();

        System.out.println("Starting quiz polling for regNo: " + regNo);
        System.out.println("--------------------------------------------");

        for (int i = 0; i < 10; i++) {

            String pollUrl = baseUrl + "/quiz/messages?regNo=" + regNo + "&poll=" + i;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(pollUrl))
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            String body = resp.body();

            System.out.println("Poll " + i + " => HTTP " + resp.statusCode());

            scores.accept(body);

            if (i < 9) {
                Thread.sleep(5000);
            }
        }

        System.out.println("--------------------------------------------");
        System.out.println("Polling done. Building leaderboard...\n");

        List<Map.Entry<String, Integer>> sortedEntries = scores.ranked();

        int totalScore = 0;
        System.out.println("LEADERBOARD:");
        System.out.println(String.format("%-5s %-20s %s", "#", "Participant", "TotalScore"));
        System.out.println("-----------------------------------");

        StringBuilder leaderboardJson = new StringBuilder("[");

        for (int rank = 0; rank < sortedEntries.size(); rank++) {
            String name = sortedEntries.get(rank).getKey();
            int pts = sortedEntries.get(rank).getValue();
            totalScore += pts;

            System.out.println(String.format("%-5d %-20s %d", rank + 1, name, pts));

            if (rank > 0) leaderboardJson.append(",");
            leaderboardJson.append("{\"participant\":\"" + name + "\",\"totalScore\":" + pts + "}");
        }

        leaderboardJson.append("]");

        System.out.println("-----------------------------------");
        System.out.println("Total combined score: " + totalScore);
        System.out.println();

        String submitBody = "{\"regNo\":\"" + regNo + "\",\"leaderboard\":" + leaderboardJson.toString() + "}";

        System.out.println("Submitting to API...");
        System.out.println("Payload: " + submitBody);

        HttpRequest submitReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/quiz/submit"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(submitBody))
                .build();

        HttpResponse<String> submitResp = client.send(submitReq, HttpResponse.BodyHandlers.ofString());

        System.out.println("\nSubmission response (" + submitResp.statusCode() + "):");
        System.out.println(submitResp.body());

        if (submitResp.statusCode() == 200 || submitResp.statusCode() == 201) {
            System.out.println("\nLeaderboard submitted successfully!");
        } else {
            System.out.println("\nSomething went wrong. Check the response above.");
        }
    }

    private static void runDemo() {
        Scores scores = new Scores();
        scores.accept("{\"events\":["
                + "{\"roundId\":\"r1\",\"participant\":\"Ada\",\"score\":10},"
                + "{\"roundId\":\"r1\",\"participant\":\"Lin\",\"score\":12}]}");
        scores.accept("{\"events\":["
                + "{\"roundId\":\"r1\",\"participant\":\"Ada\",\"score\":10},"
                + "{\"roundId\":\"r2\",\"participant\":\"Ada\",\"score\":7},"
                + "{\"roundId\":\"r2\",\"participant\":\"Lin\",\"score\":3}]}");

        System.out.println("Offline demo (two sample polls, one repeated event)");
        System.out.printf("%-5s %-20s %s%n", "Rank", "Participant", "Total");
        int rank = 1;
        for (Map.Entry<String, Integer> entry : scores.ranked()) {
            System.out.printf("%-5d %-20s %d%n", rank++, entry.getKey(), entry.getValue());
        }
    }

    private static final class Scores {
        private final Map<String, Set<String>> seenParticipantsByRound = new HashMap<>();
        private final Map<String, Integer> totals = new HashMap<>();

        void accept(String body) {
            for (String[] event : extractEvents(body)) {
                String roundId = event[0];
                String participant = event[1];
                int score = Integer.parseInt(event[2]);
                if (seenParticipantsByRound.computeIfAbsent(roundId, key -> new HashSet<>())
                        .add(participant)) {
                    totals.merge(participant, score, Integer::sum);
                }
            }
        }

        List<Map.Entry<String, Integer>> ranked() {
            List<Map.Entry<String, Integer>> ranking = new ArrayList<>(totals.entrySet());
            ranking.sort(Map.Entry.<String, Integer>comparingByValue().reversed()
                    .thenComparing(Map.Entry.comparingByKey()));
            return ranking;
        }
    }

    private static List<String[]> extractEvents(String json) {
        List<String[]> results = new ArrayList<>();

        int evIdx = json.indexOf("\"events\"");
        if (evIdx == -1) return results;

        int arrStart = json.indexOf('[', evIdx);
        if (arrStart == -1) return results;
        int arrEnd = findClosingBracket(json, arrStart);
        if (arrEnd == -1) return results;

        String arrContent = json.substring(arrStart + 1, arrEnd);

        int cursor = 0;
        while (cursor < arrContent.length()) {
            int objStart = arrContent.indexOf('{', cursor);
            if (objStart == -1) break;
            int objEnd = arrContent.indexOf('}', objStart);
            if (objEnd == -1) break;

            String objText = arrContent.substring(objStart + 1, objEnd);

            String roundId = grabJsonString(objText, "roundId");
            String participant = grabJsonString(objText, "participant");
            String scoreStr = grabJsonValue(objText, "score");

            if (roundId != null && participant != null && scoreStr != null) {
                results.add(new String[]{roundId, participant, scoreStr.trim()});
            }

            cursor = objEnd + 1;
        }

        return results;
    }

    private static int findClosingBracket(String s, int openPos) {
        int depth = 0;
        for (int i = openPos; i < s.length(); i++) {
            if (s.charAt(i) == '[') depth++;
            else if (s.charAt(i) == ']') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static String grabJsonString(String objText, String key) {
        String search = "\"" + key + "\":\"";
        int start = objText.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = objText.indexOf("\"", start);
        if (end == -1) return null;
        return objText.substring(start, end);
    }

    private static String grabJsonValue(String objText, String key) {
        String search = "\"" + key + "\":";
        int start = objText.indexOf(search);
        if (start == -1) return null;
        start += search.length();

        int end = objText.length();
        int commaPos = objText.indexOf(",", start);
        int bracePos = objText.indexOf("}", start);

        if (commaPos != -1 && commaPos < end) end = commaPos;
        if (bracePos != -1 && bracePos < end) end = bracePos;

        return objText.substring(start, end).replace("\"", "").trim();
    }
}
