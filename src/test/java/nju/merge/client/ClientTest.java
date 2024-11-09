package nju.merge.client;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;

import nju.merge.utils.PathUtils;

public class ClientTest {
    private static final String workdir = "/Volumes/Q1571825323/conflict_data/";
    private static final String reposDir = workdir + "repos";   // store all the repos
    private static final String outputDir = workdir + "output";
    private static final Logger logger = LoggerFactory.getLogger(ClientTest.class);
    
    @Test
    public void collectTest() throws Exception {

        String list_file = workdir + "100+stars_4GB-_multidev_org_lang.csv";

        HashMap<String, String> repos = new HashMap<>();
        Client.addReposFromText(list_file, repos);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        // 创建固定大小的线程池
        int threadPoolSize = 4; // 你想要的线程数量
        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        AtomicInteger completedCnt = new AtomicInteger(0);

        repos.forEach((projectName, url) -> {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                String repoPath = PathUtils.getFileWithPathSegment(reposDir, projectName);
                String outputConflictPath = PathUtils.getFileWithPathSegment(outputDir, "conflictFiles");

                logger.info("Processing repo: {} on thread: {}", projectName, Thread.currentThread().getName());
                Client.collectMergeConflict(repoPath, projectName, url, outputConflictPath, Client.allowedExtensions);

                int completed = completedCnt.incrementAndGet();
                logger.info("Completed: {}/{}, {}%", completed, repos.size(), completed * 100.0 / repos.size());
            }, executorService);
            futures.add(future);
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }


    @Test
    public void nativeRepoTest() {
        // String repoList = workdir + "/list.txt";

        String projectPath = "collect_output/tmp_repo";
        String projectName = "tmp_repo";
        String url = "";
        String output = "collect_output/output";
        try {
            Client.collectMergeConflict(projectPath, projectName, url, output, Set.of("rb"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void mergeTest() {
        String projectPath = "collect_output/tmp_repo";

        try {
            // Open the existing repository located at 'projectPath'
            File repoDir = new File(projectPath);
            Git git = Git.open(repoDir);
            Repository repo = git.getRepository();

            // Define the two commits you want to merge
            String oursHash = "b8bf0e4"; // 'ours' commit
            String theirsHash = "3e851";  // 'theirs' commit

            // Checkout to 'ours' commit
            git.checkout().setName(oursHash).call();

            // Resolve the ObjectIds for the commits
            ObjectId theirsId = repo.resolve(theirsHash);

            // Prepare the merge command
            MergeCommand merge = git.merge();
            merge.include(theirsId);               // Include 'theirs' commit in the merge

            // Execute the merge
            MergeResult result = merge.call();

            // Handle the merge result
            if (result.getMergeStatus().isSuccessful()) {
                System.out.println("Merge was successful!");
            } else if (result.getMergeStatus().equals(MergeResult.MergeStatus.CONFLICTING)) {
                System.out.println("Merge resulted in conflicts.");
                // retrieve and handle conflicts here if needed
                Map<String, int[][]> conflicts = result.getConflicts();
                if (conflicts != null) {
                    for (String path : conflicts.keySet()) {
                        System.out.println("Conflicts in file: " + path);
                    }
                }
            } else {
                System.out.println("Merge failed with status: " + result.getMergeStatus());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
