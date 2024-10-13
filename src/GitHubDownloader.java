import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.Scanner;
import org.json.JSONArray;
import org.json.JSONObject;

public class GitHubDownloader {
    private static final String GITHUB_API_URL = "https://api.github.com/repos/MCXboxBroadcast/Broadcaster/releases/latest";
    private static final String FILE_NAME = "MCXboxBroadcastStandalone.jar";
    private static final String JAR_PATH = "1.jar";
    private static final String TEMP_JAR_PATH = "2.jar";
    private static final long CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000; // 6 hours in milliseconds
    private static Process runningJarProcess = null;

    public static void main(String[] args) {
        // Start a thread to listen for user input
        Thread inputListenerThread = new Thread(GitHubDownloader::listenForInput);
        inputListenerThread.start();

        while (true) {
            try {
                checkForUpdates();
                // Wait for 6 hours before checking again
                Thread.sleep(CHECK_INTERVAL_MS);
            } catch (InterruptedException e) {
                System.out.println("Sleep interrupted: " + e.getMessage());
                break; // Exit loop if interrupted
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Clean up when the main loop exits
        cleanup();
    }

    private static void listenForInput() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String input = scanner.nextLine();
            if ("close".equalsIgnoreCase(input)) {
                System.out.println("Closing the application...");
                cleanup();
                System.exit(0); // Exit gracefully
            }
        }
    }

    private static void checkForUpdates() {
        try {
            // Check if 1.jar exists
            Path jarPath = Paths.get(JAR_PATH);
            if (Files.exists(jarPath)) {
                System.out.println(JAR_PATH + " exists, checking for updates...");

                // Download the latest file from GitHub as 2.jar
                String latestDownloadUrl = getLatestReleaseDownloadUrl();
                if (latestDownloadUrl != null) {
                    downloadFile(latestDownloadUrl, TEMP_JAR_PATH);

                    // Delete the old jar and rename the new jar only if download is successful
                    if (Files.exists(Paths.get(TEMP_JAR_PATH))) {
                        try {
                            // Delete the old JAR
                            Files.delete(jarPath);
                            System.out.println(JAR_PATH + " deleted successfully.");

                            // Rename 2.jar to 1.jar
                            Files.move(Paths.get(TEMP_JAR_PATH), jarPath, StandardCopyOption.REPLACE_EXISTING);
                            System.out.println(TEMP_JAR_PATH + " renamed to " + JAR_PATH);
                        } catch (IOException e) {
                            System.out.println("Error while managing JAR files: " + e.getMessage());
                        }
                    }

                    // Run the existing JAR
                    runningJarProcess = runJar(JAR_PATH);
                }
            } else {
                // If 1.jar doesn't exist, download the latest file directly as 1.jar
                System.out.println(JAR_PATH + " doesn't exist, downloading the latest file...");
                String latestDownloadUrl = getLatestReleaseDownloadUrl();
                if (latestDownloadUrl != null) {
                    downloadFile(latestDownloadUrl, JAR_PATH);
                    runningJarProcess = runJar(JAR_PATH);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Method to get the latest download URL from GitHub's API
    private static String getLatestReleaseDownloadUrl() throws IOException {
        URL url = new URL(GITHUB_API_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json");

        // Check if the request was successful
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            System.out.println("Failed to fetch release info. Server returned response code: " + responseCode);
            return null; // Exit the method if the response is not OK
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();
        connection.disconnect();

        // Parse the JSON response
        JSONObject json = new JSONObject(content.toString());
        JSONArray assets = json.getJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (FILE_NAME.equals(asset.getString("name"))) {
                return asset.getString("browser_download_url");
            }
        }
        return null;  // No matching asset found
    }

    // Method to download a file from a URL
    private static void downloadFile(String fileURL, String fileName) throws IOException {
        System.out.println("Downloading from: " + fileURL);
        URL url = new URL(fileURL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        // Check if the request was successful
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            System.out.println("Failed to download file. Server returned response code: " + responseCode);
            connection.disconnect();
            return; // Exit the method if the response is not OK
        }

        // Use try-with-resources to ensure the input stream is closed
        try (InputStream inputStream = connection.getInputStream()) {
            Files.copy(inputStream, Paths.get(fileName), StandardCopyOption.REPLACE_EXISTING);
            System.out.println(fileName + " downloaded successfully.");
        } catch (IOException e) {
            System.out.println("Error while downloading file: " + e.getMessage());
        } finally {
            connection.disconnect(); // Ensure the connection is closed
        }
    }

    // Method to execute a JAR file and capture its output
    private static Process runJar(String jarPath) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder("java", "-jar", jarPath);
        processBuilder.redirectErrorStream(true); // Redirect error stream to output
        Process process = processBuilder.start();

        // Print the output of the JAR to the console
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line); // Print each line of output
                }
            } catch (IOException e) {
                System.out.println("Error reading output: " + e.getMessage());
            }
        }).start();

        return process;
    }

    // Clean up any running processes and files
    private static void cleanup() {
        if (runningJarProcess != null) {
            runningJarProcess.destroy(); // Close the running jar process
            System.out.println("Terminated the running JAR process.");
        }
        // Optional: Delete temp JAR if exists
        try {
            Files.deleteIfExists(Paths.get(TEMP_JAR_PATH));
            System.out.println(TEMP_JAR_PATH + " deleted successfully.");
        } catch (IOException e) {
            System.out.println("Failed to delete " + TEMP_JAR_PATH + ": " + e.getMessage());
        }
    }
}
