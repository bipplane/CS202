import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Scanner;

public class RCPSP {

    static class Activity {
        int id;
        int duration;
        int[] resourceRequirements;
        List<Integer> successors;

        public Activity(int id, int duration, int[] resourceRequirements, List<Integer> successors) {
            this.id = id;
            this.duration = duration;
            this.resourceRequirements = resourceRequirements;
            this.successors = successors;
        }
    }

    static class ProjectInstance {
        int numActivities;
        int numResourceTypes;
        int[] resourceCapacities;
        Activity[] activities;

        public ProjectInstance(int numActivities, int numResourceTypes) {
            this.numActivities = numActivities;
            this.numResourceTypes = numResourceTypes;
            this.resourceCapacities = new int[numResourceTypes];
            this.activities = new Activity[numActivities + 2];
        }
    }

    public static ProjectInstance parsePSPLIB(String filePath) throws Exception {
        Scanner scanner = new Scanner(new File(filePath));

        String line = scanner.nextLine().trim();
        while (line.isEmpty()) line = scanner.nextLine().trim();
        String[] firstLineParts = line.split("\\s+");
        int numActivities = Integer.parseInt(firstLineParts[0]);
        int numResourceTypes = Integer.parseInt(firstLineParts[1]);

        ProjectInstance instance = new ProjectInstance(numActivities, numResourceTypes);
        int totalJobs = numActivities + 2;

        for (int i = 0; i < totalJobs; i++) {
            instance.activities[i] = new Activity(i, 0, new int[numResourceTypes], new ArrayList<>());
        }

        for (int i = 0; i < totalJobs; i++) {
            line = scanner.nextLine().trim();
            while (line.isEmpty()) line = scanner.nextLine().trim();
            String[] parts = line.split("\\s+");

            int jobId = Integer.parseInt(parts[0]);
            int numSuccessors = Integer.parseInt(parts[2]);

            for (int j = 0; j < numSuccessors; j++) {
                int succ = Integer.parseInt(parts[3 + j]);
                if (succ > jobId) {
                    instance.activities[jobId].successors.add(succ);
                }
            }
        }

        for (int i = 0; i < totalJobs; i++) {
            line = scanner.nextLine().trim();
            while (line.isEmpty()) line = scanner.nextLine().trim();
            String[] parts = line.split("\\s+");

            int jobId = Integer.parseInt(parts[0]);
            instance.activities[jobId].duration = Integer.parseInt(parts[2]);

            for (int k = 0; k < numResourceTypes; k++) {
                instance.activities[jobId].resourceRequirements[k] = Integer.parseInt(parts[3 + k]);
            }
        }

        line = scanner.nextLine().trim();
        while (line.isEmpty() && scanner.hasNextLine()) line = scanner.nextLine().trim();
        String[] capParts = line.split("\\s+");
        for (int k = 0; k < numResourceTypes; k++) {
            instance.resourceCapacities[k] = Integer.parseInt(capParts[k]);
        }

        scanner.close();
        return instance;
    }

    public static int[] scheduleProject(ProjectInstance instance) {
        int totalJobs = instance.numActivities + 2;
        int[] startTimes = new int[totalJobs];
        int[] completionTimes = new int[totalJobs];

        List<List<Integer>> predecessors = new ArrayList<>();
        int[] inDegree = new int[totalJobs];

        for (int i = 0; i < totalJobs; i++) {
            predecessors.add(new ArrayList<>());
        }

        for (int i = 0; i < totalJobs; i++) {
            for (int succ : instance.activities[i].successors) {
                predecessors.get(succ).add(i);
                inDegree[succ]++;
            }
        }

        PriorityQueue<Integer> queue = new PriorityQueue<>();
        for (int i = 0; i < totalJobs; i++) {
            if (inDegree[i] == 0) {
                queue.add(i);
            }
        }

        List<Integer> topologicalOrder = new ArrayList<>();
        while (!queue.isEmpty()) {
            int current = queue.poll();
            topologicalOrder.add(current);
            for (int succ : instance.activities[current].successors) {
                inDegree[succ]--;
                if (inDegree[succ] == 0) {
                    queue.add(succ);
                }
            }
        }

        int maxTimeHorizon = 0;
        for (int i = 0; i < totalJobs; i++) {
            maxTimeHorizon += instance.activities[i].duration;
        }

        int[][] resourceProfile = new int[maxTimeHorizon][instance.numResourceTypes];

        for (int currentIdx : topologicalOrder) {
            Activity currentActivity = instance.activities[currentIdx];

            int earliestPossibleStart = 0;
            for (int pred : predecessors.get(currentIdx)) {
                earliestPossibleStart = Math.max(earliestPossibleStart, completionTimes[pred]);
            }

            int actualStart = earliestPossibleStart;
            while (true) {
                boolean isFeasible = true;
                for (int t = actualStart; t < actualStart + currentActivity.duration; t++) {
                    for (int k = 0; k < instance.numResourceTypes; k++) {
                        if (resourceProfile[t][k] + currentActivity.resourceRequirements[k] > instance.resourceCapacities[k]) {
                            isFeasible = false;
                            break;
                        }
                    }
                    if (!isFeasible) break;
                }

                if (isFeasible) {
                    break;
                } else {
                    actualStart++;
                }
            }

            startTimes[currentIdx] = actualStart;
            completionTimes[currentIdx] = actualStart + currentActivity.duration;

            for (int t = actualStart; t < actualStart + currentActivity.duration; t++) {
                for (int k = 0; k < instance.numResourceTypes; k++) {
                    resourceProfile[t][k] += currentActivity.resourceRequirements[k];
                }
            }
        }

        return startTimes;
    }

    public static void main(String[] args) {
        String folderPath = "sm_j10";
        File folder = new File(folderPath);

        if (!folder.exists() || !folder.isDirectory()) {
            System.out.println("Directory '" + folderPath + "' not found.");
            return;
        }

        File[] listOfFiles = folder.listFiles((dir, name) -> name.toUpperCase().endsWith(".SCH"));

        if (listOfFiles == null || listOfFiles.length == 0) {
            System.out.println("No .SCH files found in the directory.");
            return;
        }

        int successCount = 0;
        long totalExecutionTime = 0;

        for (File file : listOfFiles) {
            try {
                long startTime = System.currentTimeMillis();

                ProjectInstance instance = parsePSPLIB(file.getAbsolutePath());
                int[] schedule = scheduleProject(instance);
                int makespan = schedule[instance.numActivities + 1];

                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;
                totalExecutionTime += duration;

                System.out.println(String.format("File: %-12s | Makespan: %-5d | Time: %d ms", file.getName(), makespan, duration));
                successCount++;

            } catch (Exception e) {
                System.out.println("Failed to process file: " + file.getName());
            }
        }

        System.out.println("Successfully processed " + successCount + " files.");
        System.out.println("Total execution time: " + totalExecutionTime + " ms");
    }
}